package checkpoint.compat;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Abstraction layer for version-specific Bukkit API differences.
 * Each supported Minecraft version provides a concrete implementation.
 */
public abstract class VersionCompat {

    private static VersionCompat instance;

    public static void init(VersionCompat impl) { instance = impl; }
    public static VersionCompat get() { return instance; }

    // -----------------------------------------------------------------------
    // Glass panes (colored — need data values in pre-1.13)
    // -----------------------------------------------------------------------
    public abstract ItemStack cyanGlassPane();
    public abstract ItemStack lightBlueGlassPane();
    public abstract ItemStack blueGlassPane();

    // -----------------------------------------------------------------------
    // Wool (colored — need data values in pre-1.13)
    // -----------------------------------------------------------------------
    public abstract ItemStack limeWool();
    public abstract ItemStack redWool();
    public abstract ItemStack greenWool();
    public abstract ItemStack blueWool();
    public abstract ItemStack yellowWool();
    public abstract ItemStack orangeWool();
    public abstract ItemStack cyanWool();

    // -----------------------------------------------------------------------
    // Sort dye items (7 colors for sort menus)
    // -----------------------------------------------------------------------
    public abstract ItemStack sortDyeBase(int index);

    // -----------------------------------------------------------------------
    // Player head
    // -----------------------------------------------------------------------
    public abstract ItemStack createPlayerHead();

    // -----------------------------------------------------------------------
    // Version-specific materials
    // -----------------------------------------------------------------------
    public abstract Material writableBookMaterial();
    public abstract Material sortButtonMaterial();
    public abstract Material snowballMaterial();

    // -----------------------------------------------------------------------
    // Material checks
    // -----------------------------------------------------------------------
    /** Returns true if the material is a wool type used for CP display (lime/red wool). */
    public abstract boolean isWoolCpItem(Material mat);
    /** Returns true if the material is a player head. */
    public abstract boolean isPlayerHead(Material mat);

    // -----------------------------------------------------------------------
    // Sounds
    // -----------------------------------------------------------------------
    public abstract Sound soundButtonClick();
    public abstract Sound soundItemPickup();
    public abstract Sound soundExpOrb();

    // -----------------------------------------------------------------------
    // Enchantment
    // -----------------------------------------------------------------------
    public abstract Enchantment luckEnchantment();

    // -----------------------------------------------------------------------
    // Skull owner
    // -----------------------------------------------------------------------
    public abstract void setSkullOwner(SkullMeta meta, OfflinePlayer player);

    // -----------------------------------------------------------------------
    // Item tagging (replaces PersistentDataContainer for old versions)
    // -----------------------------------------------------------------------
    public abstract void markAsPluginItem(ItemMeta meta);
    public abstract boolean isPluginItem(ItemMeta meta);
    public abstract void setTargetUuid(ItemMeta meta, String uuid);
    public abstract String getTargetUuid(ItemMeta meta);

    // -----------------------------------------------------------------------
    // Player helpers
    // -----------------------------------------------------------------------
    public abstract String getPlayerLocale(Player player);
    public abstract boolean isMainHand(PlayerInteractEvent event);
    public abstract boolean isItemAir(ItemStack item);
    public abstract void safePrepareForTeleport(Player player);
}
