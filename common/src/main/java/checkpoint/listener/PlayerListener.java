package checkpoint.listener;

import checkpoint.compat.VersionCompat;
import checkpoint.gui.GuiConstants;
import checkpoint.gui.MenuManager;
import checkpoint.i18n.Messages;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Handles player interact events (item usage), item drop prevention,
 * and inventory close cleanup.
 */
public class PlayerListener implements Listener {

    private final MenuManager menuManager;

    public PlayerListener(MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (Messages.isManuallySet(playerId)) {
            Messages.setLang(playerId, Messages.getManualLang(playerId));
        } else {
            Messages.setLang(playerId, Messages.detectLang(
                VersionCompat.get().getPlayerLocale(player)));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Messages.removeLang(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        ItemMeta meta = dropped.getItemMeta();
        if (meta != null && VersionCompat.get().isPluginItem(meta)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        VersionCompat compat = VersionCompat.get();

        if (!compat.isMainHand(event)) {
            return;
        }

        Action action = event.getAction();

        ItemStack item = event.getItem();
        if (compat.isItemAir(item)) {
            item = event.getPlayer().getItemInHand();
        }
        if (compat.isItemAir(item)) {
            return;
        }

        Material type = item.getType();
        Player player = event.getPlayer();

        if (type == Material.NETHER_STAR
            && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            menuManager.openCheckpointMenu(player, menuManager.getMenuPage(player.getUniqueId()));
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (type == Material.SLIME_BALL) {
            event.setCancelled(true);
            menuManager.handleQuickCheckpointSave(player);
        } else if (type == Material.NETHER_STAR) {
            event.setCancelled(true);
            menuManager.handleCheckpointTeleport(player);
        } else if (type == Material.DIAMOND) {
            event.setCancelled(true);
            menuManager.openCheckpointMenu(player, menuManager.getMenuPage(player.getUniqueId()));
        } else if (type == Material.FEATHER) {
            event.setCancelled(true);
            handleGameModeToggle(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();
        if (!GuiConstants.isOurMenu(title)) return;
        menuManager.scheduleMenuCloseCleanup(player);
    }

    @SuppressWarnings("deprecation")
    private void handleGameModeToggle(Player player) {
        UUID playerId = player.getUniqueId();
        GameMode current = player.getGameMode();
        if (current == GameMode.CREATIVE) {
            player.setGameMode(GameMode.ADVENTURE);
            player.sendMessage(ChatColor.GREEN + Messages.gmAdventure(playerId));
            player.playSound(player.getLocation(), VersionCompat.get().soundButtonClick(), 0.6f, 0.8f);
        } else {
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(ChatColor.GREEN + Messages.gmCreative(playerId));
            player.playSound(player.getLocation(), VersionCompat.get().soundButtonClick(), 0.6f, 1.3f);
        }
    }
}
