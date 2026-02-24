package checkpoint.listener;

import checkpoint.gui.GuiConstants;
import checkpoint.gui.MenuManager;
import checkpoint.i18n.Messages;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles player interact events (item usage), item drop prevention,
 * and inventory close cleanup.
 */
public class PlayerListener implements Listener {

    private final MenuManager menuManager;
    private final NamespacedKey cpItemKey;

    public PlayerListener(MenuManager menuManager, NamespacedKey cpItemKey) {
        this.menuManager = menuManager;
        this.cpItemKey = cpItemKey;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Messages.setLang(player.getUniqueId(), Messages.detectLang(player.getLocale()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Messages.removeLang(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        ItemMeta meta = dropped.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(cpItemKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            item = event.getPlayer().getInventory().getItem(event.getHand());
        }
        if (item == null || item.getType().isAir()) {
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
        } else if (type == Material.HEART_OF_THE_SEA) {
            event.setCancelled(true);
            menuManager.openCheckpointMenu(player, menuManager.getMenuPage(player.getUniqueId()));
        } else if (type == Material.FEATHER) {
            event.setCancelled(true);
            handleGameModeToggle(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!GuiConstants.isOurMenu(title)) return;
        menuManager.scheduleMenuCloseCleanup(player);
    }

    private void handleGameModeToggle(Player player) {
        java.util.UUID playerId = player.getUniqueId();
        GameMode current = player.getGameMode();
        if (current == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(ChatColor.GREEN + Messages.gmCreative(playerId));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
            return;
        }

        if (current == GameMode.CREATIVE) {
            player.setGameMode(GameMode.ADVENTURE);
            player.sendMessage(ChatColor.GREEN + Messages.gmAdventure(playerId));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
        }
    }
}
