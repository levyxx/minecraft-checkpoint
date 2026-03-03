package checkpoint.compat;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Compatibility layer for Minecraft 1.21.x (post-flattening) servers.
 * Uses modern material names, PersistentDataContainer, etc.
 */
public class Compat1_21 extends VersionCompat {

    private final NamespacedKey cpItemKey;
    private final NamespacedKey targetUuidKey;

    public Compat1_21(JavaPlugin plugin) {
        this.cpItemKey = new NamespacedKey(plugin, "cp_utility_item");
        this.targetUuidKey = new NamespacedKey(plugin, "target_uuid");
    }

    // -----------------------------------------------------------------------
    // Glass panes
    // -----------------------------------------------------------------------
    @Override public ItemStack cyanGlassPane()     { return new ItemStack(Material.CYAN_STAINED_GLASS_PANE); }
    @Override public ItemStack lightBlueGlassPane() { return new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE); }
    @Override public ItemStack blueGlassPane()      { return new ItemStack(Material.BLUE_STAINED_GLASS_PANE); }

    // -----------------------------------------------------------------------
    // Wool
    // -----------------------------------------------------------------------
    @Override public ItemStack limeWool()   { return new ItemStack(Material.LIME_WOOL); }
    @Override public ItemStack redWool()    { return new ItemStack(Material.RED_WOOL); }
    @Override public ItemStack greenWool()  { return new ItemStack(Material.GREEN_WOOL); }
    @Override public ItemStack blueWool()   { return new ItemStack(Material.BLUE_WOOL); }
    @Override public ItemStack yellowWool() { return new ItemStack(Material.YELLOW_WOOL); }
    @Override public ItemStack orangeWool() { return new ItemStack(Material.ORANGE_WOOL); }
    @Override public ItemStack cyanWool()   { return new ItemStack(Material.CYAN_WOOL); }

    // -----------------------------------------------------------------------
    // Sort dyes
    // -----------------------------------------------------------------------
    private static final Material[] DYES = {
        Material.LIME_DYE, Material.GREEN_DYE, Material.CYAN_DYE,
        Material.BLUE_DYE, Material.LIGHT_BLUE_DYE, Material.PURPLE_DYE,
        Material.YELLOW_DYE,
    };
    @Override public ItemStack sortDyeBase(int index) { return new ItemStack(DYES[index]); }

    // -----------------------------------------------------------------------
    // Player head
    // -----------------------------------------------------------------------
    @Override public ItemStack createPlayerHead() { return new ItemStack(Material.PLAYER_HEAD); }

    // -----------------------------------------------------------------------
    // Materials
    // -----------------------------------------------------------------------
    @Override public Material writableBookMaterial() { return Material.WRITABLE_BOOK; }
    @Override public Material sortButtonMaterial()   { return Material.COMPASS; }
    @Override public Material snowballMaterial()     { return Material.SNOWBALL; }

    // -----------------------------------------------------------------------
    // Material checks
    // -----------------------------------------------------------------------
    @Override public boolean isWoolCpItem(Material mat) {
        return mat == Material.LIME_WOOL || mat == Material.RED_WOOL;
    }
    @Override public boolean isPlayerHead(Material mat) {
        return mat == Material.PLAYER_HEAD;
    }

    // -----------------------------------------------------------------------
    // Sounds
    // -----------------------------------------------------------------------
    @Override public Sound soundButtonClick()    { return Sound.UI_BUTTON_CLICK; }
    @Override public Sound soundItemPickup()     { return Sound.ENTITY_ITEM_PICKUP; }
    @Override public Sound soundExpOrb()         { return Sound.ENTITY_EXPERIENCE_ORB_PICKUP; }

    // -----------------------------------------------------------------------
    // Enchantment
    // -----------------------------------------------------------------------
    @Override public Enchantment luckEnchantment() { return Enchantment.LUCK; }

    // -----------------------------------------------------------------------
    // Skull owner (modern API)
    // -----------------------------------------------------------------------
    @Override public void setSkullOwner(SkullMeta meta, OfflinePlayer player) {
        meta.setOwningPlayer(player);
    }

    // -----------------------------------------------------------------------
    // Item tagging (PersistentDataContainer)
    // -----------------------------------------------------------------------
    @Override public void markAsPluginItem(ItemMeta meta) {
        meta.getPersistentDataContainer().set(cpItemKey, PersistentDataType.BYTE, (byte) 1);
    }
    @Override public boolean isPluginItem(ItemMeta meta) {
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(cpItemKey, PersistentDataType.BYTE);
    }
    @Override public void setTargetUuid(ItemMeta meta, String uuid) {
        meta.getPersistentDataContainer().set(targetUuidKey, PersistentDataType.STRING, uuid);
    }
    @Override public String getTargetUuid(ItemMeta meta) {
        return meta.getPersistentDataContainer().get(targetUuidKey, PersistentDataType.STRING);
    }

    // -----------------------------------------------------------------------
    // Player helpers
    // -----------------------------------------------------------------------
    @Override public String getPlayerLocale(Player player) {
        return player.getLocale();
    }
    @Override public boolean isMainHand(PlayerInteractEvent event) {
        return event.getHand() == EquipmentSlot.HAND;
    }
    @Override public boolean isItemAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }
    @Override public void safePrepareForTeleport(Player player) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
            player.eject();
        }
        if (player.isGliding()) {
            player.setGliding(false);
        }
        if (player.isSleeping()) {
            player.wakeup(false);
        }
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
    }
}
