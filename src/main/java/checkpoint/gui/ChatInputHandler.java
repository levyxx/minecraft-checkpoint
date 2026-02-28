package checkpoint.gui;

import checkpoint.i18n.Messages;
import checkpoint.model.RenameResult;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Handles chat-based input for search, rename, and description operations.
 * Extracted from {@link MenuManager} for readability.
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
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
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
                case SUCCESS -> {
                    player.sendMessage(ChatColor.GREEN + Messages.renameSuccess(playerId, oldName, message));
                    mgr.openCheckpointMenu(player, mgr.menuPages.getOrDefault(playerId, 0));
                }
                case OLD_NOT_FOUND -> {
                    player.sendMessage(ChatColor.RED + Messages.renameNotFound(playerId, oldName));
                    mgr.openCheckpointMenu(player, mgr.menuPages.getOrDefault(playerId, 0));
                }
                case NEW_ALREADY_EXISTS -> {
                    player.sendMessage(ChatColor.RED + Messages.renameExists(playerId, message));
                    mgr.awaitingRenameInput.put(playerId, oldName);
                    player.sendMessage(ChatColor.GRAY + Messages.renameRetryHint(playerId));
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
            player.sendMessage(ChatColor.GREEN + Messages.searchSearching(playerId, message));
            mgr.menuPages.put(playerId, 0);
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
            player.sendMessage(ChatColor.GREEN + Messages.searchSearching(playerId, message));
            mgr.playerSelectPages.put(playerId, 0);
            mgr.openPlayerSelectMenu(player);
        });
        return true;
    }

    // -----------------------------------------------------------------------
    // Start input prompts
    // -----------------------------------------------------------------------

    void startSearchInput(Player player) {
        UUID playerId = player.getUniqueId();
        mgr.awaitingSearchInput.add(playerId);
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

    void startRenameInput(Player viewer, String oldName) {
        UUID playerId = viewer.getUniqueId();
        mgr.awaitingRenameInput.put(playerId, oldName);
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

    void startDescriptionInput(Player viewer, String cpName) {
        UUID playerId = viewer.getUniqueId();
        mgr.awaitingDescriptionInput.put(playerId, cpName);
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

    void startPlayerSearchInput(Player player) {
        UUID playerId = player.getUniqueId();
        mgr.awaitingPlayerSearchInput.add(playerId);
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
}
