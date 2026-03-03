package checkpoint.compat;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

/**
 * Compatibility layer for Minecraft 1.12.x servers.
 * Pre-flattening materials (same as 1.8) but with 1.9+ sounds, EquipmentSlot, etc.
 */
public class Compat1_12 extends CompatLegacy {

    // -----------------------------------------------------------------------
    // Sounds (1.9+ naming convention)
    // -----------------------------------------------------------------------
    @Override public Sound soundButtonClick()    { return Sound.UI_BUTTON_CLICK; }
    @Override public Sound soundItemPickup()     { return Sound.ENTITY_ITEM_PICKUP; }
    @Override public Sound soundExpOrb()         { return Sound.ENTITY_EXPERIENCE_ORB_PICKUP; }

    // -----------------------------------------------------------------------
    // Player locale (direct API — available since 1.12)
    // -----------------------------------------------------------------------
    @Override
    public String getPlayerLocale(Player player) {
        return player.getLocale();
    }

    // -----------------------------------------------------------------------
    // Equipment slot (available since 1.9)
    // -----------------------------------------------------------------------
    @Override
    public boolean isMainHand(PlayerInteractEvent event) {
        return event.getHand() == EquipmentSlot.HAND;
    }

    // -----------------------------------------------------------------------
    // Teleport preparation: isGliding available (1.9+), no wakeup
    // -----------------------------------------------------------------------
    @Override
    public void safePrepareForTeleport(Player player) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
            player.eject();
        }
        if (player.isGliding()) {
            player.setGliding(false);
        }
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
    }
}
