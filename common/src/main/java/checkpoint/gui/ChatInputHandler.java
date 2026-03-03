package checkpoint.gui;

import checkpoint.compat.VersionCompat;
import checkpoint.i18n.Messages;
import checkpoint.model.RenameResult;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Handles chat-based input for search, rename, and description operations.
 */
class ChatInputHandler {

    private final MenuManager mgr;

    ChatInputHandler(MenuManager mgr) {
        this.mgr = mgr;
    }

    // -----------------------------------------------------------------------
    // Description input
    // -----------------------------------------------------------------------

    boolean tryHandleDescriptionInput(UUID playerId, Player player, String message) {
        String cpName = mgr.awaitingDescriptionInput.remove(playerId);
        if (cpName == null) return false;
        Bukkit.getScheduler().runTask(mgr.plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + Messages.descCancelled(playerId));
                mgr.openCheckpointMenu(player, mgr.menuPages.getOrDefault(playerId, 0));
                return;
            }
            String desc = message.equalsIgnoreCase("clear") ? "" : message;
            boolean success = mgr.checkpointManager.setNamedCheckpointDescription(playerId, cpName, desc);
            if (success) {
                player.sendMessage(desc.isEmpty()
                    ? ChatColor.GREEN + Messages.descRemoved(playerId, cpName)
                    : ChatColor.GREEN + Messages.descSet(playerId, cpName));
                player.playSound(player.getLocation(), VersionCompat.get().soundButtonClick(), 0.6f, 1.4f);
            } else {
                player.sendMessage(ChatColor.RED + Messages.descNotFound(playerId, cpName));
            }
            mgr.openCheckpointMenu(player, mgr.menuPages.getOrDefault(playerId, 0));
        });
        return true;
    }

    // -----------------------------------------------------------------------
    // Rename input
    // -----------------------------------------------------------------------

    boolean tryHandleRenameInput(UUID playerId, Player player, String message) {
        String oldName = mgr.awaitingRenameInput.remove(playerId);
        if (oldName == null) return false;
        Bukkit.getScheduler().runTask(mgr.plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + Messages.renameCancelled(playerId));
                mgr.openCheckpointMenu(player, mgr.menuPages.getOrDefault(playerId, 0));
                return;
            }
            RenameResult result = mgr.checkpointManager.renameNamedCheckpoint(playerId, oldName, message);
            switch (result) {
                case SUCCESS: {
                    player.sendMessage(ChatColor.GREEN + Messages.renameSuccess(playerId, oldName, message));
                    mgr.openCheckpointMenu(player, mgr.menuPages.getOrDefault(playerId, 0));
                    break;
                }
                case OLD_NOT_FOUND: {
                    player.sendMessage(ChatColor.RED + Messages.renameNotFound(playerId, oldName));
                    mgr.openCheckpointMenu(player, mgr.menuPages.getOrDefault(playerId, 0));
                    break;
                }
                case NEW_ALREADY_EXISTS: {
                    player.sendMessage(ChatColor.RED + Messages.renameExists(playerId, message));
                    mgr.awaitingRenameInput.put(playerId, oldName);
                    player.sendMessage(ChatColor.GRAY + Messages.renameRetryHint(playerId));
                    break;
                }
            }
        });
        return true;
    }

    // -----------------------------------------------------------------------
    // CP search input
    // -----------------------------------------------------------------------

    boolean tryHandleSearchInput(UUID playerId, Player player, String message) {
        if (!mgr.awaitingSearchInput.remove(playerId)) return false;
        Bukkit.getScheduler().runTask(mgr.plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + Messages.searchCancelled(playerId));
                mgr.openCheckpointMenu(player, mgr.menuPages.getOrDefault(playerId, 0));
                return;
            }
            if (message.equalsIgnoreCase("clear")) {
                mgr.playerSearchQuery.remove(playerId);
                player.sendMessage(ChatColor.GREEN + Messages.searchCleared(playerId));
                mgr.menuPages.put(playerId, 0);
                mgr.openCheckpointMenu(player, 0);
                return;
            }
            mgr.playerSearchQuery.put(playerId, message);
            mgr.menuPages.put(playerId, 0);
            player.sendMessage(ChatColor.GREEN + Messages.searchSearching(playerId, message));
            mgr.openCheckpointMenu(player, 0);
        });
        return true;
    }

    // -----------------------------------------------------------------------
    // Player search input
    // -----------------------------------------------------------------------

    boolean tryHandlePlayerSearchInput(UUID playerId, Player player, String message) {
        if (!mgr.awaitingPlayerSearchInput.remove(playerId)) return false;
        Bukkit.getScheduler().runTask(mgr.plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + Messages.searchCancelled(playerId));
                mgr.openPlayerSelectMenu(player);
                return;
            }
            if (message.equalsIgnoreCase("clear")) {
                mgr.playerSelectSearchQuery.remove(playerId);
                player.sendMessage(ChatColor.GREEN + Messages.searchCleared(playerId));
                mgr.playerSelectPages.put(playerId, 0);
                mgr.openPlayerSelectMenu(player);
                return;
            }
            mgr.playerSelectSearchQuery.put(playerId, message);
            mgr.playerSelectPages.put(playerId, 0);
            player.sendMessage(ChatColor.GREEN + Messages.searchSearching(playerId, message));
            mgr.openPlayerSelectMenu(player);
        });
        return true;
    }

    // -----------------------------------------------------------------------
    // Input starters
    // -----------------------------------------------------------------------

    void startSearchInput(Player player) {
        UUID playerId = player.getUniqueId();
        player.closeInventory();
        mgr.awaitingSearchInput.add(playerId);
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━ " + Messages.searchPromptTitle(playerId) + " ━━━━━");
        player.sendMessage(ChatColor.WHITE + Messages.searchPromptMsg(playerId));
        player.sendMessage(ChatColor.GRAY + "  'cancel' " + Messages.searchCancel(playerId));
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    void startRenameInput(Player viewer, String oldName) {
        UUID viewerId = viewer.getUniqueId();
        viewer.closeInventory();
        mgr.awaitingRenameInput.put(viewerId, oldName);
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "━━━━━ " + Messages.renamePromptTitle(viewerId, oldName) + " ━━━━━");
        viewer.sendMessage(ChatColor.WHITE + Messages.renamePromptMsg(viewerId));
        viewer.sendMessage(ChatColor.GRAY + "  'cancel' " + Messages.renameCancelWord(viewerId));
        viewer.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    void startDescriptionInput(Player viewer, String cpName) {
        UUID viewerId = viewer.getUniqueId();
        viewer.closeInventory();
        mgr.awaitingDescriptionInput.put(viewerId, cpName);
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "━━━━━ " + Messages.descPromptTitle(viewerId, cpName) + " ━━━━━");
        viewer.sendMessage(ChatColor.WHITE + Messages.descPromptMsg(viewerId));
        viewer.sendMessage(ChatColor.GRAY + "  'clear' " + Messages.descClearWord(viewerId));
        viewer.sendMessage(ChatColor.GRAY + "  'cancel' " + Messages.descCancelWord(viewerId));
        viewer.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    void startPlayerSearchInput(Player player) {
        UUID playerId = player.getUniqueId();
        player.closeInventory();
        mgr.awaitingPlayerSearchInput.add(playerId);
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━ " + Messages.playerSearchTitle(playerId) + " ━━━━━");
        player.sendMessage(ChatColor.WHITE + Messages.playerSearchMsg(playerId));
        player.sendMessage(ChatColor.GRAY + "  'cancel' " + Messages.searchCancel(playerId));
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
