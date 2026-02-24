package checkpoint.listener;

import checkpoint.gui.GuiConstants;
import checkpoint.gui.MenuManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles all inventory click events for plugin menus and delegates
 * the actual logic to {@link MenuManager}.
 */
public class InventoryClickListener implements Listener {

    private final MenuManager menuManager;

    public InventoryClickListener(MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (GuiConstants.SORT_TITLE.equals(title)) {
            event.setCancelled(true);
            menuManager.handleSortMenuClick(player, event.getRawSlot());
        } else if (GuiConstants.PLAYER_SELECT_TITLE.equals(title)) {
            event.setCancelled(true);
            menuManager.handlePlayerSelectMenuClick(player, event);
        } else if (GuiConstants.CP_OPERATION_TITLE.equals(title)) {
            event.setCancelled(true);
            menuManager.handleCpOperationMenuClick(player, event.getRawSlot());
        } else if (GuiConstants.GUI_TITLE.equals(title)) {
            if (event.getClickedInventory() == null) return;
            event.setCancelled(true);
            menuManager.handleMainMenuClick(player, event);
        }
    }
}
