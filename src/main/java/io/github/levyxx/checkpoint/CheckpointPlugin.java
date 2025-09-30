package io.github.levyxx.checkpoint;

import io.github.levyxx.checkpoint.CheckpointManager.Checkpoint;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class CheckpointPlugin extends JavaPlugin implements Listener {

    private CheckpointManager checkpointManager;

    @Override
    public void onEnable() {
        this.checkpointManager = new CheckpointManager();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Checkpoint plugin enabled.");
    }

    @Override
    public void onDisable() {
        this.checkpointManager = null;
        getLogger().info("Checkpoint plugin disabled.");
    }

    // @EventHandler(ignoreCancelled=true)だとRIGHT_CLICK_AIRが取れない
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            event.getPlayer().sendMessage("action != RIGHT_CLICK_AIR && action != RIGHT_CLICK_BLOCK");
            return;
        }

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        Material type = item.getType();
        if (type == Material.SLIME_BALL) {
            event.setCancelled(true);
            handleCheckpointSave(event.getPlayer());
        } else if (type == Material.NETHER_STAR) {
            event.setCancelled(true);
            handleCheckpointTeleport(event.getPlayer());
        }
    }

    private void handleCheckpointSave(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + "ワールド情報が取得できませんでした。");
            return;
        }

        UUID playerId = player.getUniqueId();
        Checkpoint checkpoint = new Checkpoint(
            world.getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
        checkpointManager.setCheckpoint(playerId, checkpoint);
        player.sendMessage(ChatColor.GREEN + "チェックポイントを保存しました！");
        player.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
    }

    private void handleCheckpointTeleport(Player player) {
        UUID playerId = player.getUniqueId();
        Optional<Checkpoint> optionalCheckpoint = checkpointManager.getCheckpoint(playerId);

        if (optionalCheckpoint.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "チェックポイントがまだ登録されていません。");
            return;
        }

        Checkpoint checkpoint = optionalCheckpoint.get();
        World world = Bukkit.getWorld(checkpoint.worldName());
        if (world == null) {
            player.sendMessage(ChatColor.RED + "チェックポイントのワールドが見つかりませんでした。");
            return;
        }

        Location destination = new Location(
            world,
            checkpoint.x(),
            checkpoint.y(),
            checkpoint.z(),
            checkpoint.yaw(),
            checkpoint.pitch()
        );

        preparePlayerForTeleport(player);
        player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    private void preparePlayerForTeleport(Player player) {
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
    }
}
