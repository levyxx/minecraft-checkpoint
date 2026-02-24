package checkpoint.command;

import checkpoint.CheckpointPlugin;
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
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ使用できます。");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        UUID playerId = player.getUniqueId();

        switch (subCommand) {
            case "set" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "チェックポイント名を入力してください。");
                    return true;
                }
                String name = args[1].trim();
                if (name.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "チェックポイント名を入力してください。");
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
                    sender.sendMessage(ChatColor.RED + "チェックポイント名を入力してください。");
                    return true;
                }
                handleUpdate(player, playerId, name);
            }
            case "delete" -> {
                String name = extractName(args);
                if (name == null) {
                    sender.sendMessage(ChatColor.RED + "チェックポイント名を入力してください。");
                    return true;
                }
                handleDelete(player, playerId, name);
            }
            case "rename" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "使い方: /" + label + " rename <元のCP名> <変更後のCP名>");
                    return true;
                }
                handleRename(player, playerId, args[1], args[2]);
            }
            case "description" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "使い方: /" + label + " description <CP名> <説明>");
                    return true;
                }
                String name = args[1].trim();
                String desc = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
                handleDescription(player, playerId, name, desc);
            }
            case "items" -> plugin.giveCheckpointItems(player);
            case "help" -> sendHelp(sender, label);
            default -> sendUsage(sender, label);
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
            player.sendMessage(ChatColor.GREEN + "チェックポイント『" + name + "』を保存しました。");
            plugin.notifyNamedCheckpointSet(player, name);
        } else {
            player.sendMessage(ChatColor.RED + "チェックポイント『" + name + "』は既に存在します。");
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
            player.sendMessage(ChatColor.GREEN + "『" + name + "』の説明を設定しました。");
        } else {
            player.sendMessage(ChatColor.RED + "チェックポイント『" + name + "』が見つかりませんでした。");
        }
    }

    private void handleUpdate(Player player, UUID playerId, String name) {
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

        boolean updated;
        try {
            updated = checkpointManager.updateNamedCheckpoint(playerId, name, checkpoint);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
            return;
        }

        if (updated) {
            player.sendMessage(ChatColor.GREEN + "チェックポイント『" + name + "』を更新しました。");
            plugin.notifyNamedCheckpointSet(player, name);
        } else {
            player.sendMessage(ChatColor.RED + "チェックポイント『" + name + "』は存在しません。");
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
            player.sendMessage(ChatColor.GREEN + "チェックポイント『" + name + "』を削除しました。");
            plugin.notifyNamedCheckpointDeleted(playerId, name);
        } else {
            player.sendMessage(ChatColor.RED + "チェックポイント『" + name + "』は見つかりませんでした。");
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
                + "チェックポイント『" + oldName + "』を『" + newName + "』に変更しました。");
            case OLD_NOT_FOUND -> player.sendMessage(ChatColor.RED
                + "チェックポイント『" + oldName + "』は見つかりませんでした。");
            case NEW_ALREADY_EXISTS -> player.sendMessage(ChatColor.RED
                + "チェックポイント『" + newName + "』は既に存在します。");
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "使い方: /" + label + " <set|update|delete|rename|description|items|help>");
    }

    private void sendHelp(CommandSender sender, String label) {
        String l = label;
        sender.sendMessage(ChatColor.DARK_AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage(ChatColor.AQUA + "  /" + l + " コマンド一覧");
        sender.sendMessage(ChatColor.DARK_AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage(ChatColor.YELLOW + "/" + l + " set <名前> [説明]"
            + ChatColor.GRAY + "  現在地を指定した名前で保存します（説明は省略可）");
        sender.sendMessage(ChatColor.YELLOW + "/" + l + " update <名前>"
            + ChatColor.GRAY + "  既存CPの座標を現在地で上書きします");
        sender.sendMessage(ChatColor.YELLOW + "/" + l + " delete <名前>"
            + ChatColor.GRAY + "  指定したCPを削除します");
        sender.sendMessage(ChatColor.YELLOW + "/" + l + " rename <元の名前> <新しい名前>"
            + ChatColor.GRAY + "  CPの名前を変更します");
        sender.sendMessage(ChatColor.YELLOW + "/" + l + " description <名前> <説明>"
            + ChatColor.GRAY + "  CPに説明を設定します");
        sender.sendMessage(ChatColor.YELLOW + "/" + l + " items"
            + ChatColor.GRAY + "  チェックポイント用アイテムを受け取ります");
        sender.sendMessage(ChatColor.YELLOW + "/" + l + " help"
            + ChatColor.GRAY + "  このヘルプを表示します");
        sender.sendMessage(ChatColor.DARK_AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("set", "update", "delete", "rename", "description", "items", "help").stream()
                .filter(opt -> opt.startsWith(args[0].toLowerCase(Locale.ROOT)))
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
