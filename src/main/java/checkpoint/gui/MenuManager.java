package checkpoint.gui;

import checkpoint.manager.CheckpointManager;
import checkpoint.model.Checkpoint;
import checkpoint.model.RenameResult;
import checkpoint.model.SortOrder;
import java.util.ArrayList;
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
    }

    public boolean isOurMenu(String title) {
        return GUI_TITLE.equals(title) || SORT_TITLE.equals(title)
            || PLAYER_SELECT_TITLE.equals(title) || CP_OPERATION_TITLE.equals(title);
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
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + "ワールド情報が取得できませんでした。");
            return;
        }

        Checkpoint checkpoint = new Checkpoint(
            world.getName(), location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
        checkpointManager.setQuickCheckpoint(player.getUniqueId(), checkpoint);
        markLastSelection(player.getUniqueId(), SelectionType.QUICK, null);
        player.sendMessage(ChatColor.GREEN + "チェックポイントを保存しました！");
        player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
    }

    // -----------------------------------------------------------------------
    // Nether star teleport
    // -----------------------------------------------------------------------

    public void handleCheckpointTeleport(Player player) {
        UUID playerId = player.getUniqueId();
        Optional<Checkpoint> target = resolveTeleportTarget(playerId);

        if (target.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "チェックポイントがまだ登録されていません。");
            return;
        }

        Checkpoint checkpoint = target.get();
        World world = Bukkit.getWorld(checkpoint.worldName());
        if (world == null) {
            player.sendMessage(ChatColor.RED + "チェックポイントのワールドが見つかりませんでした。");
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

        Inventory inventory = Bukkit.createInventory(viewer, GUI_SIZE, GUI_TITLE);

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
        inventory.setItem(SLOT_PLAYER_HEAD, ItemFactory.createPlayerHeadItem(Bukkit.getOfflinePlayer(targetId), isSelf));

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
                        inventory.setItem(slot, ItemFactory.createCheckpointPaper(name, checkpoint.get(), selected, isSelf));
                    }
                }
                itemIndex++;
            }
        }

        if (names.isEmpty()) {
            inventory.setItem(22, ItemFactory.createEmptyNoticeItem());
        }

        // Bottom row controls
        if (totalPages > 1 && page > 0) {
            inventory.setItem(SLOT_PREVIOUS, ItemFactory.createNavItem(false, page, totalPages));
        } else {
            inventory.setItem(SLOT_PREVIOUS, ItemFactory.createDisabledNavItem(ChatColor.DARK_GRAY + "前のページなし"));
        }
        inventory.setItem(SLOT_SEARCH, ItemFactory.createAnvilSearchItem());
        inventory.setItem(SLOT_INFO, ItemFactory.createInfoItem(page + 1, totalPages, names.size(), order, query));
        inventory.setItem(SLOT_SORT, ItemFactory.createSortButtonItem(order));
        if (totalPages > 1 && page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, ItemFactory.createNavItem(true, page, totalPages));
        } else {
            inventory.setItem(SLOT_NEXT, ItemFactory.createDisabledNavItem(ChatColor.DARK_GRAY + "次のページなし"));
        }

        viewer.openInventory(inventory);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.2f);
    }

    public void openSortMenu(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, SORT_TITLE);
        SortOrder current = playerSortOrders.getOrDefault(player.getUniqueId(), SortOrder.NAME_ASC);
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
                dyeColors[i % dyeColors.length], orders[i], orders[i] == current));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    public void openPlayerSelectMenu(Player viewer) {
        List<? extends Player> onlineList = new ArrayList<>(Bukkit.getOnlinePlayers());
        UUID viewerId = viewer.getUniqueId();
        int psPage = playerSelectPages.getOrDefault(viewerId, 0);
        int totalItems = onlineList.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE));
        psPage = Math.max(0, Math.min(psPage, totalPages - 1));
        playerSelectPages.put(viewerId, psPage);

        Inventory inv = Bukkit.createInventory(viewer, GUI_SIZE, PLAYER_SELECT_TITLE);

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
                if (dataIndex < onlineList.size()) {
                    Player target = onlineList.get(dataIndex);
                    UUID targetId = target.getUniqueId();
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
                    if (skullMeta != null) {
                        skullMeta.setOwningPlayer(target);
                        boolean isViewing = targetId.equals(currentTarget);
                        skullMeta.setDisplayName(
                            (targetId.equals(viewerId) ? ChatColor.AQUA : ChatColor.YELLOW) + target.getName());
                        List<String> lore = new ArrayList<>();
                        lore.add(ChatColor.GRAY + "CP数: " +
                            checkpointManager.getNamedCheckpointNames(targetId).size());
                        if (isViewing) {
                            lore.add(ChatColor.GREEN + "✔ 現在表示中");
                            skullMeta.addEnchant(Enchantment.LUCK, 1, true);
                            skullMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                        } else {
                            lore.add(ChatColor.YELLOW + "クリックでCP一覧を表示");
                        }
                        skullMeta.setLore(lore);
                        skullMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                        skullMeta.getPersistentDataContainer()
                            .set(targetUuidKey, PersistentDataType.STRING, targetId.toString());
                        head.setItemMeta(skullMeta);
                    }
                    inv.setItem(slot, head);
                }
                itemIndex++;
            }
        }

        // Navigation
        if (totalPages > 1 && psPage > 0) {
            inv.setItem(SLOT_PREVIOUS, ItemFactory.createNavItem(false, psPage, totalPages));
        } else {
            inv.setItem(SLOT_PREVIOUS, ItemFactory.createDisabledNavItem(ChatColor.DARK_GRAY + "前のページなし"));
        }
        ItemStack infoItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.AQUA + "ページ情報");
            infoMeta.setLore(List.of(
                ChatColor.GRAY + "ページ: " + (psPage + 1) + " / " + totalPages,
                ChatColor.GRAY + "プレイヤー数: " + totalItems
            ));
            infoItem.setItemMeta(infoMeta);
        }
        inv.setItem(SLOT_INFO, infoItem);
        if (totalPages > 1 && psPage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, ItemFactory.createNavItem(true, psPage, totalPages));
        } else {
            inv.setItem(SLOT_NEXT, ItemFactory.createDisabledNavItem(ChatColor.DARK_GRAY + "次のページなし"));
        }

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    public void openCpOperationMenu(Player viewer, String cpName, UUID targetId) {
        UUID viewerId = viewer.getUniqueId();
        boolean isSelf = targetId.equals(viewerId);
        pendingOperationCp.put(viewerId, cpName);
        Inventory inv = Bukkit.createInventory(viewer, 27, CP_OPERATION_TITLE);

        for (int s = 0; s < 9; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        for (int s = 18; s < 27; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        for (int s = 9; s < 18; s++) inv.setItem(s, ItemFactory.createGlassDeco(Material.LIGHT_BLUE_STAINED_GLASS_PANE));

        checkpointManager.getNamedCheckpoint(targetId, cpName)
            .ifPresent(cp -> inv.setItem(4, ItemFactory.createCheckpointPaperInfo(cpName, cp)));

        if (isSelf) {
            inv.setItem(9,  ItemFactory.createOperationWoolItem(Material.GREEN_WOOL,
                ChatColor.GREEN + "テレポート", "クリックでこのCPにテレポート"));
            inv.setItem(11, ItemFactory.createOperationWoolItem(Material.BLUE_WOOL,
                ChatColor.AQUA + "座標を更新", "現在の座標でこのCPを上書き"));
            inv.setItem(13, ItemFactory.createOperationWoolItem(Material.YELLOW_WOOL,
                ChatColor.YELLOW + "リネーム", "このCPの名前を変更"));
            inv.setItem(15, ItemFactory.createOperationWoolItem(Material.ORANGE_WOOL,
                ChatColor.GOLD + "説明を変更", "このCPの説明文を変更"));
            inv.setItem(17, ItemFactory.createOperationWoolItem(Material.RED_WOOL,
                ChatColor.RED + "削除", "このCPを削除する"));
        } else {
            OfflinePlayer tp = Bukkit.getOfflinePlayer(targetId);
            String tName = tp.getName() != null ? tp.getName() : "不明";
            inv.setItem(9,  ItemFactory.createOperationWoolItem(Material.GREEN_WOOL,
                ChatColor.GREEN + "テレポート", "クリックでこのCPにテレポート"));
            inv.setItem(11, ItemFactory.createOperationWoolItem(Material.CYAN_WOOL,
                ChatColor.AQUA + "クローン", tName + " のCPを自分のリストに追加"));
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

    public void handlePlayerSelectMenuClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        UUID viewerId = player.getUniqueId();
        int psPage = playerSelectPages.getOrDefault(viewerId, 0);

        if (rawSlot == SLOT_PREVIOUS && event.isLeftClick() && psPage > 0) {
            playerSelectPages.put(viewerId, psPage - 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.9f);
            openPlayerSelectMenu(player);
            return;
        }
        if (rawSlot == SLOT_NEXT && event.isLeftClick()) {
            int total = (int) Math.ceil(Bukkit.getOnlinePlayers().size() / (double) ITEMS_PER_PAGE);
            if (psPage < total - 1) {
                playerSelectPages.put(viewerId, psPage + 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                openPlayerSelectMenu(player);
            }
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
                if (rawSlot == 9)       executeTeleportToCp(player, targetId, cpName);
                else if (rawSlot == 11) executeCloneCp(player, targetId, cpName);
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
                player.sendMessage(ChatColor.AQUA + "チェックポイント『" + name + "』を選択しました。");
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
                player.sendMessage(ChatColor.GRAY + "説明変更をキャンセルしました。");
                openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                return;
            }
            String desc = message.equalsIgnoreCase("clear") ? "" : message;
            boolean success = checkpointManager.setNamedCheckpointDescription(playerId, cpName, desc);
            if (success) {
                player.sendMessage(desc.isEmpty()
                    ? ChatColor.GREEN + "『" + cpName + "』の説明を削除しました。"
                    : ChatColor.GREEN + "『" + cpName + "』の説明を設定しました。");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            } else {
                player.sendMessage(ChatColor.RED + "CP『" + cpName + "』が見つかりませんでした。");
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
                player.sendMessage(ChatColor.GRAY + "リネームをキャンセルしました。");
                openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                return;
            }
            RenameResult result = checkpointManager.renameNamedCheckpoint(playerId, oldName, message);
            switch (result) {
                case SUCCESS -> {
                    player.sendMessage(ChatColor.GREEN + "『" + oldName + "』を『" + message + "』にリネームしました。");
                    openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                }
                case OLD_NOT_FOUND -> {
                    player.sendMessage(ChatColor.RED + "CP『" + oldName + "』が見つかりませんでした。");
                    openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                }
                case NEW_ALREADY_EXISTS -> {
                    player.sendMessage(ChatColor.RED + "CP『" + message + "』は既に存在します。再入力してください。");
                    awaitingRenameInput.put(playerId, oldName);
                    player.sendMessage(ChatColor.GRAY + "  新しいCP名を入力 / 『cancel』でキャンセル");
                }
            }
        });
        return true;
    }

    public boolean tryHandleSearchInput(UUID playerId, Player player, String message) {
        if (!awaitingSearchInput.remove(playerId)) return false;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + "検索をキャンセルしました。");
                openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                return;
            }
            if (message.equalsIgnoreCase("clear")) {
                playerSearchQuery.remove(playerId);
                player.sendMessage(ChatColor.GREEN + "検索を解除しました。");
                menuPages.put(playerId, 0);
                openCheckpointMenu(player, 0);
                return;
            }
            playerSearchQuery.put(playerId, message);
            player.sendMessage(ChatColor.GREEN + "「" + message + "」で検索中...");
            menuPages.put(playerId, 0);
            openCheckpointMenu(player, 0);
        });
        return true;
    }

    // -----------------------------------------------------------------------
    // Chat input start methods
    // -----------------------------------------------------------------------

    public void startSearchInput(Player player) {
        awaitingSearchInput.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.AQUA + "  チェックポイント検索");
        player.sendMessage(ChatColor.YELLOW + "  検索したいCP名をチャットに入力してください。");
        player.sendMessage(ChatColor.GRAY + "  「" + ChatColor.RED + "cancel"
            + ChatColor.GRAY + "」で取消");
        player.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }

    public void startRenameInput(Player viewer, String oldName) {
        awaitingRenameInput.put(viewer.getUniqueId(), oldName);
        viewer.closeInventory();
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        viewer.sendMessage(ChatColor.AQUA + "  CP リネーム: " + ChatColor.GOLD + oldName);
        viewer.sendMessage(ChatColor.YELLOW + "  新しいCP名をチャットに入力してください。");
        viewer.sendMessage(ChatColor.GRAY + "  『" + ChatColor.RED + "cancel"
            + ChatColor.GRAY + "』でキャンセル");
        viewer.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        viewer.sendMessage("");
    }

    public void startDescriptionInput(Player viewer, String cpName) {
        awaitingDescriptionInput.put(viewer.getUniqueId(), cpName);
        viewer.closeInventory();
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        viewer.sendMessage(ChatColor.AQUA + "  CP 説明の変更: " + ChatColor.GOLD + cpName);
        viewer.sendMessage(ChatColor.YELLOW + "  説明文をチャットに入力してください。");
        viewer.sendMessage(ChatColor.GRAY + "  『" + ChatColor.RED + "clear"
            + ChatColor.GRAY + "』で説明を削除、『" + ChatColor.RED + "cancel"
            + ChatColor.GRAY + "』でキャンセル");
        viewer.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        viewer.sendMessage("");
    }

    // -----------------------------------------------------------------------
    // CP operation executors
    // -----------------------------------------------------------------------

    public void executeTeleportToCp(Player viewer, UUID targetId, String cpName) {
        Optional<Checkpoint> cpOpt = checkpointManager.getNamedCheckpoint(targetId, cpName);
        if (cpOpt.isEmpty()) {
            viewer.sendMessage(ChatColor.RED + "CPが見つかりません。");
            viewer.closeInventory();
            return;
        }
        Checkpoint checkpoint = cpOpt.get();
        World world = Bukkit.getWorld(checkpoint.worldName());
        if (world == null) {
            viewer.sendMessage(ChatColor.RED + "チェックポイントのワールドが見つかりませんでした。");
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
        Location location = viewer.getLocation();
        World world = location.getWorld();
        if (world == null) {
            viewer.sendMessage(ChatColor.RED + "ワールド情報が取得できませんでした。");
            return;
        }
        Checkpoint updated = new Checkpoint(world.getName(),
            location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
        UUID viewerId = viewer.getUniqueId();
        boolean success = checkpointManager.updateNamedCheckpoint(viewerId, cpName, updated);
        viewer.closeInventory();
        if (success) {
            viewer.sendMessage(ChatColor.GREEN + "チェックポイント『" + cpName + "』を現在地に更新しました。");
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            openCheckpointMenu(viewer, menuPages.getOrDefault(viewerId, 0));
        } else {
            viewer.sendMessage(ChatColor.RED + "更新に失敗しました。");
        }
    }

    private void executeDeleteCp(Player viewer, String cpName) {
        UUID viewerId = viewer.getUniqueId();
        boolean success = checkpointManager.removeNamedCheckpoint(viewerId, cpName);
        notifyNamedCheckpointDeleted(viewerId, cpName);
        viewer.closeInventory();
        if (success) {
            viewer.sendMessage(ChatColor.GREEN + "チェックポイント『" + cpName + "』を削除しました。");
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            openCheckpointMenu(viewer, Math.max(0, menuPages.getOrDefault(viewerId, 0) - 1));
        } else {
            viewer.sendMessage(ChatColor.RED + "削除に失敗しました。");
        }
    }

    private void executeCloneCp(Player viewer, UUID targetId, String cpName) {
        Optional<Checkpoint> cpOpt = checkpointManager.getNamedCheckpoint(targetId, cpName);
        if (cpOpt.isEmpty()) {
            viewer.sendMessage(ChatColor.RED + "CPが見つかりません。");
            viewer.closeInventory();
            return;
        }
        Checkpoint src = cpOpt.get();
        Checkpoint cloned = new Checkpoint(src.worldName(),
            src.x(), src.y(), src.z(), src.yaw(), src.pitch());
        boolean success = checkpointManager.addNamedCheckpoint(
            viewer.getUniqueId(), cpName, cloned);
        viewer.closeInventory();
        if (success) {
            viewer.sendMessage(ChatColor.GREEN + "チェックポイント『" + cpName + "』をクローンしました。");
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        } else {
            viewer.sendMessage(ChatColor.YELLOW + "同名のCPが既に存在します: 『" + cpName + "』");
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
            }
        }, 1L);
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
            player.sendMessage(ChatColor.RED + "テレポートに失敗しました。");
            return;
        }
        boolean success = player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (!success) {
            Bukkit.getScheduler().runTaskLater(plugin,
                () -> attemptTeleport(player, destination, attemptsRemaining - 1),
                TELEPORT_RETRY_DELAY_TICKS);
        }
    }

    private void markLastSelection(UUID playerId, SelectionType type, String identifier) {
        lastSelections.put(playerId, new LastSelection(type, identifier));
    }
}
