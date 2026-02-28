package checkpoint.gui;

import checkpoint.i18n.Messages;
import checkpoint.manager.CheckpointManager;
import checkpoint.model.Checkpoint;
import checkpoint.model.PlayerSortOrder;
import checkpoint.model.RenameResult;
import checkpoint.model.SortOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import static checkpoint.gui.GuiConstants.*;

/**
 * Manages all GUI state, opens menus, handles menu interactions,
 * and executes checkpoint operations triggered from the GUI.
 */
public class MenuManager {

    // ---- Internal types ---------------------------------------------------
    enum SelectionType { NAMED, QUICK }
    record LastSelection(SelectionType type, String identifier) {}

    // ---- State maps -------------------------------------------------------
    private final Map<UUID, Integer>        menuPages            = new ConcurrentHashMap<>();
    private final Map<UUID, LastSelection>  lastSelections       = new ConcurrentHashMap<>();
    private final Map<UUID, SortOrder>      playerSortOrders     = new ConcurrentHashMap<>();
    private final Map<UUID, String>         playerSearchQuery    = new ConcurrentHashMap<>();
    private final Set<UUID>                 awaitingSearchInput  = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID>           viewingPlayerId      = new ConcurrentHashMap<>();
    private final Map<UUID, String>         pendingOperationCp   = new ConcurrentHashMap<>();
    private final Map<UUID, String>         awaitingRenameInput  = new ConcurrentHashMap<>();
    private final Map<UUID, String>         awaitingDescriptionInput = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>        playerSelectPages    = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSortOrder> playerSelectSortOrders = new ConcurrentHashMap<>();
    private final Map<UUID, String>         playerSelectSearchQuery = new ConcurrentHashMap<>();
    private final Set<UUID>                 awaitingPlayerSearchInput = ConcurrentHashMap.newKeySet();

    // ---- Dependencies -----------------------------------------------------
    private final JavaPlugin          plugin;
    private final CheckpointManager   checkpointManager;
    private final NamespacedKey       targetUuidKey;

    public MenuManager(JavaPlugin plugin, CheckpointManager checkpointManager) {
        this.plugin = plugin;
        this.checkpointManager = checkpointManager;
        this.targetUuidKey = new NamespacedKey(plugin, "target_uuid");
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void clearAll() {
        menuPages.clear();
        lastSelections.clear();
        playerSortOrders.clear();
        playerSearchQuery.clear();
        awaitingSearchInput.clear();
        viewingPlayerId.clear();
        pendingOperationCp.clear();
        awaitingRenameInput.clear();
        awaitingDescriptionInput.clear();
        playerSelectPages.clear();
        playerSelectSortOrders.clear();
        playerSelectSearchQuery.clear();
        awaitingPlayerSearchInput.clear();
    }

    public boolean isOurMenu(String title) {
        return GuiConstants.isOurMenu(title);
    }

    // -----------------------------------------------------------------------
    // State accessors
    // -----------------------------------------------------------------------

    public int getMenuPage(UUID playerId) {
        return menuPages.getOrDefault(playerId, 0);
    }

    // -----------------------------------------------------------------------
    // Awaiting input checks (atomic remove-and-return for thread-safety)
    // -----------------------------------------------------------------------

    public boolean isAwaitingDescriptionInput(UUID playerId) {
        return awaitingDescriptionInput.containsKey(playerId);
    }

    public boolean isAwaitingRenameInput(UUID playerId) {
        return awaitingRenameInput.containsKey(playerId);
    }

    public boolean isAwaitingSearchInput(UUID playerId) {
        return awaitingSearchInput.contains(playerId);
    }

    public boolean isAwaitingPlayerSearchInput(UUID playerId) {
        return awaitingPlayerSearchInput.contains(playerId);
    }

    // -----------------------------------------------------------------------
    // Notifications (called from command layer)
    // -----------------------------------------------------------------------

    public void notifyNamedCheckpointSet(Player player, String rawName) {
        UUID playerId = player.getUniqueId();
        if (checkpointManager.selectNamedCheckpoint(playerId, rawName)) {
            checkpointManager.getSelectedNamedCheckpointName(playerId)
                .ifPresent(actualName -> markLastSelection(playerId, SelectionType.NAMED, actualName));
        }
    }

    public void notifyNamedCheckpointDeleted(UUID playerId, String rawName) {
        lastSelections.computeIfPresent(playerId, (id, selection) ->
            selection.type == SelectionType.NAMED && selection.identifier != null
                && selection.identifier.equalsIgnoreCase(rawName)
                ? null
                : selection);
    }

    // -----------------------------------------------------------------------
    // Quick checkpoint save (slime ball)
    // -----------------------------------------------------------------------

    public void handleQuickCheckpointSave(Player player) {
        UUID playerId = player.getUniqueId();
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + Messages.cmdWorldError(playerId));
            return;
        }

        Checkpoint checkpoint = new Checkpoint(
            world.getName(), location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
        checkpointManager.setQuickCheckpoint(playerId, checkpoint);
        markLastSelection(playerId, SelectionType.QUICK, null);
        player.sendMessage(ChatColor.GREEN + Messages.quickSaved(playerId));
        player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
    }

