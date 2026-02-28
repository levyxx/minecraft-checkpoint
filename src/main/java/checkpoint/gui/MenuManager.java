package checkpoint.gui;

import checkpoint.manager.CheckpointManager;
import checkpoint.model.ClearSortOrder;
import checkpoint.model.PlayerSortOrder;
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
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import static checkpoint.gui.GuiConstants.*;

/**
 * Manages all GUI state and coordinates between handlers.
 *
 * <p>Menu rendering is delegated to {@link MenuRenderer}, click handling to
 * {@link MenuClickHandler}, chat input to {@link ChatInputHandler}, and
 * teleport / CP operations to {@link TeleportHandler}.</p>
 */
public class MenuManager {

    // ---- Internal types ---------------------------------------------------
    enum SelectionType { NAMED, QUICK }
    record LastSelection(SelectionType type, String identifier) {}

    // ---- State maps (package-private for handler access) ------------------
    final Map<UUID, Integer>        menuPages            = new ConcurrentHashMap<>();
    final Map<UUID, LastSelection>  lastSelections       = new ConcurrentHashMap<>();
    final Map<UUID, SortOrder>      playerSortOrders     = new ConcurrentHashMap<>();
    final Map<UUID, String>         playerSearchQuery    = new ConcurrentHashMap<>();
    final Set<UUID>                 awaitingSearchInput  = ConcurrentHashMap.newKeySet();
    final Map<UUID, UUID>           viewingPlayerId      = new ConcurrentHashMap<>();
    final Map<UUID, String>         pendingOperationCp   = new ConcurrentHashMap<>();
    final Map<UUID, String>         awaitingRenameInput  = new ConcurrentHashMap<>();
    final Map<UUID, String>         awaitingDescriptionInput = new ConcurrentHashMap<>();
    final Map<UUID, Integer>        playerSelectPages    = new ConcurrentHashMap<>();
    final Map<UUID, PlayerSortOrder> playerSelectSortOrders = new ConcurrentHashMap<>();
    final Map<UUID, String>         playerSelectSearchQuery = new ConcurrentHashMap<>();
    final Set<UUID>                 awaitingPlayerSearchInput = ConcurrentHashMap.newKeySet();
    final Map<UUID, Boolean>        displayWoolMode      = new ConcurrentHashMap<>();
    final Map<UUID, ClearSortOrder> clearSortOrders      = new ConcurrentHashMap<>();

    // ---- Dependencies (package-private for handler access) ----------------
    final JavaPlugin          plugin;
    final CheckpointManager   checkpointManager;
    final NamespacedKey       targetUuidKey;

    // ---- Handlers ---------------------------------------------------------
    private final MenuRenderer      renderer;
    private final MenuClickHandler  clickHandler;
    private final ChatInputHandler  chatInputHandler;
    private final TeleportHandler   teleportHandler;

