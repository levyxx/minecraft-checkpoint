package checkpoint.listener;

import checkpoint.gui.MenuManager;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Handles chat input for search, rename, and description operations.
 * Delegates actual processing to {@link MenuManager}.
 */
public class ChatInputListener implements Listener {

    private final MenuManager menuManager;

    public ChatInputListener(MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @SuppressWarnings("deprecation") 
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String message = event.getMessage().trim();

        if (menuManager.tryHandleDescriptionInput(playerId, player, message)) {
            event.setCancelled(true);
            return;
        }

        if (menuManager.tryHandleRenameInput(playerId, player, message)) {
            event.setCancelled(true);
            return;
        }

        if (menuManager.tryHandleSearchInput(playerId, player, message)) {
            event.setCancelled(true);
        }
    }
}
