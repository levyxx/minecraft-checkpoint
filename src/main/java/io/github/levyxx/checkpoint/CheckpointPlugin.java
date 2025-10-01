package io.github.levyxx.checkpoint;

import io.github.levyxx.checkpoint.CheckpointManager.Checkpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class CheckpointPlugin extends JavaPlugin implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;
    private static final String GUI_TITLE = ChatColor.DARK_AQUA + "チェックポイント一覧";
    private static final int TELEPORT_MAX_ATTEMPTS = 5;
    private static final long TELEPORT_RETRY_DELAY_TICKS = 1L;

    private CheckpointManager checkpointManager;
    private final Map<UUID, Integer> menuPages = new ConcurrentHashMap<>();
    private final Map<UUID, LastSelection> lastSelections = new ConcurrentHashMap<>();

    private enum SelectionType {
        NAMED,
        QUICK
    }

    private record LastSelection(SelectionType type, String identifier) {}

    @Override
    public void onEnable() {
        this.checkpointManager = new CheckpointManager();
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
        this.checkpointManager = null;
        getLogger().info("Checkpoint plugin disabled.");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            item = event.getPlayer().getInventory().getItem(event.getHand());
        }
        if (item == null || item.getType().isAir()) {
            return;
        }

        Material type = item.getType();
        Player player = event.getPlayer();

        if (type == Material.SLIME_BALL || type == Material.SLIME_BLOCK) {
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

    private void openCheckpointMenu(Player player, int requestedPage) {
        UUID playerId = player.getUniqueId();
        List<String> names = checkpointManager.getNamedCheckpointNames(playerId);
        int totalPages = Math.max(1, (int) Math.ceil(Math.max(1, names.size()) / (double) ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        menuPages.put(playerId, page);

        Inventory inventory = Bukkit.createInventory(player, GUI_SIZE, GUI_TITLE);
        Optional<String> selectedName = checkpointManager.getSelectedNamedCheckpointName(playerId);

        int startIndex = page * ITEMS_PER_PAGE;
        for (int slot = 0; slot < ITEMS_PER_PAGE; slot++) {
            int index = startIndex + slot;
            if (index >= names.size()) {
                continue;
            }
            String name = names.get(index);
            Optional<Checkpoint> checkpoint = checkpointManager.getNamedCheckpoint(playerId, name);
            if (checkpoint.isEmpty()) {
                continue;
            }
            boolean selected = selectedName.map(existing -> existing.equalsIgnoreCase(name)).orElse(false);
            inventory.setItem(slot, createCheckpointPaper(name, checkpoint.get(), selected));
        }

        for (int slot = ITEMS_PER_PAGE; slot < GUI_SIZE; slot++) {
            if (slot == SLOT_PREVIOUS || slot == SLOT_INFO || slot == SLOT_NEXT) {
                continue;
            }
            inventory.setItem(slot, createFillerItem());
        }

        if (names.isEmpty()) {
            inventory.setItem(22, createEmptyNoticeItem());
        }

        if (totalPages > 1 && page > 0) {
            inventory.setItem(SLOT_PREVIOUS, createNavItem(false, page, totalPages));
        } else {
            inventory.setItem(SLOT_PREVIOUS, createDisabledNavItem(ChatColor.DARK_GRAY + "前のページなし"));
        }

        inventory.setItem(SLOT_INFO, createInfoItem(page + 1, totalPages, names.size()));

        if (totalPages > 1 && page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, createNavItem(true, page, totalPages));
        } else {
            inventory.setItem(SLOT_NEXT, createDisabledNavItem(ChatColor.DARK_GRAY + "次のページなし"));
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!GUI_TITLE.equals(event.getView().getTitle())) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }

        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= GUI_SIZE) {
            return;
        }

        UUID playerId = player.getUniqueId();
        List<String> names = checkpointManager.getNamedCheckpointNames(playerId);
        int page = menuPages.getOrDefault(playerId, 0);
        int totalPages = Math.max(1, (int) Math.ceil(Math.max(1, names.size()) / (double) ITEMS_PER_PAGE));

        if (rawSlot < ITEMS_PER_PAGE) {
            if (!event.isLeftClick()) {
                return;
            }
            int index = page * ITEMS_PER_PAGE + rawSlot;
            if (index >= names.size()) {
                return;
            }
            String name = names.get(index);
            if (checkpointManager.selectNamedCheckpoint(playerId, name)) {
                checkpointManager.getSelectedNamedCheckpointName(playerId)
                    .ifPresent(actualName -> markLastSelection(playerId, SelectionType.NAMED, actualName));
                player.sendMessage(ChatColor.AQUA + "チェックポイント『" + name + "』を選択しました。");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                player.closeInventory();
            }
            return;
        }

        if (rawSlot == SLOT_PREVIOUS && event.isLeftClick() && page > 0) {
            openCheckpointMenu(player, page - 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.9f);
            return;
        }

        if (rawSlot == SLOT_NEXT && event.isLeftClick() && page < totalPages - 1) {
            openCheckpointMenu(player, page + 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!GUI_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!GUI_TITLE.equals(player.getOpenInventory().getTitle())) {
                menuPages.remove(player.getUniqueId());
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

    private ItemStack createCheckpointPaper(String name, Checkpoint checkpoint, boolean selected) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ワールド: " + checkpoint.worldName());
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "X: %.1f Y: %.1f Z: %.1f", checkpoint.x(), checkpoint.y(), checkpoint.z()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "Yaw: %.1f Pitch: %.1f", checkpoint.yaw(), checkpoint.pitch()));
            lore.add("");
            lore.add(selected ? ChatColor.AQUA + "現在選択中" : ChatColor.YELLOW + "左クリックで選択");
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
            List<String> lore = new ArrayList<>();
            int targetPage = forward ? currentPage + 2 : currentPage;
            lore.add(ChatColor.GRAY + "ページ: " + targetPage + " / " + totalPages);
            lore.add(ChatColor.YELLOW + "左クリックで移動");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createDisabledNavItem(String label) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(label);
            meta.setLore(List.of(ChatColor.DARK_GRAY + "ページ移動はできません"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(int currentPage, int totalPages, int totalCheckpoints) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "ページ情報");
            meta.setLore(List.of(
                ChatColor.GRAY + "ページ: " + currentPage + " / " + totalPages,
                ChatColor.GRAY + "登録数: " + totalCheckpoints,
                "",
                ChatColor.YELLOW + "紙を左クリックで選択"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
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
}