    public MenuManager(JavaPlugin plugin, CheckpointManager checkpointManager) {
        this.plugin = plugin;
        this.checkpointManager = checkpointManager;
        this.targetUuidKey = new NamespacedKey(plugin, "target_uuid");
        this.renderer = new MenuRenderer(this);
        this.clickHandler = new MenuClickHandler(this);
        this.chatInputHandler = new ChatInputHandler(this);
        this.teleportHandler = new TeleportHandler(this);
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
        displayWoolMode.clear();
        clearSortOrders.clear();
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
    // Awaiting input checks
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
            selection.type() == SelectionType.NAMED && selection.identifier() != null
                && selection.identifier().equalsIgnoreCase(rawName)
                ? null
                : selection);
    }

    // -----------------------------------------------------------------------
    // Delegated to TeleportHandler
    // -----------------------------------------------------------------------

    public void handleQuickCheckpointSave(Player player)                       { teleportHandler.handleQuickCheckpointSave(player); }
    public void handleCheckpointTeleport(Player player)                        { teleportHandler.handleCheckpointTeleport(player); }
    public void executeTeleportToCp(Player v, UUID t, String n)                { teleportHandler.executeTeleportToCp(v, t, n); }
    void executeUpdateCp(Player viewer, String cpName)                         { teleportHandler.executeUpdateCp(viewer, cpName); }
    void executeDeleteCp(Player viewer, String cpName)                         { teleportHandler.executeDeleteCp(viewer, cpName); }
    void executeCloneCp(Player viewer, UUID targetId, String cpName)           { teleportHandler.executeCloneCp(viewer, targetId, cpName); }

    // -----------------------------------------------------------------------
    // Delegated to MenuRenderer
    // -----------------------------------------------------------------------

    public void openCheckpointMenu(Player viewer, int page)                    { renderer.openCheckpointMenu(viewer, page); }
    public void openCheckpointMenuFor(Player viewer, int page, UUID targetId)  { renderer.openCheckpointMenuFor(viewer, page, targetId); }
    public void openSortMenu(Player player)                                    { renderer.openSortMenu(player); }
    public void openPlayerSelectMenu(Player viewer)                            { renderer.openPlayerSelectMenu(viewer); }
    public void openPlayerSortMenu(Player player)                              { renderer.openPlayerSortMenu(player); }
    public void openClearSortMenu(Player player)                               { renderer.openClearSortMenu(player); }
    public void openCpOperationMenu(Player viewer, String cpName, UUID target) { renderer.openCpOperationMenu(viewer, cpName, target); }

    // -----------------------------------------------------------------------
    // Delegated to MenuClickHandler
    // -----------------------------------------------------------------------

    public void handleSortMenuClick(Player p, int s)                           { clickHandler.handleSortMenuClick(p, s); }
    public void handlePlayerSortMenuClick(Player p, int s)                     { clickHandler.handlePlayerSortMenuClick(p, s); }
    public void handleClearSortMenuClick(Player p, int s)                      { clickHandler.handleClearSortMenuClick(p, s); }
    public void handlePlayerSelectMenuClick(Player p, InventoryClickEvent e)   { clickHandler.handlePlayerSelectMenuClick(p, e); }
    public void handleCpOperationMenuClick(Player p, int s)                    { clickHandler.handleCpOperationMenuClick(p, s); }
    public void handleMainMenuClick(Player p, InventoryClickEvent e)           { clickHandler.handleMainMenuClick(p, e); }

    // -----------------------------------------------------------------------
    // Delegated to ChatInputHandler
    // -----------------------------------------------------------------------

    public boolean tryHandleDescriptionInput(UUID id, Player p, String m)      { return chatInputHandler.tryHandleDescriptionInput(id, p, m); }
    public boolean tryHandleRenameInput(UUID id, Player p, String m)           { return chatInputHandler.tryHandleRenameInput(id, p, m); }
    public boolean tryHandleSearchInput(UUID id, Player p, String m)           { return chatInputHandler.tryHandleSearchInput(id, p, m); }
    public boolean tryHandlePlayerSearchInput(UUID id, Player p, String m)     { return chatInputHandler.tryHandlePlayerSearchInput(id, p, m); }
    public void startSearchInput(Player player)                                { chatInputHandler.startSearchInput(player); }
    public void startRenameInput(Player viewer, String oldName)                { chatInputHandler.startRenameInput(viewer, oldName); }
    public void startDescriptionInput(Player viewer, String cpName)            { chatInputHandler.startDescriptionInput(viewer, cpName); }
    public void startPlayerSearchInput(Player player)                          { chatInputHandler.startPlayerSearchInput(player); }

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
    // Clear-sort helper
    // -----------------------------------------------------------------------

    /**
     * Returns sorted + filtered CP names, optionally grouped by clear status.
     */
    List<String> getSortedFilteredCheckpointNamesWithClearSort(
            UUID targetId, SortOrder order, String query,
            double px, double pz, ClearSortOrder csOrder) {
        List<String> base = checkpointManager.getSortedFilteredCheckpointNames(
            targetId, order, query, px, pz);
        if (csOrder == ClearSortOrder.NONE) return base;

        List<String> clearedList = new ArrayList<>();
        List<String> unclearedList = new ArrayList<>();
        for (String name : base) {
            if (checkpointManager.isCleared(targetId, name)) {
                clearedList.add(name);
            } else {
                unclearedList.add(name);
            }
        }
        List<String> result = new ArrayList<>(base.size());
        if (csOrder == ClearSortOrder.CLEARED_FIRST) {
            result.addAll(clearedList);
            result.addAll(unclearedList);
        } else {
            result.addAll(unclearedList);
            result.addAll(clearedList);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Player list helper
    // -----------------------------------------------------------------------

    List<UUID> getSortedFilteredPlayers(UUID viewerId, PlayerSortOrder order,
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
    // Internal
    // -----------------------------------------------------------------------

    void markLastSelection(UUID playerId, SelectionType type, String identifier) {
        lastSelections.put(playerId, new LastSelection(type, identifier));
    }
}
