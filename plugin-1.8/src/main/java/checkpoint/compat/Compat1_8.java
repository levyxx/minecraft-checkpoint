package checkpoint.compat;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

/**
 * Compatibility layer for Minecraft 1.8.x servers.
 * Uses old sound names and no EquipmentSlot / isGliding / wakeup.
 */
public class Compat1_8 extends CompatLegacy {

    // -----------------------------------------------------------------------
    // Sounds (1.8 naming convention)
    // -----------------------------------------------------------------------
    @Override public Sound soundButtonClick()    { return Sound.valueOf("CLICK"); }
    @Override public Sound soundItemPickup()     { return Sound.valueOf("ITEM_PICKUP"); }
    @Override public Sound soundExpOrb()         { return Sound.valueOf("ORB_PICKUP"); }

    // -----------------------------------------------------------------------
    // Player locale (reflection fallback for 1.8)
    // -----------------------------------------------------------------------
    @Override
    public String getPlayerLocale(Player player) {
        try {
            // Try Player.getLocale() — available in some Spigot 1.8 builds
            java.lang.reflect.Method m = player.getClass().getMethod("getLocale");
            return (String) m.invoke(player);
        } catch (Exception ignored) {
            try {
                // Try player.spigot().getLocale()
                Object spigot = player.getClass().getMethod("spigot").invoke(player);
                java.lang.reflect.Method lm = spigot.getClass().getMethod("getLocale");
                return (String) lm.invoke(spigot);
            } catch (Exception ignored2) {
                return "en_us";
            }
        }
    }

    // -----------------------------------------------------------------------
    // Equipment slot: no off-hand in 1.8, always main hand
    // -----------------------------------------------------------------------
    @Override
    public boolean isMainHand(PlayerInteractEvent event) {
        return true;
    }

    // -----------------------------------------------------------------------
    // Teleport preparation: no isGliding / wakeup in 1.8
    // -----------------------------------------------------------------------
    @Override
    public void safePrepareForTeleport(Player player) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
            player.eject();
        }
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
    }
}
