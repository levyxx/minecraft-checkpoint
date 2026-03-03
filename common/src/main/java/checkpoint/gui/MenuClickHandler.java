package checkpoint.gui;

import checkpoint.compat.VersionCompat;
import checkpoint.i18n.Messages;
import checkpoint.model.ClearSortOrder;
import checkpoint.model.PlayerSortOrder;
import checkpoint.model.SortOrder;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import static checkpoint.gui.GuiConstants.*;

/**
 * Handles click events for all plugin GUI menus.
 */
class MenuClickHandler {

    private final MenuManager mgr;

    MenuClickHandler(MenuManager mgr) {
        this.mgr = mgr;
    }

    // -----------------------------------------------------------------------
    // Sort menu (CP)
    // -----------------------------------------------------------------------

    void handleSortMenuClick(Player player, int rawSlot) {
        SortOrder[] orders = SortOrder.values();
        int dyeBase = 10;
        for (int i = 0; i < orders.length; i++) {
            if (rawSlot == dyeBase + i) {
                mgr.playerSortOrders.put(player.getUniqueId(), orders[i]);
                mgr.menuPages.put(player.getUniqueId(), 0);
                player.playSound(player.getLocation(), VersionCompat.get().soundButtonClick(), 0.6f, 1.4f);
                mgr.openCheckpointMenu(player, 0);
                return;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Sort menu (Player)
    // -----------------------------------------------------------------------

    void handlePlayerSortMenuClick(Player player, int rawSlot) {
        PlayerSortOrder[] orders = PlayerSortOrder.values();
        int dyeBase = 10;
        for (int i = 0; i < orders.length; i++) {
            if (rawSlot == dyeBase + i) {
                mgr.playerSelectSortOrders.put(player.getUniqueId(), orders[i]);
                mgr.playerSelectPages.put(player.getUniqueId(), 0);
                player.playSound(player.getLocation(), VersionCompat.get().soundButtonClick(), 0.6f, 1.4f);
                mgr.openPlayerSelectMenu(player);
                return;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Clear sort menu
    // -----------------------------------------------------------------------

    void handleClearSortMenuClick(Player player, int rawSlot) {
        UUID viewerId = player.getUniqueId();
        if (rawSlot == 12) {
            mgr.clearSortOrders.put(viewerId, ClearSortOrder.CLEARED_FIRST);
        } else if (rawSlot == 14) {
            mgr.clearSortOrders.put(viewerId, ClearSortOrder.UNCLEARED_FIRST);
        } else {
            return;
        }
        mgr.menuPages.put(viewerId, 0);
        player.playSound(player.getLocation(), VersionCompat.get().soundButtonClick(), 0.6f, 1.4f);
        mgr.openCheckpointMenu(player, 0);
    }

    // -----------------------------------------------------------------------
    // Player select menu
    // -----------------------------------------------------------------------

    void handlePlayerSelectMenuClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        UUID viewerId = player.getUniqueId();
        int psPage = mgr.playerSelectPages.getOrDefault(viewerId, 0);
        VersionCompat compat = VersionCompat.get();

        PlayerSortOrder psOrder = mgr.playerSelectSortOrders.getOrDefault(viewerId, PlayerSortOrder.NAME_ASC);
        String psQuery = mgr.playerSelectSearchQuery.get(viewerId);
        double px = player.getLocation().getX();
        double pz = player.getLocation().getZ();
        List<UUID> playerList = mgr.getSortedFilteredPlayers(viewerId, psOrder, psQuery, px, pz);
        int totalPages = Math.max(1, (int) Math.ceil(playerList.size() / (double) ITEMS_PER_PAGE));

        if (rawSlot == SLOT_PREVIOUS && event.isLeftClick() && psPage > 0) {
            mgr.playerSelectPages.put(viewerId, psPage - 1);
            player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 0.9f);
            mgr.openPlayerSelectMenu(player);
            return;
        }
        if (rawSlot == SLOT_NEXT && event.isLeftClick() && psPage < totalPages - 1) {
            mgr.playerSelectPages.put(viewerId, psPage + 1);
            player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.2f);
            mgr.openPlayerSelectMenu(player);
            return;
        }
        if (rawSlot == SLOT_SORT && event.isLeftClick()) {
            mgr.openPlayerSortMenu(player);
            return;
        }
        if (rawSlot == SLOT_SEARCH && event.isLeftClick()) {
            mgr.startPlayerSearchInput(player);
            return;
        }
        if (rawSlot == SLOT_SEARCH && event.isRightClick()) {
            mgr.playerSelectSearchQuery.remove(viewerId);
            mgr.playerSelectPages.put(viewerId, 0);
            player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
            mgr.openPlayerSelectMenu(player);
            return;
        }

        int psRow = rawSlot / 9;
        int psCol = rawSlot % 9;
        if (psRow >= 1 && psRow <= 4 && psCol >= 1 && psCol <= 7) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !compat.isPlayerHead(clicked.getType())) return;
            ItemMeta clickedMeta = clicked.getItemMeta();
            if (clickedMeta == null) return;
            String uuidStr = compat.getTargetUuid(clickedMeta);
            if (uuidStr == null) return;
            UUID targetId = UUID.fromString(uuidStr);
            mgr.viewingPlayerId.put(viewerId, targetId);
            mgr.menuPages.put(viewerId, 0);
            mgr.playerSearchQuery.remove(viewerId);
            player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
            mgr.openCheckpointMenuFor(player, 0, targetId);
        }
    }

    // -----------------------------------------------------------------------
    // CP operation menu
    // -----------------------------------------------------------------------

    void handleCpOperationMenuClick(Player player, int rawSlot) {
        UUID viewerId = player.getUniqueId();
        String cpName = mgr.pendingOperationCp.get(viewerId);
        if (cpName == null) return;
        UUID targetId = mgr.viewingPlayerId.getOrDefault(viewerId, viewerId);
        boolean isSelf = targetId.equals(viewerId);
        Bukkit.getScheduler().runTask(mgr.plugin, () -> {
            if (isSelf) {
                if (rawSlot == 9)       mgr.executeTeleportToCp(player, targetId, cpName);
                else if (rawSlot == 11) mgr.executeUpdateCp(player, cpName);
                else if (rawSlot == 13) mgr.startRenameInput(player, cpName);
                else if (rawSlot == 15) mgr.startDescriptionInput(player, cpName);
                else if (rawSlot == 17) mgr.executeDeleteCp(player, cpName);
            } else {
                if (rawSlot == 12)      mgr.executeTeleportToCp(player, targetId, cpName);
                else if (rawSlot == 14) mgr.executeCloneCp(player, targetId, cpName);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Main checkpoint menu
    // -----------------------------------------------------------------------

    void handleMainMenuClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= GUI_SIZE) return;

        UUID playerId = player.getUniqueId();
        UUID targetId = mgr.viewingPlayerId.getOrDefault(playerId, playerId);
        boolean isSelf = targetId.equals(playerId);
        VersionCompat compat = VersionCompat.get();
        SortOrder order = mgr.playerSortOrders.getOrDefault(playerId, SortOrder.NAME_ASC);
        String query = mgr.playerSearchQuery.get(playerId);
        double px = player.getLocation().getX();
        double pz = player.getLocation().getZ();
        ClearSortOrder csOrder = mgr.clearSortOrders.getOrDefault(playerId, ClearSortOrder.NONE);
        List<String> names = mgr.getSortedFilteredCheckpointNamesWithClearSort(
            targetId, order, query, px, pz, csOrder);
        int page = mgr.menuPages.getOrDefault(playerId, 0);
        int totalPages = Math.max(1, (int) Math.ceil(Math.max(1, names.size()) / (double) ITEMS_PER_PAGE));

        // Player head: open player selector
        if (rawSlot == SLOT_PLAYER_HEAD) {
            mgr.openPlayerSelectMenu(player);
            return;
        }

        // Display mode toggle (slot 2)
        if (rawSlot == SLOT_DISPLAY_MODE) {
            boolean current = mgr.displayWoolMode.getOrDefault(playerId, false);
            mgr.displayWoolMode.put(playerId, !current);
            player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
            mgr.openCheckpointMenu(player, page);
            return;
        }

        // Clear sort button (slot 6): left-click opens menu, right-click clears
        if (rawSlot == SLOT_CLEAR_SORT) {
            if (event.isRightClick()) {
                mgr.clearSortOrders.remove(playerId);
                mgr.menuPages.put(playerId, 0);
                player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
                mgr.openCheckpointMenu(player, 0);
            } else {
                mgr.openClearSortMenu(player);
            }
            return;
        }

        // CP item click (rows 1-4, cols 1-7)
        int row = rawSlot / 9;
        int col = rawSlot % 9;
        if (row >= 1 && row <= 4 && col >= 1 && col <= 7) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            Material mat = clicked.getType();
            if (mat != Material.PAPER && !compat.isWoolCpItem(mat)) return;
            int itemIndex = (row - 1) * 7 + (col - 1);
            int dataIndex = page * ITEMS_PER_PAGE + itemIndex;
            if (dataIndex >= names.size()) return;
            String name = names.get(dataIndex);
            if (event.isRightClick()) {
                mgr.openCpOperationMenu(player, name, targetId);
                return;
            }
            if (!isSelf) {
                mgr.executeTeleportToCp(player, targetId, name);
                return;
            }
            if (mgr.checkpointManager.selectNamedCheckpoint(playerId, name)) {
                mgr.checkpointManager.getSelectedNamedCheckpointName(playerId)
                    .ifPresent(actualName -> mgr.markLastSelection(playerId, MenuManager.SelectionType.NAMED, actualName));
                player.sendMessage(ChatColor.AQUA + Messages.cpSelected(playerId, name));
                player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
                player.closeInventory();
            }
            return;
        }

        // Bottom row controls
        if (rawSlot == SLOT_PREVIOUS && event.isLeftClick() && page > 0) {
            mgr.openCheckpointMenu(player, page - 1);
            player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 0.9f);
            return;
        }
        if (rawSlot == SLOT_SORT && event.isLeftClick()) {
            mgr.openSortMenu(player);
            return;
        }
        if (rawSlot == SLOT_SEARCH && event.isLeftClick()) {
            mgr.startSearchInput(player);
            return;
        }
        if (rawSlot == SLOT_SEARCH && event.isRightClick()) {
            mgr.playerSearchQuery.remove(player.getUniqueId());
            mgr.menuPages.put(player.getUniqueId(), 0);
            player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
            mgr.openCheckpointMenu(player, 0);
            return;
        }
        if (rawSlot == SLOT_NEXT && event.isLeftClick() && page < totalPages - 1) {
            mgr.openCheckpointMenu(player, page + 1);
            player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.2f);
        }
    }
}
