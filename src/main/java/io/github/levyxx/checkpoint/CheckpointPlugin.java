package io.github.levyxx.checkpoint;

import io.github.levyxx.checkpoint.CheckpointManager.Checkpoint;
import io.github.levyxx.checkpoint.CheckpointManager.SortOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class CheckpointPlugin extends JavaPlugin implements Listener {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------
    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 28;
    private static final int SLOT_PREVIOUS  = 45;
    private static final int SLOT_SEARCH    = 47;
    private static final int SLOT_INFO      = 49;
    private static final int SLOT_SORT      = 51;
    private static final int SLOT_NEXT      = 53;

    private static final String GUI_TITLE   = ChatColor.DARK_AQUA + "チェックポイント一覧";
    private static final String SORT_TITLE          = ChatColor.DARK_AQUA + "ソート方法を選択";
    private static final String PLAYER_SELECT_TITLE  = ChatColor.DARK_AQUA + "プレイヤーを選択";
    private static final String CP_OPERATION_TITLE   = ChatColor.DARK_AQUA + "CP操作";
    private static final int    SLOT_PLAYER_HEAD     = 4;

    private static final int TELEPORT_MAX_ATTEMPTS    = 5;
    private static final long TELEPORT_RETRY_DELAY_TICKS = 1L;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------
    private CheckpointManager checkpointManager;
    private NamespacedKey cpItemKey;
    private final Map<UUID, Integer>    menuPages        = new ConcurrentHashMap<>();
    private final Map<UUID, LastSelection> lastSelections = new ConcurrentHashMap<>();
    private final Map<UUID, SortOrder>  playerSortOrders = new ConcurrentHashMap<>();
    private final Map<UUID, String>     playerSearchQuery= new ConcurrentHashMap<>();
    private final Set<UUID>             awaitingSearchInput = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID>   viewingPlayerId     = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingOperationCp  = new ConcurrentHashMap<>();
    private final Map<UUID, String> awaitingRenameInput = new ConcurrentHashMap<>();

    private enum SelectionType { NAMED, QUICK }
    private record LastSelection(SelectionType type, String identifier) {}

    @Override
    public void onEnable() {
        this.checkpointManager = new CheckpointManager();
        this.cpItemKey = new NamespacedKey(this, "cp_utility_item");
        Bukkit.getPluginManager().registerEvents(this, this);

        PluginCommand cpCommand = getCommand("cp");
        if (cpCommand != null) {
            CheckpointCommand executor = new CheckpointCommand(this, this.checkpointManager);
            cpCommand.setExecutor(executor);
            cpCommand.setTabCompleter(executor);
        } else {
            getLogger().severe("Command /cp is not defined in plugin.yml");
        }

        getLogger().info("Checkpoint plugin enabled.");
    }

    @Override
    public void onDisable() {
        this.menuPages.clear();
        this.lastSelections.clear();
        this.playerSortOrders.clear();
        this.playerSearchQuery.clear();
        this.awaitingSearchInput.clear();
        this.viewingPlayerId.clear();
        this.pendingOperationCp.clear();
        this.awaitingRenameInput.clear();
        this.checkpointManager = null;
        getLogger().info("Checkpoint plugin disabled.");
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        ItemMeta meta = dropped.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(cpItemKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            item = event.getPlayer().getInventory().getItem(event.getHand());
        }
        if (item == null || item.getType().isAir()) {
            return;
        }

        Material type = item.getType();
        Player player = event.getPlayer();

        if (type == Material.NETHER_STAR
            && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            openCheckpointMenu(player, menuPages.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (type == Material.SLIME_BALL) {
            event.setCancelled(true);
            handleQuickCheckpointSave(player);
        } else if (type == Material.NETHER_STAR) {
            event.setCancelled(true);
            handleCheckpointTeleport(player);
        } else if (type == Material.HEART_OF_THE_SEA) {
            event.setCancelled(true);
            openCheckpointMenu(player, menuPages.getOrDefault(player.getUniqueId(), 0));
        } else if (type == Material.FEATHER) {
            event.setCancelled(true);
            handleGameModeToggle(player);
        }
    }

    void notifyNamedCheckpointSet(Player player, String rawName) {
        UUID playerId = player.getUniqueId();
        if (checkpointManager.selectNamedCheckpoint(playerId, rawName)) {
            checkpointManager.getSelectedNamedCheckpointName(playerId)
                .ifPresent(actualName -> markLastSelection(playerId, SelectionType.NAMED, actualName));
        }
    }

    void notifyNamedCheckpointDeleted(UUID playerId, String rawName) {
        lastSelections.computeIfPresent(playerId, (id, selection) ->
            selection.type == SelectionType.NAMED && selection.identifier != null
                && selection.identifier.equalsIgnoreCase(rawName)
                ? null
                : selection);
    }

    private void handleQuickCheckpointSave(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + "ワールド情報が取得できませんでした。");
            return;
        }

        Checkpoint checkpoint = new Checkpoint(
            world.getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
        checkpointManager.setQuickCheckpoint(player.getUniqueId(), checkpoint);
        markLastSelection(player.getUniqueId(), SelectionType.QUICK, null);
        player.sendMessage(ChatColor.GREEN + "チェックポイントを保存しました！");
        player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
    }

    private void handleCheckpointTeleport(Player player) {
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
            world,
            checkpoint.x(),
            checkpoint.y(),
            checkpoint.z(),
            checkpoint.yaw(),
            checkpoint.pitch()
        );

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
                    if (named.isPresent()) {
                        return named;
                    }
                    lastSelections.remove(playerId);
                }
                case QUICK -> {
                    Optional<Checkpoint> quick = checkpointManager.getQuickCheckpoint(playerId);
                    if (quick.isPresent()) {
                        return quick;
                    }
                    lastSelections.remove(playerId);
                }
            }
        }

        Optional<Checkpoint> selectedNamed = checkpointManager.getSelectedNamedCheckpoint(playerId);
        if (selectedNamed.isPresent()) {
            return selectedNamed;
        }

        return checkpointManager.getQuickCheckpoint(playerId);
    }

    private void openCheckpointMenu(Player viewer, int requestedPage) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = viewingPlayerId.getOrDefault(viewerId, viewerId);
        openCheckpointMenuFor(viewer, requestedPage, targetId);
    }

    private void openCheckpointMenuFor(Player viewer, int requestedPage, UUID targetId) {
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

        // --- Border decoration (top row, left/right columns, bottom row)
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                Material mat = (row + col) % 2 == 0
                    ? Material.CYAN_STAINED_GLASS_PANE
                    : Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                inventory.setItem(slot, createGlassDeco(mat));
            }
        }

        // --- Player head at top-row middle (slot 4): shows who is being viewed
        inventory.setItem(SLOT_PLAYER_HEAD, createPlayerHeadItem(Bukkit.getOfflinePlayer(targetId), isSelf));

        // --- Checkpoint items in inner area (rows 1-4, cols 1-7)
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
                        inventory.setItem(slot, createCheckpointPaper(name, checkpoint.get(), selected, isSelf));
                    }
                }
                itemIndex++;
            }
        }

        // Empty CP notice
        if (names.isEmpty()) {
            inventory.setItem(22, createEmptyNoticeItem());
        }

        // --- Bottom row controls (overwrite glass at specific slots)
        if (totalPages > 1 && page > 0) {
            inventory.setItem(SLOT_PREVIOUS, createNavItem(false, page, totalPages));
        } else {
            inventory.setItem(SLOT_PREVIOUS, createDisabledNavItem(ChatColor.DARK_GRAY + "前のページなし"));
        }
        inventory.setItem(SLOT_SEARCH, createAnvilSearchItem());
        inventory.setItem(SLOT_INFO, createInfoItem(page + 1, totalPages, names.size(), order, query));
        inventory.setItem(SLOT_SORT, createSortButtonItem(order));
        if (totalPages > 1 && page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, createNavItem(true, page, totalPages));
        } else {
            inventory.setItem(SLOT_NEXT, createDisabledNavItem(ChatColor.DARK_GRAY + "次のページなし"));
        }

        viewer.openInventory(inventory);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ---- Sort selection screen ----------------------------------------
        if (SORT_TITLE.equals(title)) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();
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
            return;
        }

        // ---- Player select screen -----------------------------------------
        if (PLAYER_SELECT_TITLE.equals(title)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
            ItemMeta clickedMeta = clicked.getItemMeta();
            if (clickedMeta == null) return;
            String uuidStr = clickedMeta.getPersistentDataContainer()
                .get(new NamespacedKey(this, "target_uuid"), PersistentDataType.STRING);
            if (uuidStr == null) return;
            UUID targetId = UUID.fromString(uuidStr);
            UUID viewerId = player.getUniqueId();
            viewingPlayerId.put(viewerId, targetId);
            menuPages.put(viewerId, 0);
            playerSearchQuery.remove(viewerId);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            openCheckpointMenuFor(player, 0, targetId);
            return;
        }

        // ---- CP operation menu -------------------------------------------
        if (CP_OPERATION_TITLE.equals(title)) {
            event.setCancelled(true);
            UUID viewerId = player.getUniqueId();
            String cpName = pendingOperationCp.get(viewerId);
            if (cpName == null) return;
            UUID targetId = viewingPlayerId.getOrDefault(viewerId, viewerId);
            boolean isSelf = targetId.equals(viewerId);
            int rawSlot = event.getRawSlot();
            if (isSelf) {
                if (rawSlot == 10) executeTeleportToCp(player, targetId, cpName);
                else if (rawSlot == 12) executeUpdateCp(player, cpName);
                else if (rawSlot == 14) startRenameInput(player, cpName);
                else if (rawSlot == 16) executeDeleteCp(player, cpName);
            } else {
                if (rawSlot == 11) executeTeleportToCp(player, targetId, cpName);
                else if (rawSlot == 15) executeCloneCp(player, targetId, cpName);
            }
            return;
        }

        // ---- Main CP list screen ------------------------------------------
        if (!GUI_TITLE.equals(title)) return;
        if (event.getClickedInventory() == null) return;
        event.setCancelled(true);

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
            // Left click: own CP → select and close / other's CP → teleport directly
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        boolean isOurMenu = GUI_TITLE.equals(title) || SORT_TITLE.equals(title)
            || PLAYER_SELECT_TITLE.equals(title) || CP_OPERATION_TITLE.equals(title);
        if (!isOurMenu) return;

        Bukkit.getScheduler().runTaskLater(this, () -> {
            String newTitle = player.getOpenInventory().getTitle();
            boolean newIsOurMenu = GUI_TITLE.equals(newTitle) || SORT_TITLE.equals(newTitle)
                || PLAYER_SELECT_TITLE.equals(newTitle) || CP_OPERATION_TITLE.equals(newTitle);
            if (!newIsOurMenu) {
                menuPages.remove(player.getUniqueId());
                pendingOperationCp.remove(player.getUniqueId());
            }
        }, 1L);
    }

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
        if (world == null) {
            return;
        }
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
            Bukkit.getScheduler().runTaskLater(this, () -> attemptTeleport(player, destination, attemptsRemaining - 1), TELEPORT_RETRY_DELAY_TICKS);
        }
    }

    private void markLastSelection(UUID playerId, SelectionType type, String identifier) {
        lastSelections.put(playerId, new LastSelection(type, identifier));
    }

    // -----------------------------------------------------------------------
    // Sort menu
    // -----------------------------------------------------------------------

    private void openSortMenu(Player player) {
        // 3-row inventory (27 slots): row 0 and row 2 = cyan glass decoration
        // row 1 (slots 9-17): dye per sort option, active one highlighted
        Inventory inv = Bukkit.createInventory(player, 27, SORT_TITLE);
        SortOrder current = playerSortOrders.getOrDefault(player.getUniqueId(), SortOrder.NAME_ASC);
        SortOrder[] orders = SortOrder.values();

        // Decoration: top row and bottom row
        for (int s = 0; s < 9; s++) {
            inv.setItem(s, createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        }
        for (int s = 18; s < 27; s++) {
            inv.setItem(s, createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        }
        // Fill middle row background
        for (int s = 9; s < 18; s++) {
            inv.setItem(s, createGlassDeco(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }

        // Dye items for each sort option: slots 10~17 (8 options max)
        Material[] dyeColors = {
            Material.LIME_DYE,    // NAME_ASC
            Material.GREEN_DYE,   // NAME_DESC
            Material.CYAN_DYE,    // CREATED_ASC
            Material.BLUE_DYE,    // CREATED_DESC
            Material.LIGHT_BLUE_DYE,  // UPDATED_ASC
            Material.PURPLE_DYE,  // UPDATED_DESC
            Material.YELLOW_DYE,  // DISTANCE_ASC
        };

        int dyeBase = 10;
        for (int i = 0; i < orders.length; i++) {
            SortOrder order = orders[i];
            Material dyeMat = dyeColors[i % dyeColors.length];
            boolean active = order == current;
            inv.setItem(dyeBase + i, createSortDyeItem(dyeMat, order, active));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    // -----------------------------------------------------------------------
    // Chat-based search input
    // -----------------------------------------------------------------------

    private void startSearchInput(Player player) {
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

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // ---- Rename input ------------------------------------------------
        if (awaitingRenameInput.containsKey(playerId)) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            String oldName = awaitingRenameInput.remove(playerId);
            Bukkit.getScheduler().runTask(this, () -> {
                if (message.equalsIgnoreCase("cancel")) {
                    player.sendMessage(ChatColor.GRAY + "リネームをキャンセルしました。");
                    openCheckpointMenu(player, menuPages.getOrDefault(playerId, 0));
                    return;
                }
                CheckpointManager.RenameResult result =
                    checkpointManager.renameNamedCheckpoint(playerId, oldName, message);
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
            return;
        }

        if (!awaitingSearchInput.remove(playerId)) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        Bukkit.getScheduler().runTask(this, () -> {
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
    }

    private void handleGameModeToggle(Player player) {
        GameMode current = player.getGameMode();
        if (current == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(ChatColor.GREEN + "ゲームモードをクリエイティブに変更しました。");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
            return;
        }

        if (current == GameMode.CREATIVE) {
            player.setGameMode(GameMode.ADVENTURE);
            player.sendMessage(ChatColor.GREEN + "ゲームモードをアドベンチャーに変更しました。");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
        }
    }

    public void giveCheckpointItems(Player player) {
        ItemStack netherStar = createUtilityItem(
            Material.NETHER_STAR,
            ChatColor.AQUA,
            "CheckPoint",
            List.of(ChatColor.GRAY + "左クリック: チェックポイント一覧", ChatColor.GRAY + "右クリック: テレポート")
        );

        ItemStack slimeBall = createUtilityItem(
            Material.SLIME_BALL,
            ChatColor.GREEN,
            "Set CheckPoint",
            List.of(ChatColor.GRAY + "右クリック: 現在地を保存")
        );

        ItemStack feather = createUtilityItem(
            Material.FEATHER,
            ChatColor.GOLD,
            "Change Gamemode",
            List.of(ChatColor.GRAY + "右クリック: クリエ/アドベンチャー切替")
        );

        ItemStack heart = createUtilityItem(
            Material.HEART_OF_THE_SEA,
            ChatColor.LIGHT_PURPLE,
            "CheckPoint List",
            List.of(ChatColor.GRAY + "右クリック: チェックポイント一覧")
        );

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(netherStar, slimeBall, feather, heart);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }

        player.sendMessage(ChatColor.AQUA + "チェックポイントアイテムを受け取りました。所持品を確認してください。");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.1f);
    }

    private ItemStack createUtilityItem(Material material, ChatColor color, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + displayName);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(cpItemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCheckpointPaper(String name, Checkpoint checkpoint, boolean selected, boolean isSelf) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ワールド: " + checkpoint.worldName());
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "X: %.1f Y: %.1f Z: %.1f", checkpoint.x(), checkpoint.y(), checkpoint.z()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "Yaw: %.1f Pitch: %.1f", checkpoint.yaw(), checkpoint.pitch()));
            lore.add("");
            if (isSelf) {
                lore.add(selected ? ChatColor.AQUA + "現在選択中"
                    : ChatColor.YELLOW + "左クリックで選択 / 右クリックで操作");
            } else {
                lore.add(ChatColor.YELLOW + "左クリックでテレポート / 右クリックで操作");
            }
            meta.setLore(lore);
            if (selected) {
                meta.addEnchant(Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            paper.setItemMeta(meta);
        }
        return paper;
    }

    private ItemStack createNavItem(boolean forward, int currentPage, int totalPages) {
        ItemStack item = new ItemStack(forward ? Material.SPECTRAL_ARROW : Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(forward ? ChatColor.GREEN + "次のページ" : ChatColor.GREEN + "前のページ");
            int targetPage = forward ? currentPage + 2 : currentPage;
            meta.setLore(List.of(
                ChatColor.GRAY + "ページ: " + targetPage + " / " + totalPages,
                ChatColor.YELLOW + "左クリックで移動"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createDisabledNavItem(String label) {
        ItemStack item = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(label);
            meta.setLore(List.of(ChatColor.DARK_GRAY + "ページ移動はできません"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(int currentPage, int totalPages, int totalCheckpoints,
                                     SortOrder order, String query) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "ページ情報");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ページ: " + currentPage + " / " + totalPages);
            lore.add(ChatColor.GRAY + "表示数: " + totalCheckpoints);
            lore.add(ChatColor.GRAY + "ソート: " + ChatColor.AQUA + order.label);
            if (query != null && !query.isBlank()) {
                lore.add(ChatColor.GRAY + "検索: " + ChatColor.YELLOW + query);
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "紙を左クリックで選択");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSortButtonItem(SortOrder current) {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "ソート方法を変更");
            meta.setLore(List.of(
                ChatColor.GRAY + "現在: " + ChatColor.YELLOW + current.label,
                ChatColor.YELLOW + "左クリックで選択画面を開く"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAnvilSearchItem() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + "CP名を検索");
            meta.setLore(List.of(
                ChatColor.GRAY + "左クリックで検索バーを開く",
                ChatColor.GRAY + "右クリックで検索を解除",
                ChatColor.GRAY + "部分一致でフィルタリングします"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlassDeco(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSortDyeItem(Material dye, SortOrder order, boolean active) {
        ItemStack item = new ItemStack(dye);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((active ? ChatColor.AQUA : ChatColor.WHITE) + order.label);
            List<String> lore = new ArrayList<>();
            if (active) {
                lore.add(ChatColor.GREEN + "✔ 現在選択中");
                meta.addEnchant(Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(ChatColor.YELLOW + "左クリックで選択");
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFillerItem() {
        return createGlassDeco(Material.CYAN_STAINED_GLASS_PANE);
    }

    private ItemStack createEmptyNoticeItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "チェックポイントがありません");
            meta.setLore(List.of(
                ChatColor.GRAY + "コマンド /cp set <名前> で登録できます",
                ChatColor.GRAY + "登録後に海洋の心をクリックすると表示されます"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    // -----------------------------------------------------------------------
    // Player select menu
    // -----------------------------------------------------------------------

    private void openPlayerSelectMenu(Player viewer) {
        List<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int rowCount = Math.max(1, (int) Math.ceil(online.size() / 9.0));
        int size = Math.min(54, rowCount * 9);
        Inventory inv = Bukkit.createInventory(viewer, size, PLAYER_SELECT_TITLE);
        NamespacedKey targetUuidKey = new NamespacedKey(this, "target_uuid");
        UUID viewerId = viewer.getUniqueId();
        UUID currentTarget = viewingPlayerId.getOrDefault(viewerId, viewerId);
        for (int i = 0; i < online.size() && i < 54; i++) {
            Player target = online.get(i);
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
            inv.setItem(i, head);
        }
        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    // -----------------------------------------------------------------------
    // CP operation menu
    // -----------------------------------------------------------------------

    private void openCpOperationMenu(Player viewer, String cpName, UUID targetId) {
        UUID viewerId = viewer.getUniqueId();
        boolean isSelf = targetId.equals(viewerId);
        pendingOperationCp.put(viewerId, cpName);
        Inventory inv = Bukkit.createInventory(viewer, 27, CP_OPERATION_TITLE);
        for (int s = 0; s < 9; s++) inv.setItem(s, createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        for (int s = 18; s < 27; s++) inv.setItem(s, createGlassDeco(Material.CYAN_STAINED_GLASS_PANE));
        for (int s = 9; s < 18; s++) inv.setItem(s, createGlassDeco(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        checkpointManager.getNamedCheckpoint(targetId, cpName)
            .ifPresent(cp -> inv.setItem(9, createCheckpointPaperInfo(cpName, cp)));
        if (isSelf) {
            inv.setItem(10, createOperationWoolItem(Material.GREEN_WOOL,
                ChatColor.GREEN + "テレポート", "クリックでこのCPにテレポート"));
            inv.setItem(12, createOperationWoolItem(Material.BLUE_WOOL,
                ChatColor.AQUA + "座標を更新", "現在の座標でこのCPを上書き"));
            inv.setItem(14, createOperationWoolItem(Material.YELLOW_WOOL,
                ChatColor.YELLOW + "リネーム", "このCPの名前を変更"));
            inv.setItem(16, createOperationWoolItem(Material.RED_WOOL,
                ChatColor.RED + "削除", "このCPを削除する"));
        } else {
            OfflinePlayer tp = Bukkit.getOfflinePlayer(targetId);
            String tName = tp.getName() != null ? tp.getName() : "不明";
            inv.setItem(11, createOperationWoolItem(Material.GREEN_WOOL,
                ChatColor.GREEN + "テレポート", "クリックでこのCPにテレポート"));
            inv.setItem(15, createOperationWoolItem(Material.CYAN_WOOL,
                ChatColor.AQUA + "クローン", tName + " のCPを自分のリストに追加"));
        }
        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    private void executeTeleportToCp(Player viewer, UUID targetId, String cpName) {
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
        boolean success = checkpointManager.updateNamedCheckpoint(
            viewer.getUniqueId(), cpName, updated);
        viewer.closeInventory();
        if (success) {
            viewer.sendMessage(ChatColor.GREEN + "チェックポイント『" + cpName + "』を現在地に更新しました。");
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
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

    private void startRenameInput(Player viewer, String oldName) {
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

    private ItemStack createPlayerHeadItem(OfflinePlayer target, boolean isSelf) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            String name = target.getName() != null ? target.getName() : "不明";
            meta.setDisplayName(ChatColor.YELLOW +
                (isSelf ? "自分（" + name + "）" : name + " のCP"));
            meta.setLore(List.of(
                ChatColor.GRAY + "クリックでプレイヤーを変更",
                isSelf
                    ? ChatColor.AQUA + "現在: 自分のCPを表示中"
                    : ChatColor.AQUA + "現在: " + name + " のCPを表示中"
            ));
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createOperationWoolItem(Material wool, String displayName, String loreText) {
        ItemStack item = new ItemStack(wool);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(List.of(
                ChatColor.GRAY + loreText,
                ChatColor.YELLOW + "クリックで実行"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCheckpointPaperInfo(String name, Checkpoint checkpoint) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            meta.setLore(List.of(
                ChatColor.GRAY + "ワールド: " + checkpoint.worldName(),
                ChatColor.GRAY + String.format(Locale.ROOT,
                    "X: %.1f Y: %.1f Z: %.1f",
                    checkpoint.x(), checkpoint.y(), checkpoint.z()),
                ChatColor.GRAY + String.format(Locale.ROOT,
                    "Yaw: %.1f Pitch: %.1f",
                    checkpoint.yaw(), checkpoint.pitch())
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            paper.setItemMeta(meta);
        }
        return paper;
    }
}
