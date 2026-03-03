package checkpoint.command;

import checkpoint.CheckpointPluginBase;
import checkpoint.i18n.Messages;
import checkpoint.i18n.Messages.Lang;
import checkpoint.manager.CheckpointManager;
import checkpoint.model.Checkpoint;
import checkpoint.model.RenameResult;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Implements individual subcommand handlers for /cp.
 * Extracted from {@link CheckpointCommand} for readability.
 */
class SubcommandHandlers {

    private final CheckpointPluginBase plugin;
    private final CheckpointManager checkpointManager;

    SubcommandHandlers(CheckpointPluginBase plugin, CheckpointManager checkpointManager) {
        this.plugin = plugin;
        this.checkpointManager = checkpointManager;
    }

    // -----------------------------------------------------------------------
    // /cp set
    // -----------------------------------------------------------------------

    void handleSet(Player player, UUID playerId, String name, String description) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + Messages.cmdWorldError(playerId));
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

        boolean success;
        try {
            success = checkpointManager.addNamedCheckpoint(playerId, name, checkpoint);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
            return;
        }

        if (success) {
            if (!description.isEmpty()) {
                checkpointManager.setNamedCheckpointDescription(playerId, name, description);
            }
            player.sendMessage(ChatColor.GREEN + Messages.cmdSetSuccess(playerId, name));
            plugin.notifyNamedCheckpointSet(player, name);
        } else {
            player.sendMessage(ChatColor.RED + Messages.cmdSetDuplicate(playerId, name));
        }
    }

    // -----------------------------------------------------------------------
    // /cp description
    // -----------------------------------------------------------------------

    void handleDescription(Player player, UUID playerId, String name, String description) {
        boolean success;
        try {
            success = checkpointManager.setNamedCheckpointDescription(playerId, name, description);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
            return;
        }
        if (success) {
            player.sendMessage(ChatColor.GREEN + Messages.cmdDescSuccess(playerId, name));
        } else {
            player.sendMessage(ChatColor.RED + Messages.cmdDescNotFound(playerId, name));
        }
    }

    // -----------------------------------------------------------------------
    // /cp update
    // -----------------------------------------------------------------------

    void handleUpdate(Player player, UUID playerId, String name) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + Messages.cmdWorldError(playerId));
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

        boolean updated;
        try {
            updated = checkpointManager.updateNamedCheckpoint(playerId, name, checkpoint);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
            return;
        }

        if (updated) {
            player.sendMessage(ChatColor.GREEN + Messages.cmdUpdateSuccess(playerId, name));
            plugin.notifyNamedCheckpointSet(player, name);
        } else {
            player.sendMessage(ChatColor.RED + Messages.cmdUpdateNotFound(playerId, name));
        }
    }

    // -----------------------------------------------------------------------
    // /cp delete
    // -----------------------------------------------------------------------

    void handleDelete(Player player, UUID playerId, String name) {
        boolean removed;
        try {
            removed = checkpointManager.removeNamedCheckpoint(playerId, name);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
            return;
        }

        if (removed) {
            player.sendMessage(ChatColor.GREEN + Messages.cmdDeleteSuccess(playerId, name));
            plugin.notifyNamedCheckpointDeleted(playerId, name);
        } else {
            player.sendMessage(ChatColor.RED + Messages.cmdDeleteNotFound(playerId, name));
        }
    }

    // -----------------------------------------------------------------------
    // /cp rename
    // -----------------------------------------------------------------------

    void handleRename(Player player, UUID playerId, String oldName, String newName) {
        RenameResult result;
        try {
            result = checkpointManager.renameNamedCheckpoint(playerId, oldName, newName);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
            return;
        }

        switch (result) {
            case SUCCESS:
                player.sendMessage(ChatColor.GREEN
                    + Messages.cmdRenameSuccess(playerId, oldName, newName));
                break;
            case OLD_NOT_FOUND:
                player.sendMessage(ChatColor.RED
                    + Messages.cmdRenameOldNotFound(playerId, oldName));
                break;
            case NEW_ALREADY_EXISTS:
                player.sendMessage(ChatColor.RED
                    + Messages.cmdRenameNewExists(playerId, newName));
                break;
        }
    }

    // -----------------------------------------------------------------------
    // /cp did
    // -----------------------------------------------------------------------

    void handleDid(Player player, UUID playerId) {
        Optional<String> selected = checkpointManager.getSelectedNamedCheckpointName(playerId);
        if (!selected.isPresent()) {
            player.sendMessage(ChatColor.RED + Messages.cmdDidNeedNamed(playerId));
            return;
        }
        String name = selected.get();
        checkpointManager.markCleared(playerId, name);
        player.sendMessage(ChatColor.GREEN + Messages.cmdDidSuccess(playerId, name));
    }

    // -----------------------------------------------------------------------
    // /cp didnt
    // -----------------------------------------------------------------------

    void handleDidnt(Player player, UUID playerId) {
        Optional<String> selected = checkpointManager.getSelectedNamedCheckpointName(playerId);
        if (!selected.isPresent()) {
            player.sendMessage(ChatColor.RED + Messages.cmdDidNeedNamed(playerId));
            return;
        }
        String name = selected.get();
        boolean removed = checkpointManager.unmarkCleared(playerId, name);
        if (removed) {
            player.sendMessage(ChatColor.GREEN + Messages.cmdDidntSuccess(playerId, name));
        } else {
            player.sendMessage(ChatColor.YELLOW + Messages.cmdDidntNotCleared(playerId, name));
        }
    }

    // -----------------------------------------------------------------------
    // /cp language
    // -----------------------------------------------------------------------

    void handleLanguage(Player player, UUID playerId, String langArg) {
        String lower = langArg.toLowerCase(Locale.ROOT);
        if ("ja".equals(lower)) {
            plugin.setPlayerLanguageManual(playerId, Lang.JP);
        } else if ("en".equals(lower)) {
            plugin.setPlayerLanguageManual(playerId, Lang.EN);
        } else {
            player.sendMessage(ChatColor.RED + Messages.cmdUsageLanguage(playerId, "cp"));
            return;
        }
        player.sendMessage(ChatColor.GREEN + Messages.cmdLangChanged(playerId));
    }

    // -----------------------------------------------------------------------
    // /cp help
    // -----------------------------------------------------------------------

    void sendUsage(Player player, UUID playerId, String label) {
        player.sendMessage(ChatColor.GRAY + Messages.cmdUsage(playerId, label));
    }

    void sendHelp(Player player, UUID playerId, String label) {
        String l = label;
        player.sendMessage(ChatColor.DARK_AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.AQUA + "  /" + l + " " + Messages.helpTitle(playerId));
        player.sendMessage(ChatColor.DARK_AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(Messages.helpSet(playerId, l));
        player.sendMessage(Messages.helpUpdate(playerId, l));
        player.sendMessage(Messages.helpDelete(playerId, l));
        player.sendMessage(Messages.helpRename(playerId, l));
        player.sendMessage(Messages.helpDescription(playerId, l));
        player.sendMessage(Messages.helpItems(playerId, l));
        player.sendMessage(Messages.helpDid(playerId, l));
        player.sendMessage(Messages.helpDidnt(playerId, l));
        player.sendMessage(Messages.helpLanguage(playerId, l));
        player.sendMessage(Messages.helpHelp(playerId, l));
        player.sendMessage(ChatColor.DARK_AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