    // -----------------------------------------------------------------------
    // Nether star teleport
    // -----------------------------------------------------------------------

    public void handleCheckpointTeleport(Player player) {
        UUID playerId = player.getUniqueId();
        Optional<Checkpoint> target = resolveTeleportTarget(playerId);

        if (target.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + Messages.noCheckpoint(playerId));
            return;
        }

        Checkpoint checkpoint = target.get();
        World world = Bukkit.getWorld(checkpoint.worldName());
        if (world == null) {
            player.sendMessage(ChatColor.RED + Messages.worldNotFound(playerId));
            return;
        }

        Location destination = new Location(
            world, checkpoint.x(), checkpoint.y(), checkpoint.z(),
            checkpoint.yaw(), checkpoint.pitch());
        preparePlayerForTeleport(player);
        ensureChunkLoaded(destination);
        attemptTeleport(player, destination, TELEPORT_MAX_ATTEMPTS);
    }

    private Optional<Checkpoint> resolveTeleportTarget(UUID playerId) {
        LastSelection lastSelection = lastSelections.get(playerId);
        if (lastSelection != null) {
            switch (lastSelection.type) {
                case NAMED -> {
                    Optional<Checkpoint> named = checkpointManager.getNamedCheckpoint(playerId, lastSelection.identifier);
                    if (named.isPresent()) return named;
                    lastSelections.remove(playerId);
                }
                case QUICK -> {
                    Optional<Checkpoint> quick = checkpointManager.getQuickCheckpoint(playerId);
                    if (quick.isPresent()) return quick;
                    lastSelections.remove(playerId);
                }
            }
        }
        Optional<Checkpoint> selectedNamed = checkpointManager.getSelectedNamedCheckpoint(playerId);
        if (selectedNamed.isPresent()) return selectedNamed;
        return checkpointManager.getQuickCheckpoint(playerId);
    }

    // -----------------------------------------------------------------------
    // Open menus
    // -----------------------------------------------------------------------

    public void openCheckpointMenu(Player viewer, int requestedPage) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = viewingPlayerId.getOrDefault(viewerId, viewerId);
        openCheckpointMenuFor(viewer, requestedPage, targetId);
    }

    public void openCheckpointMenuFor(Player viewer, int requestedPage, UUID targetId) {
        UUID viewerId = viewer.getUniqueId();
        viewingPlayerId.put(viewerId, targetId);
        boolean isSelf = targetId.equals(viewerId);

        SortOrder order = playerSortOrders.getOrDefault(viewerId, SortOrder.NAME_ASC);
        String query = playerSearchQuery.get(viewerId);
        double px = viewer.getLocation().getX();
        double pz = viewer.getLocation().getZ();

        List<String> names = checkpointManager.getSortedFilteredCheckpointNames(
            targetId, order, query, px, pz);

        int totalPages = Math.max(1, (int) Math.ceil(Math.max(1, names.size()) / (double) ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        menuPages.put(viewerId, page);

        Inventory inventory = Bukkit.createInventory(viewer, GUI_SIZE,
            ChatColor.DARK_AQUA + Messages.guiTitle(viewerId));

        // Border decoration
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                Material mat = (row + col) % 2 == 0
                    ? Material.CYAN_STAINED_GLASS_PANE
                    : Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                inventory.setItem(slot, ItemFactory.createGlassDeco(mat));
            }
        }

        // Player head at top-row middle
        inventory.setItem(SLOT_PLAYER_HEAD, ItemFactory.createPlayerHeadItem(viewerId, Bukkit.getOfflinePlayer(targetId), isSelf));

        // CP items in inner area (rows 1-4, cols 1-7)
        Optional<String> selectedName = isSelf
            ? checkpointManager.getSelectedNamedCheckpointName(viewerId)
            : Optional.empty();
        int startIndex = page * ITEMS_PER_PAGE;
        int itemIndex = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                int slot = row * 9 + col;
                int dataIndex = startIndex + itemIndex;
                if (dataIndex < names.size()) {
                    String name = names.get(dataIndex);
                    Optional<Checkpoint> checkpoint = checkpointManager.getNamedCheckpoint(targetId, name);
                    if (checkpoint.isPresent()) {
                        boolean selected = selectedName.map(s -> s.equalsIgnoreCase(name)).orElse(false);
                        inventory.setItem(slot, ItemFactory.createCheckpointPaper(viewerId, name, checkpoint.get(), selected, isSelf));
                    }
                }
                itemIndex++;
            }
        }

        if (names.isEmpty()) {
            inventory.setItem(22, ItemFactory.createEmptyNoticeItem(viewerId));
        }

        // Bottom row controls
        if (totalPages > 1 && page > 0) {
            inventory.setItem(SLOT_PREVIOUS, ItemFactory.createNavItem(viewerId, false, page, totalPages));
        } else {
            inventory.setItem(SLOT_PREVIOUS, ItemFactory.createDisabledNavItem(viewerId, false));
        }
        inventory.setItem(SLOT_SEARCH, ItemFactory.createAnvilSearchItem(viewerId));
        inventory.setItem(SLOT_INFO, ItemFactory.createInfoItem(viewerId, page + 1, totalPages, names.size(), order, query));
        inventory.setItem(SLOT_SORT, ItemFactory.createSortButtonItem(viewerId, order));
        if (totalPages > 1 && page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, ItemFactory.createNavItem(viewerId, true, page, totalPages));
        } else {
            inventory.setItem(SLOT_NEXT, ItemFactory.createDisabledNavItem(viewerId, true));
        }

        viewer.openInventory(inventory);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.2f);
    }

    public void openSortMenu(Player player) {
        UUID viewerId = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(player, 27,
            ChatColor.DARK_AQUA + Messages.sortTitle(viewerId));
        SortOrder current = playerSortOrders.getOrDefault(viewerId, SortOrder.NAME_ASC);
        SortOrder[] orders = SortOrder.values();

        for (int s = 0; s < 9; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        for (int s = 18; s < 27; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        for (int s = 9; s < 18; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.LIGHT_BLUE_STAINED_GLASS_PANE));

        Material[] dyeColors = {
            Material.LIME_DYE, Material.GREEN_DYE, Material.CYAN_DYE,
            Material.BLUE_DYE, Material.LIGHT_BLUE_DYE, Material.PURPLE_DYE,
            Material.YELLOW_DYE,
        };
        int dyeBase = 10;
        for (int i = 0; i < orders.length; i++) {
            inv.setItem(dyeBase + i, ItemFactory.createSortDyeItem(
                viewerId, dyeColors[i % dyeColors.length], orders[i], orders[i] == current));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    public void openPlayerSelectMenu(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        PlayerSortOrder psOrder = playerSelectSortOrders.getOrDefault(viewerId, PlayerSortOrder.NAME_ASC);
        String psQuery = playerSelectSearchQuery.get(viewerId);
        double px = viewer.getLocation().getX();
        double pz = viewer.getLocation().getZ();

        List<UUID> playerList = getSortedFilteredPlayers(viewerId, psOrder, psQuery, px, pz);

        int psPage = playerSelectPages.getOrDefault(viewerId, 0);
        int totalItems = playerList.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE));
        psPage = Math.max(0, Math.min(psPage, totalPages - 1));
        playerSelectPages.put(viewerId, psPage);

        Inventory inv = Bukkit.createInventory(viewer, GUI_SIZE,
            ChatColor.DARK_AQUA + Messages.playerSelectTitle(viewerId));

        // Glass border (same pattern as CP list)
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                Material mat = (row + col) % 2 == 0
                    ? Material.CYAN_STAINED_GLASS_PANE
                    : Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                inv.setItem(slot, ItemFactory.createGlassDeco(mat));
            }
        }

        UUID currentTarget = viewingPlayerId.getOrDefault(viewerId, viewerId);
        int startIndex = psPage * ITEMS_PER_PAGE;
        int itemIndex = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                int slot = row * 9 + col;
                int dataIndex = startIndex + itemIndex;
                if (dataIndex < playerList.size()) {
                    UUID targetId = playerList.get(dataIndex);
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
                    boolean isSelf = targetId.equals(viewerId);
                    boolean isViewing = targetId.equals(currentTarget);
                    int cpCount = checkpointManager.getNamedCheckpointNames(targetId).size();
                    String lastCloneStr = checkpointManager.getCloneTime(viewerId, targetId)
                        .map(ItemFactory::formatInstant).orElse(null);
                    int totalClonedCount = checkpointManager.getClonedCount(targetId);
                    double nearestDist = Math.sqrt(checkpointManager.getNearestCpDistanceSq(targetId, px, pz));
                    String lastActivityStr = checkpointManager.getLastActivityTime(targetId)
                        .map(ItemFactory::formatInstant).orElse(null);

                    inv.setItem(slot, ItemFactory.createPlayerSelectHead(
                        viewerId, target, isSelf, isViewing, cpCount, lastCloneStr,
                        totalClonedCount, nearestDist, lastActivityStr, targetUuidKey));
                }
                itemIndex++;
            }
        }

        // Bottom row controls
        if (totalPages > 1 && psPage > 0) {
            inv.setItem(SLOT_PREVIOUS, ItemFactory.createNavItem(viewerId, false, psPage, totalPages));
        } else {
            inv.setItem(SLOT_PREVIOUS, ItemFactory.createDisabledNavItem(viewerId, false));
        }
        inv.setItem(SLOT_SEARCH, ItemFactory.createPlayerSearchItem(viewerId));
        inv.setItem(SLOT_INFO, ItemFactory.createPlayerInfoItem(viewerId, psPage + 1, totalPages, totalItems, psOrder, psQuery));
        inv.setItem(SLOT_SORT, ItemFactory.createPlayerSortButtonItem(viewerId, psOrder));
        if (totalPages > 1 && psPage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, ItemFactory.createNavItem(viewerId, true, psPage, totalPages));
        } else {
            inv.setItem(SLOT_NEXT, ItemFactory.createDisabledNavItem(viewerId, true));
        }

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    public void openPlayerSortMenu(Player player) {
        UUID viewerId = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(player, 27,
            ChatColor.DARK_AQUA + Messages.playerSortTitle(viewerId));
        PlayerSortOrder current = playerSelectSortOrders.getOrDefault(viewerId, PlayerSortOrder.NAME_ASC);
        PlayerSortOrder[] orders = PlayerSortOrder.values();

        for (int s = 0; s < 9; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        for (int s = 18; s < 27; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        for (int s = 9; s < 18; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.LIGHT_BLUE_STAINED_GLASS_PANE));

        Material[] dyeColors = {
            Material.LIME_DYE, Material.GREEN_DYE, Material.CYAN_DYE,
            Material.BLUE_DYE, Material.LIGHT_BLUE_DYE, Material.PURPLE_DYE,
            Material.YELLOW_DYE,
        };
        int dyeBase = 10;
        for (int i = 0; i < orders.length; i++) {
            inv.setItem(dyeBase + i, ItemFactory.createSortDyeItem(
                viewerId, dyeColors[i % dyeColors.length],
                Messages.playerSortOrderLabel(viewerId, orders[i]), orders[i] == current));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    public void openCpOperationMenu(Player viewer, String cpName, UUID targetId) {
        UUID viewerId = viewer.getUniqueId();
        boolean isSelf = targetId.equals(viewerId);
        pendingOperationCp.put(viewerId, cpName);
        Inventory inv = Bukkit.createInventory(viewer, 27,
            ChatColor.DARK_AQUA + Messages.cpOperationTitle(viewerId));

        for (int s = 0; s < 9; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        for (int s = 18; s < 27; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        for (int s = 9; s < 18; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.LIGHT_BLUE_STAINED_GLASS_PANE));

        checkpointManager.getNamedCheckpoint(targetId, cpName)
            .ifPresent(cp -> inv.setItem(4, ItemFactory.createCheckpointPaperInfo(viewerId, cpName, cp)));

        if (isSelf) {
            inv.setItem(9,  ItemFactory.createOperationWoolItem(viewerId, Material.GREEN_WOOL,
                ChatColor.GREEN + Messages.opTeleport(viewerId), Messages.opTeleportLore(viewerId)));
            inv.setItem(11, ItemFactory.createOperationWoolItem(viewerId, Material.BLUE_WOOL,
                ChatColor.AQUA + Messages.opUpdate(viewerId), Messages.opUpdateLore(viewerId)));
            inv.setItem(13, ItemFactory.createOperationWoolItem(viewerId, Material.YELLOW_WOOL,
                ChatColor.YELLOW + Messages.opRename(viewerId), Messages.opRenameLore(viewerId)));
            inv.setItem(15, ItemFactory.createOperationWoolItem(viewerId, Material.ORANGE_WOOL,
                ChatColor.GOLD + Messages.opDescChange(viewerId), Messages.opDescChangeLore(viewerId)));
            inv.setItem(17, ItemFactory.createOperationWoolItem(viewerId, Material.RED_WOOL,
                ChatColor.RED + Messages.opDelete(viewerId), Messages.opDeleteLore(viewerId)));
        } else {
            OfflinePlayer tp = Bukkit.getOfflinePlayer(targetId);
            String tName = tp.getName() != null ? tp.getName() : Messages.psUnknown(viewerId);
            inv.setItem(12, ItemFactory.createOperationWoolItem(viewerId, Material.GREEN_WOOL,
                ChatColor.GREEN + Messages.opTeleport(viewerId), Messages.opTeleportLore(viewerId)));
            inv.setItem(14, ItemFactory.createOperationWoolItem(viewerId, Material.CYAN_WOOL,
                ChatColor.AQUA + Messages.opClone(viewerId), Messages.opCloneLore(viewerId, tName)));
        }

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    // -----------------------------------------------------------------------
    // Click handlers (called by InventoryClickListener)
    // -----------------------------------------------------------------------

    public void handleSortMenuClick(Player player, int rawSlot) {
        SortOrder[] orders = SortOrder.values();
        int dyeBase = 10;
        for (int i = 0; i < orders.length; i++) {
            if (rawSlot == dyeBase + i) {
                playerSortOrders.put(player.getUniqueId(), orders[i]);
                menuPages.put(player.getUniqueId(), 0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                openCheckpointMenu(player, 0);
                return;
            }
        }
    }

    public void handlePlayerSortMenuClick(Player player, int rawSlot) {
        PlayerSortOrder[] orders = PlayerSortOrder.values();
        int dyeBase = 10;
        for (int i = 0; i < orders.length; i++) {
            if (rawSlot == dyeBase + i) {
                playerSelectSortOrders.put(player.getUniqueId(), orders[i]);
                playerSelectPages.put(player.getUniqueId(), 0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                openPlayerSelectMenu(player);
                return;
            }
        }
    }

    public void handlePlayerSelectMenuClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        UUID viewerId = player.getUniqueId();
        int psPage = playerSelectPages.getOrDefault(viewerId, 0);

        PlayerSortOrder psOrder = playerSelectSortOrders.getOrDefault(viewerId, PlayerSortOrder.NAME_ASC);
        String psQuery = playerSelectSearchQuery.get(viewerId);
        double px = player.getLocation().getX();
        double pz = player.getLocation().getZ();
        List<UUID> playerList = getSortedFilteredPlayers(viewerId, psOrder, psQuery, px, pz);
        int totalPages = Math.max(1, (int) Math.ceil(playerList.size() / (double) ITEMS_PER_PAGE));

        if (rawSlot == SLOT_PREVIOUS && event.isLeftClick() && psPage > 0) {
            playerSelectPages.put(viewerId, psPage - 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.9f);
            openPlayerSelectMenu(player);
            return;
        }
        if (rawSlot == SLOT_NEXT && event.isLeftClick() && psPage < totalPages - 1) {
            playerSelectPages.put(viewerId, psPage + 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            openPlayerSelectMenu(player);
            return;
        }
        if (rawSlot == SLOT_SORT && event.isLeftClick()) {
            openPlayerSortMenu(player);
            return;
        }
        if (rawSlot == SLOT_SEARCH && event.isLeftClick()) {
            startPlayerSearchInput(player);
            return;
        }
        if (rawSlot == SLOT_SEARCH && event.isRightClick()) {
            playerSelectSearchQuery.remove(viewerId);
            playerSelectPages.put(viewerId, 0);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            openPlayerSelectMenu(player);
            return;
        }

        int psRow = rawSlot / 9;
        int psCol = rawSlot % 9;
        if (psRow >= 1 && psRow <= 4 && psCol >= 1 && psCol <= 7) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
            ItemMeta clickedMeta = clicked.getItemMeta();
            if (clickedMeta == null) return;
            String uuidStr = clickedMeta.getPersistentDataContainer()
                .get(targetUuidKey, PersistentDataType.STRING);
            if (uuidStr == null) return;
            UUID targetId = UUID.fromString(uuidStr);
            viewingPlayerId.put(viewerId, targetId);
            menuPages.put(viewerId, 0);
            playerSearchQuery.remove(viewerId);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            openCheckpointMenuFor(player, 0, targetId);
        }
    }

    public void handleCpOperationMenuClick(Player player, int rawSlot) {
        UUID viewerId = player.getUniqueId();
        String cpName = pendingOperationCp.get(viewerId);
        if (cpName == null) return;
        UUID targetId = viewingPlayerId.getOrDefault(viewerId, viewerId);
        boolean isSelf = targetId.equals(viewerId);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isSelf) {
                if (rawSlot == 9)       executeTeleportToCp(player, targetId, cpName);
                else if (rawSlot == 11) executeUpdateCp(player, cpName);
                else if (rawSlot == 13) startRenameInput(player, cpName);
                else if (rawSlot == 15) startDescriptionInput(player, cpName);
                else if (rawSlot == 17) executeDeleteCp(player, cpName);
            } else {
                if (rawSlot == 12)      executeTeleportToCp(player, targetId, cpName);
                else if (rawSlot == 14) executeCloneCp(player, targetId, cpName);
            }
        });
    }

    public void handleMainMenuClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= GUI_SIZE) return;

        UUID playerId = player.getUniqueId();
        UUID targetId = viewingPlayerId.getOrDefault(playerId, playerId);
        boolean isSelf = targetId.equals(playerId);
        SortOrder order = playerSortOrders.getOrDefault(playerId, SortOrder.NAME_ASC);
        String query = playerSearchQuery.get(playerId);
        double px = player.getLocation().getX();
        double pz = player.getLocation().getZ();
        List<String> names = checkpointManager.getSortedFilteredCheckpointNames(
            targetId, order, query, px, pz);
        int page = menuPages.getOrDefault(playerId, 0);
        int totalPages = Math.max(1, (int) Math.ceil(Math.max(1, names.size()) / (double) ITEMS_PER_PAGE));

        // Player head: open player selector
        if (rawSlot == SLOT_PLAYER_HEAD) {
            openPlayerSelectMenu(player);
            return;
        }

        // CP item click (rows 1-4, cols 1-7)
        int row = rawSlot / 9;
        int col = rawSlot % 9;
        if (row >= 1 && row <= 4 && col >= 1 && col <= 7) {
            int itemIndex = (row - 1) * 7 + (col - 1);
            int dataIndex = page * ITEMS_PER_PAGE + itemIndex;
            if (dataIndex >= names.size()) return;
            String name = names.get(dataIndex);
            if (event.isRightClick()) {
                openCpOperationMenu(player, name, targetId);
                return;
            }
            if (!isSelf) {
                executeTeleportToCp(player, targetId, name);
                return;
            }
            if (checkpointManager.selectNamedCheckpoint(playerId, name)) {
                checkpointManager.getSelectedNamedCheckpointName(playerId)
                    .ifPresent(actualName -> markLastSelection(playerId, SelectionType.NAMED, actualName));
                player.sendMessage(ChatColor.AQUA + Messages.cpSelected(playerId, name));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                player.closeInventory();
            }
            return;
        }

        // Bottom row controls
        if (rawSlot == SLOT_PREVIOUS && event.isLeftClick() && page > 0) {
            openCheckpointMenu(player, page - 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.9f);
            return;
        }
        if (rawSlot == SLOT_SORT && event.isLeftClick()) {
            openSortMenu(player);
            return;
        }
        if (rawSlot == SLOT_SEARCH && event.isLeftClick()) {
            startSearchInput(player);
            return;
        }
        if (rawSlot == SLOT_SEARCH && event.isRightClick()) {
            playerSearchQuery.remove(player.getUniqueId());
            menuPages.put(player.getUniqueId(), 0);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            openCheckpointMenu(player, 0);
            return;
        }
        if (rawSlot == SLOT_NEXT && event.isLeftClick() && page < totalPages - 1) {
            openCheckpointMenu(player, page + 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
    }

    // -----------------------------------------------------------------------
    // Chat input handlers (called by ChatInputListener)
    // -----------------------------------------------------------------------

    public boolean tryHandleDescriptionInput(UUID playerId, Player player, String message) {
        String cpName = awaitingDescriptionInput.remove(playerId);
        if (cpName == null) return false;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + Messages.descCancelled(playerId));
                openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                return;
            }
            String desc = message.equalsIgnoreCase("clear") ? "" : message;
            boolean success = checkpointManager.setNamedCheckpointDescription(playerId, cpName, desc);
            if (success) {
                player.sendMessage(desc.isEmpty()
                    ? ChatColor.GREEN + Messages.descRemoved(playerId, cpName)
                    : ChatColor.GREEN + Messages.descSet(playerId, cpName));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            } else {
                player.sendMessage(ChatColor.RED + Messages.descNotFound(playerId, cpName));
            }
            openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
        });
        return true;
    }

    public boolean tryHandleRenameInput(UUID playerId, Player player, String message) {
        String oldName = awaitingRenameInput.remove(playerId);
        if (oldName == null) return false;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + Messages.renameCancelled(playerId));
                openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                return;
            }
            RenameResult result = checkpointManager.renameNamedCheckpoint(playerId, oldName, message);
            switch (result) {
                case SUCCESS -> {
                    player.sendMessage(ChatColor.GREEN + Messages.renameSuccess(playerId, oldName, message));
                    openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                }
                case OLD_NOT_FOUND -> {
                    player.sendMessage(ChatColor.RED + Messages.renameNotFound(playerId, oldName));
                    openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                }
                case NEW_ALREADY_EXISTS -> {
                    player.sendMessage(ChatColor.RED + Messages.renameExists(playerId, message));
                    awaitingRenameInput.put(playerId, oldName);
                    player.sendMessage(ChatColor.GRAY + Messages.renameRetryHint(playerId));
                }
            }
        });
        return true;
    }

    public boolean tryHandleSearchInput(UUID playerId, Player player, String message) {
        if (!awaitingSearchInput.remove(playerId)) return false;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + Messages.searchCancelled(playerId));
                openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                return;
            }
            if (message.equalsIgnoreCase("clear")) {
                playerSearchQuery.remove(playerId);
                player.sendMessage(ChatColor.GREEN + Messages.searchCleared(playerId));
                menuPages.put(playerId, 0);
                openCheckpointMenu(player, 0);
                return;
            }
            playerSearchQuery.put(playerId, message);
            player.sendMessage(ChatColor.GREEN + Messages.searchSearching(playerId, message));
            menuPages.put(playerId, 0);
            openCheckpointMenu(player, 0);
        });
        return true;
    }

    // -----------------------------------------------------------------------
    // Chat input start methods
    // -----------------------------------------------------------------------

    public void startSearchInput(Player player) {
        UUID playerId = player.getUniqueId();
        awaitingSearchInput.add(playerId);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.AQUA + "  " + Messages.searchPromptTitle(playerId));
        player.sendMessage(ChatColor.YELLOW + "  " + Messages.searchPromptMsg(playerId));
        player.sendMessage(ChatColor.GRAY + "  「" + ChatColor.RED + "cancel"
            + ChatColor.GRAY + "」" + Messages.searchCancel(playerId));
        player.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }

    public void startRenameInput(Player viewer, String oldName) {
        UUID playerId = viewer.getUniqueId();
        awaitingRenameInput.put(playerId, oldName);
        viewer.closeInventory();
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        viewer.sendMessage(ChatColor.AQUA + "  " + Messages.renamePromptTitle(playerId, oldName));
        viewer.sendMessage(ChatColor.YELLOW + "  " + Messages.renamePromptMsg(playerId));
        viewer.sendMessage(ChatColor.GRAY + "  『" + ChatColor.RED + "cancel"
            + ChatColor.GRAY + "』" + Messages.renameCancelWord(playerId));
        viewer.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        viewer.sendMessage("");
    }

    public void startDescriptionInput(Player viewer, String cpName) {
        UUID playerId = viewer.getUniqueId();
        awaitingDescriptionInput.put(playerId, cpName);
        viewer.closeInventory();
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        viewer.sendMessage(ChatColor.AQUA + "  " + Messages.descPromptTitle(playerId, cpName));
        viewer.sendMessage(ChatColor.YELLOW + "  " + Messages.descPromptMsg(playerId));
        viewer.sendMessage(ChatColor.GRAY + "  『" + ChatColor.RED + "clear"
            + ChatColor.GRAY + "』" + Messages.descClearWord(playerId) + "、『" + ChatColor.RED + "cancel"
            + ChatColor.GRAY + "』" + Messages.descCancelWord(playerId));
        viewer.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        viewer.sendMessage("");
    }

    // -----------------------------------------------------------------------
    // Player search chat input
    // -----------------------------------------------------------------------

    public boolean tryHandlePlayerSearchInput(UUID playerId, Player player, String message) {
        if (!awaitingPlayerSearchInput.remove(playerId)) return false;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + Messages.searchCancelled(playerId));
                openPlayerSelectMenu(player);
                return;
            }
            if (message.equalsIgnoreCase("clear")) {
                playerSelectSearchQuery.remove(playerId);
                player.sendMessage(ChatColor.GREEN + Messages.searchCleared(playerId));
                playerSelectPages.put(playerId, 0);
                openPlayerSelectMenu(player);
                return;
            }
            playerSelectSearchQuery.put(playerId, message);
            player.sendMessage(ChatColor.GREEN + Messages.searchSearching(playerId, message));
            playerSelectPages.put(playerId, 0);
            openPlayerSelectMenu(player);
        });
        return true;
    }

    public void startPlayerSearchInput(Player player) {
        UUID playerId = player.getUniqueId();
        awaitingPlayerSearchInput.add(playerId);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.AQUA + "  " + Messages.playerSearchTitle(playerId));
        player.sendMessage(ChatColor.YELLOW + "  " + Messages.playerSearchMsg(playerId));
        player.sendMessage(ChatColor.GRAY + "  「" + ChatColor.RED + "cancel"
            + ChatColor.GRAY + "」" + Messages.searchCancel(playerId));
        player.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }

    // -----------------------------------------------------------------------
    // CP operation executors
    // -----------------------------------------------------------------------

    public void executeTeleportToCp(Player viewer, UUID targetId, String cpName) {
        UUID viewerId = viewer.getUniqueId();
        Optional<Checkpoint> cpOpt = checkpointManager.getNamedCheckpoint(targetId, cpName);
        if (cpOpt.isEmpty()) {
            viewer.sendMessage(ChatColor.RED + Messages.cpNotFound(viewerId));
            viewer.closeInventory();
            return;
        }
        Checkpoint checkpoint = cpOpt.get();
        World world = Bukkit.getWorld(checkpoint.worldName());
        if (world == null) {
            viewer.sendMessage(ChatColor.RED + Messages.worldNotFound(viewerId));
            viewer.closeInventory();
            return;
        }
        Location destination = new Location(world,
            checkpoint.x(), checkpoint.y(), checkpoint.z(),
            checkpoint.yaw(), checkpoint.pitch());
        viewer.closeInventory();
        preparePlayerForTeleport(viewer);
        ensureChunkLoaded(destination);
        attemptTeleport(viewer, destination, TELEPORT_MAX_ATTEMPTS);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    private void executeUpdateCp(Player viewer, String cpName) {
        UUID viewerId = viewer.getUniqueId();
        Location location = viewer.getLocation();
        World world = location.getWorld();
        if (world == null) {
            viewer.sendMessage(ChatColor.RED + Messages.cmdWorldError(viewerId));
            return;
        }
        Checkpoint updated = new Checkpoint(world.getName(),
            location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
        boolean success = checkpointManager.updateNamedCheckpoint(viewerId, cpName, updated);
        viewer.closeInventory();
        if (success) {
            viewer.sendMessage(ChatColor.GREEN + Messages.cpUpdateSuccess(viewerId, cpName));
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            openCheckpointMenu(viewer, menuPages.getOrDefault(viewerId, 0));
        } else {
            viewer.sendMessage(ChatColor.RED + Messages.cpUpdateFailed(viewerId));
        }
    }

    private void executeDeleteCp(Player viewer, String cpName) {
        UUID viewerId = viewer.getUniqueId();
        boolean success = checkpointManager.removeNamedCheckpoint(viewerId, cpName);
        notifyNamedCheckpointDeleted(viewerId, cpName);
        viewer.closeInventory();
        if (success) {
            viewer.sendMessage(ChatColor.GREEN + Messages.cpDeleteSuccess(viewerId, cpName));
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            openCheckpointMenu(viewer, Math.max(0, menuPages.getOrDefault(viewerId, 0) - 1));
        } else {
            viewer.sendMessage(ChatColor.RED + Messages.cpDeleteFailed(viewerId));
        }
    }

    private void executeCloneCp(Player viewer, UUID targetId, String cpName) {
        UUID viewerId = viewer.getUniqueId();
        Optional<Checkpoint> cpOpt = checkpointManager.getNamedCheckpoint(targetId, cpName);
        if (cpOpt.isEmpty()) {
            viewer.sendMessage(ChatColor.RED + Messages.cpNotFound(viewerId));
            viewer.closeInventory();
            return;
        }
        Checkpoint src = cpOpt.get();
        Checkpoint cloned = new Checkpoint(src.worldName(),
            src.x(), src.y(), src.z(), src.yaw(), src.pitch(),
            java.time.Instant.now(), java.time.Instant.now(), src.description());
        boolean success = checkpointManager.addNamedCheckpoint(viewerId, cpName, cloned);
        viewer.closeInventory();
        if (success) {
            checkpointManager.recordClone(viewerId, targetId);
            viewer.sendMessage(ChatColor.GREEN + Messages.cpCloneSuccess(viewerId, cpName));
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        } else {
            viewer.sendMessage(ChatColor.YELLOW + Messages.cpCloneDuplicate(viewerId, cpName));
        }
    }

    // -----------------------------------------------------------------------
    // Close cleanup
    // -----------------------------------------------------------------------

    public void scheduleMenuCloseCleanup(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String newTitle = player.getOpenInventory().getTitle();
            if (!isOurMenu(newTitle)) {
                UUID playerId = player.getUniqueId();
                menuPages.remove(playerId);
                pendingOperationCp.remove(playerId);
                playerSelectPages.remove(playerId);
                playerSelectSearchQuery.remove(playerId);
            }
        }, 1L);
    }

    // -----------------------------------------------------------------------
    // Player list helper
    // -----------------------------------------------------------------------

    private List<UUID> getSortedFilteredPlayers(UUID viewerId, PlayerSortOrder order,
            String query, double px, double pz) {
        Set<UUID> allPlayers = checkpointManager.getAllPlayersWithData();
        List<UUID> players = new ArrayList<>(allPlayers);

        // Filter by name query
        if (query != null && !query.isBlank()) {
            String lower = query.toLowerCase();
            players.removeIf(uuid -> {
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                return name == null || !name.toLowerCase().contains(lower);
            });
        }

        // Sort
        Comparator<UUID> comparator;
        if (order == PlayerSortOrder.NAME_DESC) {
            Comparator<UUID> c = Comparator.comparing(
                (UUID uuid) -> {
                    String n = Bukkit.getOfflinePlayer(uuid).getName();
                    return n != null ? n : "";
                }, String.CASE_INSENSITIVE_ORDER);
            comparator = c.reversed();
        } else if (order == PlayerSortOrder.CLONED_BY_ME_DESC) {
            Comparator<UUID> c = Comparator.comparing(
                (UUID uuid) -> checkpointManager.getCloneTime(viewerId, uuid).orElse(Instant.MIN));
            comparator = c.reversed();
        } else if (order == PlayerSortOrder.CLONED_COUNT_DESC) {
            Comparator<UUID> c = Comparator.comparingInt(
                (UUID uuid) -> checkpointManager.getClonedCount(uuid));
            comparator = c.reversed();
        } else if (order == PlayerSortOrder.DISTANCE_ASC) {
            comparator = Comparator.comparingDouble(
                (UUID uuid) -> checkpointManager.getNearestCpDistanceSq(uuid, px, pz));
        } else if (order == PlayerSortOrder.LAST_ACTIVITY_DESC) {
            Comparator<UUID> c = Comparator.comparing(
                (UUID uuid) -> checkpointManager.getLastActivityTime(uuid).orElse(Instant.MIN));
            comparator = c.reversed();
        } else if (order == PlayerSortOrder.LAST_ACTIVITY_ASC) {
            comparator = Comparator.comparing(
                (UUID uuid) -> checkpointManager.getLastActivityTime(uuid).orElse(Instant.MAX));
        } else {
            // NAME_ASC (default)
            comparator = Comparator.comparing(
                (UUID uuid) -> {
                    String n = Bukkit.getOfflinePlayer(uuid).getName();
                    return n != null ? n : "";
                }, String.CASE_INSENSITIVE_ORDER);
        }

        players.sort(comparator);
        return players;
    }

    // -----------------------------------------------------------------------
    // Teleport helpers
    // -----------------------------------------------------------------------

    private void preparePlayerForTeleport(Player player) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
            player.eject();
        }
        if (player.isGliding()) {
            player.setGliding(false);
        }
        if (player.isSleeping()) {
            player.wakeup(false);
        }
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
    }

    private void ensureChunkLoaded(Location destination) {
        World world = destination.getWorld();
        if (world == null) return;
        int chunkX = destination.getBlockX() >> 4;
        int chunkZ = destination.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.loadChunk(chunkX, chunkZ);
        }
    }

    private void attemptTeleport(Player player, Location destination, int attemptsRemaining) {
        if (attemptsRemaining <= 0) {
            player.sendMessage(ChatColor.RED + Messages.teleportFailed(player.getUniqueId()));
            return;
        }
        boolean success = player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (success) {
            stabilizePlayer(player);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin,
                () -> attemptTeleport(player, destination, attemptsRemaining - 1),
                TELEPORT_RETRY_DELAY_TICKS);
        }
    }

    /**
     * Immediately resets the player's velocity and fall distance after teleport,
     * and schedules a follow-up reset 1 tick later to ensure the server has
     * fully processed the teleport before the player can accumulate momentum.
     */
    private void stabilizePlayer(Player player) {
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setVelocity(new Vector(0, 0, 0));
                player.setFallDistance(0f);
            }
        }, 1L);
    }

    private void markLastSelection(UUID playerId, SelectionType type, String identifier) {
        lastSelections.put(playerId, new LastSelection(type, identifier));
    }
}
