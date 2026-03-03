package checkpoint.gui;

import checkpoint.compat.VersionCompat;
import checkpoint.i18n.Messages;
import checkpoint.model.Checkpoint;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import static checkpoint.gui.GuiConstants.*;

/**
 * Handles teleportation logic and checkpoint operation execution from the GUI.
 */
class TeleportHandler {

    private final MenuManager mgr;

    TeleportHandler(MenuManager mgr) {
        this.mgr = mgr;
    }

    // -----------------------------------------------------------------------
    // Quick checkpoint save (slime ball)
    // -----------------------------------------------------------------------

    void handleQuickCheckpointSave(Player player) {
        UUID playerId = player.getUniqueId();
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            player.sendMessage(ChatColor.RED + Messages.cmdWorldError(playerId));
            return;
        }

        Checkpoint checkpoint = new Checkpoint(
            world.getName(), location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
        mgr.checkpointManager.setQuickCheckpoint(playerId, checkpoint);
        mgr.markLastSelection(playerId, MenuManager.SelectionType.QUICK, null);
        player.sendMessage(ChatColor.GREEN + Messages.quickSaved(playerId));
        player.playSound(location, VersionCompat.get().soundExpOrb(), 0.8f, 1.5f);
    }

    // -----------------------------------------------------------------------
    // Nether star teleport
    // -----------------------------------------------------------------------

    void handleCheckpointTeleport(Player player) {
        UUID playerId = player.getUniqueId();
        Optional<Checkpoint> target = resolveTeleportTarget(playerId);

        if (!target.isPresent()) {
            player.sendMessage(ChatColor.YELLOW + Messages.noCheckpoint(playerId));
            return;
        }

        Checkpoint checkpoint = target.get();
        World world = Bukkit.getWorld(checkpoint.worldName());
        if (world == null) {
            player.sendMessage(ChatColor.RED + Messages.worldNotFound(playerId));
            return;
        }

        Location destination = new Location(
            world, checkpoint.x(), checkpoint.y(), checkpoint.z(),
            checkpoint.yaw(), checkpoint.pitch());
        VersionCompat.get().safePrepareForTeleport(player);
        ensureChunkLoaded(destination);
        attemptTeleport(player, destination, TELEPORT_MAX_ATTEMPTS);
    }

    private Optional<Checkpoint> resolveTeleportTarget(UUID playerId) {
        MenuManager.LastSelection lastSelection = mgr.lastSelections.get(playerId);
        if (lastSelection != null) {
            switch (lastSelection.type()) {
                case NAMED: {
                    Optional<Checkpoint> named = mgr.checkpointManager.getNamedCheckpoint(playerId, lastSelection.identifier());
                    if (named.isPresent()) return named;
                    mgr.lastSelections.remove(playerId);
                    break;
                }
                case QUICK: {
                    Optional<Checkpoint> quick = mgr.checkpointManager.getQuickCheckpoint(playerId);
                    if (quick.isPresent()) return quick;
                    mgr.lastSelections.remove(playerId);
                    break;
                }
            }
        }
        Optional<Checkpoint> selectedNamed = mgr.checkpointManager.getSelectedNamedCheckpoint(playerId);
        if (selectedNamed.isPresent()) return selectedNamed;
        return mgr.checkpointManager.getQuickCheckpoint(playerId);
    }

    // -----------------------------------------------------------------------
    // CP operation executors
    // -----------------------------------------------------------------------

    void executeTeleportToCp(Player viewer, UUID targetId, String cpName) {
        UUID viewerId = viewer.getUniqueId();
        Optional<Checkpoint> cpOpt = mgr.checkpointManager.getNamedCheckpoint(targetId, cpName);
        if (!cpOpt.isPresent()) {
            viewer.sendMessage(ChatColor.RED + Messages.cpNotFound(viewerId));
            viewer.closeInventory();
            return;
        }
        Checkpoint checkpoint = cpOpt.get();
        World world = Bukkit.getWorld(checkpoint.worldName());
        if (world == null) {
            viewer.sendMessage(ChatColor.RED + Messages.worldNotFound(viewerId));
            viewer.closeInventory();
            return;
        }
        Location destination = new Location(world,
            checkpoint.x(), checkpoint.y(), checkpoint.z(),
            checkpoint.yaw(), checkpoint.pitch());
        viewer.closeInventory();
        VersionCompat.get().safePrepareForTeleport(viewer);
        ensureChunkLoaded(destination);
        attemptTeleport(viewer, destination, TELEPORT_MAX_ATTEMPTS);
        viewer.playSound(viewer.getLocation(), VersionCompat.get().soundButtonClick(), 0.6f, 1.4f);
    }

