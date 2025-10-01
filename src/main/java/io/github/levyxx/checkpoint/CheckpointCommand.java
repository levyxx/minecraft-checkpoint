package io.github.levyxx.checkpoint;

import io.github.levyxx.checkpoint.CheckpointManager.Checkpoint;
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
                String name = extractName(args);
                if (name == null) {
                    sender.sendMessage(ChatColor.RED + "チェックポイント名を入力してください。");
                    return true;
                }
                handleSet(player, playerId, name);
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
            case "items" -> plugin.giveCheckpointItems(player);
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

    private void handleSet(Player player, UUID playerId, String name) {
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
            player.sendMessage(ChatColor.GREEN + "チェックポイント『" + name + "』を保存しました。");
            plugin.notifyNamedCheckpointSet(player, name);
        } else {
            player.sendMessage(ChatColor.RED + "チェックポイント『" + name + "』は既に存在します。");
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

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "使い方: /" + label + " set <名前>・/" + label + " update <名前>・/" + label + " delete <名前>・/" + label + " items");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("set", "update", "delete", "items").stream()
                .filter(opt -> opt.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length >= 2 && ("delete".equalsIgnoreCase(args[0]) || "update".equalsIgnoreCase(args[0]))) {
            List<String> names = new ArrayList<>(checkpointManager.getNamedCheckpointNames(player.getUniqueId()));
            String entered = args[1].toLowerCase(Locale.ROOT);
            return names.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(entered))
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
