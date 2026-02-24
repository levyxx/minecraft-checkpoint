package checkpoint.command;

import checkpoint.CheckpointPlugin;
import checkpoint.i18n.Messages;
import checkpoint.i18n.Messages.Lang;
import checkpoint.manager.CheckpointManager;
import checkpoint.model.Checkpoint;
import checkpoint.model.RenameResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public class CheckpointCommand implements TabExecutor {

    private final CheckpointPlugin plugin;
    private final CheckpointManager checkpointManager;

    public CheckpointCommand(CheckpointPlugin plugin, CheckpointManager checkpointManager) {
        this.plugin = plugin;
        this.checkpointManager = checkpointManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        UUID playerId = player.getUniqueId();

        if (args.length == 0) {
            sendUsage(player, playerId, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "set" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + Messages.cmdEnterCpName(playerId));
                    return true;
                }
                String name = args[1].trim();
                if (name.isEmpty()) {
                    player.sendMessage(ChatColor.RED + Messages.cmdEnterCpName(playerId));
                    return true;
                }
                String desc = args.length >= 3
                    ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim()
                    : "";
                handleSet(player, playerId, name, desc);
            }
            case "update" -> {
                String name = extractName(args);
                if (name == null) {
                    player.sendMessage(ChatColor.RED + Messages.cmdEnterCpName(playerId));
                    return true;
                }
                handleUpdate(player, playerId, name);
            }
            case "delete" -> {
                String name = extractName(args);
                if (name == null) {
                    player.sendMessage(ChatColor.RED + Messages.cmdEnterCpName(playerId));
                    return true;
                }
                handleDelete(player, playerId, name);
            }
            case "rename" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + Messages.cmdUsageRename(playerId, label));
                    return true;
                }
                handleRename(player, playerId, args[1], args[2]);
            }
            case "description" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + Messages.cmdUsageDesc(playerId, label));
                    return true;
                }
                String name = args[1].trim();
                String desc = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
                handleDescription(player, playerId, name, desc);
            }
            case "items" -> plugin.giveCheckpointItems(player);
            case "language", "lang" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + Messages.cmdUsageLanguage(playerId, label));
                    return true;
                }
                handleLanguage(player, playerId, args[1]);
            }
            case "help" -> sendHelp(player, playerId, label);
            default -> sendUsage(player, playerId, label);
        }
        return true;
    }

    private String extractName(String[] args) {
        if (args.length < 2) {
            return null;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        return name.isEmpty() ? null : name;
    }

    private void handleSet(Player player, UUID playerId, String name, String description) {
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

    private void handleDescription(Player player, UUID playerId, String name, String description) {
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

    private void handleUpdate(Player player, UUID playerId, String name) {
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

    private void handleDelete(Player player, UUID playerId, String name) {
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

    private void handleRename(Player player, UUID playerId, String oldName, String newName) {
        RenameResult result;
        try {
            result = checkpointManager.renameNamedCheckpoint(playerId, oldName, newName);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
            return;
        }

        switch (result) {
            case SUCCESS -> player.sendMessage(ChatColor.GREEN
                + Messages.cmdRenameSuccess(playerId, oldName, newName));
            case OLD_NOT_FOUND -> player.sendMessage(ChatColor.RED
                + Messages.cmdRenameOldNotFound(playerId, oldName));
            case NEW_ALREADY_EXISTS -> player.sendMessage(ChatColor.RED
                + Messages.cmdRenameNewExists(playerId, newName));
        }
    }

    private void handleLanguage(Player player, UUID playerId, String langArg) {
        String lower = langArg.toLowerCase(Locale.ROOT);
        if ("jp".equals(lower) || "ja".equals(lower)) {
            Messages.setLang(playerId, Lang.JP);
        } else if ("en".equals(lower)) {
            Messages.setLang(playerId, Lang.EN);
        } else {
            player.sendMessage(ChatColor.RED + Messages.cmdUsageLanguage(playerId, "cp"));
            return;
        }
        player.sendMessage(ChatColor.GREEN + Messages.cmdLangChanged(playerId));
    }

    private void sendUsage(Player player, UUID playerId, String label) {
        player.sendMessage(ChatColor.YELLOW + Messages.cmdUsage(playerId, label));
    }

    private void sendHelp(Player player, UUID playerId, String label) {
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
        player.sendMessage(Messages.helpLanguage(playerId, l));
        player.sendMessage(Messages.helpHelp(playerId, l));
        player.sendMessage(ChatColor.DARK_AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("set", "update", "delete", "rename", "description", "items", "language", "help").stream()
                .filter(opt -> opt.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && ("language".equalsIgnoreCase(args[0]) || "lang".equalsIgnoreCase(args[0]))) {
            return List.of("jp", "en").stream()
                .filter(opt -> opt.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length >= 2 && ("delete".equalsIgnoreCase(args[0]) || "update".equalsIgnoreCase(args[0])
                || "rename".equalsIgnoreCase(args[0]) || "description".equalsIgnoreCase(args[0]))) {
            List<String> names = new ArrayList<>(checkpointManager.getNamedCheckpointNames(player.getUniqueId()));
            String entered = args[1].toLowerCase(Locale.ROOT);
            return names.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(entered))
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