    void executeUpdateCp(Player viewer, String cpName) {
        UUID viewerId = viewer.getUniqueId();
        Location location = viewer.getLocation();
        World world = location.getWorld();
        if (world == null) {
            viewer.sendMessage(ChatColor.RED + Messages.cmdWorldError(viewerId));
            return;
        }
        Checkpoint updated = new Checkpoint(world.getName(),
            location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
        boolean success = mgr.checkpointManager.updateNamedCheckpoint(viewerId, cpName, updated);
        viewer.closeInventory();
        if (success) {
            viewer.sendMessage(ChatColor.GREEN + Messages.cpUpdateSuccess(viewerId, cpName));
            viewer.playSound(viewer.getLocation(), VersionCompat.get().soundButtonClick(), 0.6f, 1.4f);
            mgr.openCheckpointMenu(viewer, mgr.menuPages.getOrDefault(viewerId, 0));
        } else {
            viewer.sendMessage(ChatColor.RED + Messages.cpUpdateFailed(viewerId));
        }
    }

    void executeDeleteCp(Player viewer, String cpName) {
        UUID viewerId = viewer.getUniqueId();
        boolean success = mgr.checkpointManager.removeNamedCheckpoint(viewerId, cpName);
        mgr.notifyNamedCheckpointDeleted(viewerId, cpName);
        viewer.closeInventory();
        if (success) {
            viewer.sendMessage(ChatColor.GREEN + Messages.cpDeleteSuccess(viewerId, cpName));
            viewer.playSound(viewer.getLocation(), VersionCompat.get().soundButtonClick(), 0.6f, 1.4f);
            mgr.openCheckpointMenu(viewer, Math.max(0, mgr.menuPages.getOrDefault(viewerId, 0) - 1));
        } else {
            viewer.sendMessage(ChatColor.RED + Messages.cpDeleteFailed(viewerId));
        }
    }

    void executeCloneCp(Player viewer, UUID targetId, String cpName) {
        UUID viewerId = viewer.getUniqueId();
        Optional<Checkpoint> cpOpt = mgr.checkpointManager.getNamedCheckpoint(targetId, cpName);
        if (!cpOpt.isPresent()) {
            viewer.sendMessage(ChatColor.RED + Messages.cpNotFound(viewerId));
            viewer.closeInventory();
            return;
        }
        Checkpoint src = cpOpt.get();
        Checkpoint cloned = new Checkpoint(src.worldName(),
            src.x(), src.y(), src.z(), src.yaw(), src.pitch(),
            Instant.now(), Instant.now(), src.description());
        boolean success = mgr.checkpointManager.addNamedCheckpoint(viewerId, cpName, cloned);
        viewer.closeInventory();
        if (success) {
            mgr.checkpointManager.recordClone(viewerId, targetId);
            viewer.sendMessage(ChatColor.GREEN + Messages.cpCloneSuccess(viewerId, cpName));
            viewer.playSound(viewer.getLocation(), VersionCompat.get().soundButtonClick(), 0.6f, 1.4f);
        } else {
            viewer.sendMessage(ChatColor.YELLOW + Messages.cpCloneDuplicate(viewerId, cpName));
        }
    }

    // -----------------------------------------------------------------------
    // Teleport helpers
    // -----------------------------------------------------------------------

    private void ensureChunkLoaded(Location destination) {
        World world = destination.getWorld();
        if (world == null) return;
        int chunkX = destination.getBlockX() >> 4;
        int chunkZ = destination.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.loadChunk(chunkX, chunkZ);
        }
    }

    private void attemptTeleport(Player player, Location destination, int attemptsRemaining) {
        if (attemptsRemaining <= 0) {
            player.sendMessage(ChatColor.RED + Messages.teleportFailed(player.getUniqueId()));
            return;
        }
        boolean success = player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (success) {
            stabilizePlayer(player);
        } else {
            Bukkit.getScheduler().runTaskLater(mgr.plugin,
                () -> attemptTeleport(player, destination, attemptsRemaining - 1),
                TELEPORT_RETRY_DELAY_TICKS);
        }
    }

    private void stabilizePlayer(Player player) {
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);
        Bukkit.getScheduler().runTaskLater(mgr.plugin, () -> {
            if (player.isOnline()) {
                player.setVelocity(new Vector(0, 0, 0));
                player.setFallDistance(0f);
            }
        }, 1L);
    }
}
