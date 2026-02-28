package checkpoint.command;

import checkpoint.CheckpointPlugin;
import checkpoint.i18n.Messages;
import checkpoint.manager.CheckpointManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public class CheckpointCommand implements TabExecutor {

    private final CheckpointPlugin plugin;
    private final CheckpointManager checkpointManager;
    private final SubcommandHandlers handlers;

    public CheckpointCommand(CheckpointPlugin plugin, CheckpointManager checkpointManager) {
        this.plugin = plugin;
        this.checkpointManager = checkpointManager;
        this.handlers = new SubcommandHandlers(plugin, checkpointManager);
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
                int descFlagIndex = findDescriptionFlag(args);
                String name;
                String desc;
                if (descFlagIndex > 0) {
                    name = String.join(" ", Arrays.copyOfRange(args, 1, descFlagIndex)).trim();
                    desc = descFlagIndex + 1 < args.length
                        ? String.join(" ", Arrays.copyOfRange(args, descFlagIndex + 1, args.length)).trim()
                        : "";
                } else {
                    name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
                    desc = "";
                }
                if (name.isEmpty()) {
                    player.sendMessage(ChatColor.RED + Messages.cmdEnterCpName(playerId));
                    return true;
                }
                handlers.handleSet(player, playerId, name, desc);
            }
            case "update" -> {
                String name = extractName(args);
                if (name == null) {
                    player.sendMessage(ChatColor.RED + Messages.cmdEnterCpName(playerId));
                    return true;
                }
                handlers.handleUpdate(player, playerId, name);
            }
            case "delete" -> {
                String name = extractName(args);
                if (name == null) {
                    player.sendMessage(ChatColor.RED + Messages.cmdEnterCpName(playerId));
                    return true;
                }
                handlers.handleDelete(player, playerId, name);
            }
            case "rename" -> {
                int nameFlagIdx = findNameFlag(args);
                if (nameFlagIdx < 0) {
                    player.sendMessage(ChatColor.RED + Messages.cmdUsageRename(playerId, label));
                    return true;
                }
                String oldName = String.join(" ", Arrays.copyOfRange(args, 1, nameFlagIdx)).trim();
                String newName = nameFlagIdx + 1 < args.length
                    ? String.join(" ", Arrays.copyOfRange(args, nameFlagIdx + 1, args.length)).trim()
                    : "";
                if (oldName.isEmpty() || newName.isEmpty()) {
                    player.sendMessage(ChatColor.RED + Messages.cmdUsageRename(playerId, label));
                    return true;
                }
                handlers.handleRename(player, playerId, oldName, newName);
            }
            case "description" -> {
                int descFlagIdx = findDescriptionFlag(args);
                if (descFlagIdx < 0) {
                    player.sendMessage(ChatColor.RED + Messages.cmdUsageDesc(playerId, label));
                    return true;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 1, descFlagIdx)).trim();
                String desc = descFlagIdx + 1 < args.length
                    ? String.join(" ", Arrays.copyOfRange(args, descFlagIdx + 1, args.length)).trim()
                    : "";
                if (name.isEmpty()) {
                    player.sendMessage(ChatColor.RED + Messages.cmdEnterCpName(playerId));
                    return true;
                }
                handlers.handleDescription(player, playerId, name, desc);
            }
            case "did" -> handlers.handleDid(player, playerId);
            case "didnt" -> handlers.handleDidnt(player, playerId);
            case "items" -> plugin.giveCheckpointItems(player);
            case "language", "lang" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + Messages.cmdUsageLanguage(playerId, label));
                    return true;
                }
                handlers.handleLanguage(player, playerId, args[1]);
            }
            case "help" -> handlers.sendHelp(player, playerId, label);
            default -> sendUsage(player, playerId, label);
        }
        return true;
    }

    private static int findDescriptionFlag(String[] args) {
        for (int i = 1; i < args.length; i++) {
            if ("-d".equals(args[i]) || "--description".equals(args[i])) {
                return i;
            }
        }
        return -1;
    }

    private static int findNameFlag(String[] args) {
        for (int i = 1; i < args.length; i++) {
            if ("-n".equals(args[i]) || "--name".equals(args[i])) {
                return i;
            }
        }
        return -1;
    }

    private String extractName(String[] args) {
        if (args.length < 2) {
            return null;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        return name.isEmpty() ? null : name;
    }

    private void sendUsage(Player player, UUID playerId, String label) {
        handlers.sendUsage(player, playerId, label);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("set", "update", "delete", "rename", "description", "items", "did", "didnt", "language", "help").stream()
                .filter(opt -> opt.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && ("language".equalsIgnoreCase(args[0]) || "lang".equalsIgnoreCase(args[0]))) {
            return List.of("ja", "en").stream()
                .filter(opt -> opt.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length >= 2 && ("delete".equalsIgnoreCase(args[0]) || "update".equalsIgnoreCase(args[0]))) {
            List<String> names = new ArrayList<>(checkpointManager.getNamedCheckpointNames(player.getUniqueId()));
            String entered = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
            return names.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(entered))
                .collect(Collectors.toList());
        }

        if (args.length >= 2 && "set".equalsIgnoreCase(args[0])) {
            String last = args[args.length - 1].toLowerCase(Locale.ROOT);
            if (findDescriptionFlag(args) < 0 && last.startsWith("-")) {
                return List.of("-d", "--description").stream()
                    .filter(opt -> opt.startsWith(last))
                    .collect(Collectors.toList());
            }
            return List.of();
        }

        if (args.length >= 2 && "rename".equalsIgnoreCase(args[0])) {
            if (findNameFlag(args) < 0) {
                String last = args[args.length - 1].toLowerCase(Locale.ROOT);
                if (last.startsWith("-")) {
                    return List.of("-n", "--name").stream()
                        .filter(opt -> opt.startsWith(last))
                        .collect(Collectors.toList());
                }
                List<String> names = new ArrayList<>(checkpointManager.getNamedCheckpointNames(player.getUniqueId()));
                String entered = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
                return names.stream()
                    .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(entered))
                    .collect(Collectors.toList());
            }
            return List.of();
        }

        if (args.length >= 2 && "description".equalsIgnoreCase(args[0])) {
            if (findDescriptionFlag(args) < 0) {
                String last = args[args.length - 1].toLowerCase(Locale.ROOT);
                if (last.startsWith("-")) {
                    return List.of("-d", "--description").stream()
                        .filter(opt -> opt.startsWith(last))
                        .collect(Collectors.toList());
                }
                List<String> names = new ArrayList<>(checkpointManager.getNamedCheckpointNames(player.getUniqueId()));
                String entered = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
                return names.stream()
                    .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(entered))
                    .collect(Collectors.toList());
            }
            return List.of();
        }

        return List.of();
    }
}
