package checkpoint.gui;

import checkpoint.compat.VersionCompat;
import checkpoint.i18n.Messages;
import checkpoint.model.Checkpoint;
import checkpoint.model.ClearSortOrder;
import checkpoint.model.PlayerSortOrder;
import checkpoint.model.SortOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import static checkpoint.gui.GuiConstants.*;

/**
 * Responsible for building and opening all plugin GUI menus.
 */
class MenuRenderer {

    private final MenuManager mgr;

    MenuRenderer(MenuManager mgr) {
        this.mgr = mgr;
    }

    // -----------------------------------------------------------------------
    // Checkpoint list menu
    // -----------------------------------------------------------------------

    void openCheckpointMenu(Player viewer, int requestedPage) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = mgr.viewingPlayerId.getOrDefault(viewerId, viewerId);
        openCheckpointMenuFor(viewer, requestedPage, targetId);
    }

    void openCheckpointMenuFor(Player viewer, int requestedPage, UUID targetId) {
        UUID viewerId = viewer.getUniqueId();
        mgr.viewingPlayerId.put(viewerId, targetId);
        boolean isSelf = targetId.equals(viewerId);
        VersionCompat compat = VersionCompat.get();

        SortOrder order = mgr.playerSortOrders.getOrDefault(viewerId, SortOrder.NAME_ASC);
        String query = mgr.playerSearchQuery.get(viewerId);
        double px = viewer.getLocation().getX();
        double pz = viewer.getLocation().getZ();

        ClearSortOrder csOrder = mgr.clearSortOrders.getOrDefault(viewerId, ClearSortOrder.NONE);
        List<String> names = mgr.getSortedFilteredCheckpointNamesWithClearSort(
            targetId, order, query, px, pz, csOrder);

        int totalPages = Math.max(1, (int) Math.ceil(Math.max(1, names.size()) / (double) ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        mgr.menuPages.put(viewerId, page);

        Inventory inventory = Bukkit.createInventory(viewer, GUI_SIZE,
            ChatColor.DARK_AQUA + Messages.guiTitle(viewerId));

        // Border decoration
        ItemStack cyanGlass = compat.cyanGlassPane();
        ItemStack lightBlueGlass = compat.lightBlueGlassPane();
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                inventory.setItem(slot, ItemFactory.createGlassDeco(
                    (row + col) % 2 == 0 ? cyanGlass : lightBlueGlass));
            }
        }

        // Player head at top-row middle
        inventory.setItem(SLOT_PLAYER_HEAD, PlayerItemFactory.createPlayerHeadItem(viewerId, Bukkit.getOfflinePlayer(targetId), isSelf));

        // Display mode toggle (slot 2) and Clear sort button (slot 6)
        boolean woolMode = mgr.displayWoolMode.getOrDefault(viewerId, false);
        inventory.setItem(SLOT_DISPLAY_MODE, ItemFactory.createDisplayModeToggle(viewerId, woolMode));
        inventory.setItem(SLOT_CLEAR_SORT, ItemFactory.createClearSortButton(viewerId, csOrder));

        // CP items in inner area (rows 1-4, cols 1-7)
        Optional<String> selectedName = isSelf
            ? mgr.checkpointManager.getSelectedNamedCheckpointName(viewerId)
            : Optional.empty();
        int startIndex = page * ITEMS_PER_PAGE;
        int itemIndex = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                int slot = row * 9 + col;
                int dataIndex = startIndex + itemIndex;
                if (dataIndex < names.size()) {
                    String name = names.get(dataIndex);
                    Optional<Checkpoint> checkpoint = mgr.checkpointManager.getNamedCheckpoint(targetId, name);
                    if (checkpoint.isPresent()) {
                        boolean selected = selectedName.map(s -> s.equalsIgnoreCase(name)).orElse(false);
                        boolean cleared = mgr.checkpointManager.isCleared(targetId, name);
                        if (woolMode) {
                            inventory.setItem(slot, ItemFactory.createCheckpointWool(
                                viewerId, name, checkpoint.get(), selected, isSelf, cleared));
                        } else {
                            inventory.setItem(slot, ItemFactory.createCheckpointPaper(
                                viewerId, name, checkpoint.get(), selected, isSelf, cleared));
                        }
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
    }

    // -----------------------------------------------------------------------
    // Sort menu (CP)
    // -----------------------------------------------------------------------

    void openSortMenu(Player player) {
        UUID viewerId = player.getUniqueId();
        VersionCompat compat = VersionCompat.get();
        Inventory inv = Bukkit.createInventory(player, 27,
            ChatColor.DARK_AQUA + Messages.sortTitle(viewerId));
        SortOrder current = mgr.playerSortOrders.getOrDefault(viewerId, SortOrder.NAME_ASC);
        SortOrder[] orders = SortOrder.values();

        ItemStack cyanGlass = compat.cyanGlassPane();
        ItemStack lightBlueGlass = compat.lightBlueGlassPane();
        for (int s = 0; s < 9; s++) inv.setItem(s, ItemFactory.createGlassDeco(cyanGlass));
        for (int s = 18; s < 27; s++) inv.setItem(s, ItemFactory.createGlassDeco(cyanGlass));
        for (int s = 9; s < 18; s++) inv.setItem(s, ItemFactory.createGlassDeco(lightBlueGlass));

        int dyeBase = 10;
        for (int i = 0; i < orders.length; i++) {
            inv.setItem(dyeBase + i, ItemFactory.createSortDyeItem(
                viewerId, compat.sortDyeBase(i % 7), orders[i], orders[i] == current));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
    }

    // -----------------------------------------------------------------------
    // Player select menu
    // -----------------------------------------------------------------------

    void openPlayerSelectMenu(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        VersionCompat compat = VersionCompat.get();
        PlayerSortOrder psOrder = mgr.playerSelectSortOrders.getOrDefault(viewerId, PlayerSortOrder.NAME_ASC);
        String psQuery = mgr.playerSelectSearchQuery.get(viewerId);
        double px = viewer.getLocation().getX();
        double pz = viewer.getLocation().getZ();

        List<UUID> playerList = mgr.getSortedFilteredPlayers(viewerId, psOrder, psQuery, px, pz);

        int psPage = mgr.playerSelectPages.getOrDefault(viewerId, 0);
        int totalItems = playerList.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE));
        psPage = Math.max(0, Math.min(psPage, totalPages - 1));
        mgr.playerSelectPages.put(viewerId, psPage);

        Inventory inv = Bukkit.createInventory(viewer, GUI_SIZE,
            ChatColor.DARK_AQUA + Messages.playerSelectTitle(viewerId));

        // Glass border (same pattern as CP list)
        ItemStack cyanGlass = compat.cyanGlassPane();
        ItemStack lightBlueGlass = compat.lightBlueGlassPane();
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                inv.setItem(slot, ItemFactory.createGlassDeco(
                    (row + col) % 2 == 0 ? cyanGlass : lightBlueGlass));
            }
        }

        UUID currentTarget = mgr.viewingPlayerId.getOrDefault(viewerId, viewerId);
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
                    int cpCount = mgr.checkpointManager.getNamedCheckpointNames(targetId).size();
                    String lastCloneStr = mgr.checkpointManager.getCloneTime(viewerId, targetId)
                        .map(ItemFactory::formatInstant).orElse(null);
                    int totalClonedCount = mgr.checkpointManager.getClonedCount(targetId);
                    double nearestDist = Math.sqrt(mgr.checkpointManager.getNearestCpDistanceSq(targetId, px, pz));
                    String lastActivityStr = mgr.checkpointManager.getLastActivityTime(targetId)
                        .map(ItemFactory::formatInstant).orElse(null);

                    inv.setItem(slot, PlayerItemFactory.createPlayerSelectHead(
                        viewerId, target, isSelf, isViewing, cpCount, lastCloneStr,
                        totalClonedCount, nearestDist, lastActivityStr));
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
        inv.setItem(SLOT_SEARCH, PlayerItemFactory.createPlayerSearchItem(viewerId));
        inv.setItem(SLOT_INFO, PlayerItemFactory.createPlayerInfoItem(viewerId, psPage + 1, totalPages, totalItems, psOrder, psQuery));
        inv.setItem(SLOT_SORT, PlayerItemFactory.createPlayerSortButtonItem(viewerId, psOrder));
        if (totalPages > 1 && psPage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, ItemFactory.createNavItem(viewerId, true, psPage, totalPages));
        } else {
            inv.setItem(SLOT_NEXT, ItemFactory.createDisabledNavItem(viewerId, true));
        }

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
    }

    // -----------------------------------------------------------------------
    // Player sort menu
    // -----------------------------------------------------------------------

    void openPlayerSortMenu(Player player) {
        UUID viewerId = player.getUniqueId();
        VersionCompat compat = VersionCompat.get();
        Inventory inv = Bukkit.createInventory(player, 27,
            ChatColor.DARK_AQUA + Messages.playerSortTitle(viewerId));
        PlayerSortOrder current = mgr.playerSelectSortOrders.getOrDefault(viewerId, PlayerSortOrder.NAME_ASC);
        PlayerSortOrder[] orders = PlayerSortOrder.values();

        ItemStack cyanGlass = compat.cyanGlassPane();
        ItemStack lightBlueGlass = compat.lightBlueGlassPane();
        for (int s = 0; s < 9; s++) inv.setItem(s, ItemFactory.createGlassDeco(cyanGlass));
        for (int s = 18; s < 27; s++) inv.setItem(s, ItemFactory.createGlassDeco(cyanGlass));
        for (int s = 9; s < 18; s++) inv.setItem(s, ItemFactory.createGlassDeco(lightBlueGlass));

        int dyeBase = 10;
        for (int i = 0; i < orders.length; i++) {
            inv.setItem(dyeBase + i, ItemFactory.createSortDyeItem(
                viewerId, compat.sortDyeBase(i % 7),
                Messages.playerSortOrderLabel(viewerId, orders[i]), orders[i] == current));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
    }

    // -----------------------------------------------------------------------
    // Clear sort menu
    // -----------------------------------------------------------------------

    void openClearSortMenu(Player player) {
        UUID viewerId = player.getUniqueId();
        VersionCompat compat = VersionCompat.get();
        Inventory inv = Bukkit.createInventory(player, 27,
            ChatColor.DARK_AQUA + Messages.clearSortTitle(viewerId));
        ClearSortOrder current = mgr.clearSortOrders.getOrDefault(viewerId, ClearSortOrder.NONE);

        ItemStack cyanGlass = compat.cyanGlassPane();
        ItemStack lightBlueGlass = compat.lightBlueGlassPane();
        for (int s = 0; s < 9; s++) inv.setItem(s, ItemFactory.createGlassDeco(cyanGlass));
        for (int s = 18; s < 27; s++) inv.setItem(s, ItemFactory.createGlassDeco(cyanGlass));
        for (int s = 9; s < 18; s++) inv.setItem(s, ItemFactory.createGlassDeco(lightBlueGlass));

        inv.setItem(12, ItemFactory.createClearSortOption(viewerId, ClearSortOrder.CLEARED_FIRST, current == ClearSortOrder.CLEARED_FIRST));
        inv.setItem(14, ItemFactory.createClearSortOption(viewerId, ClearSortOrder.UNCLEARED_FIRST, current == ClearSortOrder.UNCLEARED_FIRST));

        player.openInventory(inv);
        player.playSound(player.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
    }

    // -----------------------------------------------------------------------
    // CP operation menu
    // -----------------------------------------------------------------------

    void openCpOperationMenu(Player viewer, String cpName, UUID targetId) {
        UUID viewerId = viewer.getUniqueId();
        VersionCompat compat = VersionCompat.get();
        boolean isSelf = targetId.equals(viewerId);
        mgr.pendingOperationCp.put(viewerId, cpName);
        Inventory inv = Bukkit.createInventory(viewer, 27,
            ChatColor.DARK_AQUA + Messages.cpOperationTitle(viewerId));

        ItemStack cyanGlass = compat.cyanGlassPane();
        ItemStack lightBlueGlass = compat.lightBlueGlassPane();
        for (int s = 0; s < 9; s++) inv.setItem(s, ItemFactory.createGlassDeco(cyanGlass));
        for (int s = 18; s < 27; s++) inv.setItem(s, ItemFactory.createGlassDeco(cyanGlass));
        for (int s = 9; s < 18; s++) inv.setItem(s, ItemFactory.createGlassDeco(lightBlueGlass));

        mgr.checkpointManager.getNamedCheckpoint(targetId, cpName)
            .ifPresent(cp -> inv.setItem(4, ItemFactory.createCheckpointPaperInfo(viewerId, cpName, cp)));

        if (isSelf) {
            inv.setItem(9,  PlayerItemFactory.createOperationWoolItem(viewerId, compat.greenWool(),
                ChatColor.GREEN + Messages.opTeleport(viewerId), Messages.opTeleportLore(viewerId)));
            inv.setItem(11, PlayerItemFactory.createOperationWoolItem(viewerId, compat.blueWool(),
                ChatColor.AQUA + Messages.opUpdate(viewerId), Messages.opUpdateLore(viewerId)));
            inv.setItem(13, PlayerItemFactory.createOperationWoolItem(viewerId, compat.yellowWool(),
                ChatColor.YELLOW + Messages.opRename(viewerId), Messages.opRenameLore(viewerId)));
            inv.setItem(15, PlayerItemFactory.createOperationWoolItem(viewerId, compat.orangeWool(),
                ChatColor.GOLD + Messages.opDescChange(viewerId), Messages.opDescChangeLore(viewerId)));
            inv.setItem(17, PlayerItemFactory.createOperationWoolItem(viewerId, compat.redWool(),
                ChatColor.RED + Messages.opDelete(viewerId), Messages.opDeleteLore(viewerId)));
        } else {
            OfflinePlayer tp = Bukkit.getOfflinePlayer(targetId);
            String tName = tp.getName() != null ? tp.getName() : Messages.psUnknown(viewerId);
            inv.setItem(12, PlayerItemFactory.createOperationWoolItem(viewerId, compat.greenWool(),
                ChatColor.GREEN + Messages.opTeleport(viewerId), Messages.opTeleportLore(viewerId)));
            inv.setItem(14, PlayerItemFactory.createOperationWoolItem(viewerId, compat.cyanWool(),
                ChatColor.AQUA + Messages.opClone(viewerId), Messages.opCloneLore(viewerId, tName)));
        }

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), compat.soundButtonClick(), 0.6f, 1.4f);
    }
}
