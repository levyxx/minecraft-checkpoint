package checkpoint.compat;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.OfflinePlayer;

/**
 * Shared compatibility layer for pre-1.13 (legacy) Minecraft versions (1.8.x, 1.12.x).
 * Provides colored materials using data values, lore-based item tagging, and old skull API.
 */
@SuppressWarnings("deprecation")
public abstract class CompatLegacy extends VersionCompat {

    // -----------------------------------------------------------------------
    // Glass panes: STAINED_GLASS_PANE with data values
    // -----------------------------------------------------------------------
    @Override public ItemStack cyanGlassPane()      { return legacy("STAINED_GLASS_PANE", 9); }
    @Override public ItemStack lightBlueGlassPane()  { return legacy("STAINED_GLASS_PANE", 3); }
    @Override public ItemStack blueGlassPane()        { return legacy("STAINED_GLASS_PANE", 11); }

    // -----------------------------------------------------------------------
    // Wool: WOOL with data values
    // -----------------------------------------------------------------------
    @Override public ItemStack limeWool()   { return legacy("WOOL", 5); }
    @Override public ItemStack redWool()    { return legacy("WOOL", 14); }
    @Override public ItemStack greenWool()  { return legacy("WOOL", 13); }
    @Override public ItemStack blueWool()   { return legacy("WOOL", 11); }
    @Override public ItemStack yellowWool() { return legacy("WOOL", 4); }
    @Override public ItemStack orangeWool() { return legacy("WOOL", 1); }
    @Override public ItemStack cyanWool()   { return legacy("WOOL", 9); }

    // -----------------------------------------------------------------------
    // Dyes: INK_SACK with data values
    //   Index: 0=lime(10) 1=green(2) 2=cyan(6) 3=blue(4) 4=lightblue(12) 5=purple(5) 6=yellow(11)
    // -----------------------------------------------------------------------
    private static final short[] DYE_DATA = {10, 2, 6, 4, 12, 5, 11};
    @Override public ItemStack sortDyeBase(int index) {
        return legacy("INK_SACK", DYE_DATA[index]);
    }

    // -----------------------------------------------------------------------
    // Player head: SKULL_ITEM with data value 3
    // -----------------------------------------------------------------------
    @Override public ItemStack createPlayerHead() { return legacy("SKULL_ITEM", 3); }

    // -----------------------------------------------------------------------
    // Version-specific materials
    // -----------------------------------------------------------------------
    @Override public Material writableBookMaterial() { return Material.valueOf("BOOK_AND_QUILL"); }
    @Override public Material sortButtonMaterial()   { return Material.COMPASS; }
    @Override public Material snowballMaterial()     { return Material.valueOf("SNOW_BALL"); }

    // -----------------------------------------------------------------------
    // Material checks
    // -----------------------------------------------------------------------
    @Override public boolean isWoolCpItem(Material mat) {
        return mat.name().equals("WOOL");
    }
    @Override public boolean isPlayerHead(Material mat) {
        return mat.name().equals("SKULL_ITEM");
    }

    // -----------------------------------------------------------------------
    // Enchantment
    // -----------------------------------------------------------------------
    @Override public Enchantment luckEnchantment() {
        return Enchantment.LUCK;
    }

    // -----------------------------------------------------------------------
    // Skull owner (old API: setOwner(String))
    // -----------------------------------------------------------------------
    @Override public void setSkullOwner(SkullMeta meta, OfflinePlayer player) {
        meta.setOwner(player.getName());
    }

    // -----------------------------------------------------------------------
    // Item tagging via lore markers (no PersistentDataContainer)
    // -----------------------------------------------------------------------
    private static final String PLUGIN_MARKER = ChatColor.BLACK.toString() + ChatColor.MAGIC + "cpi";
    private static final String UUID_PREFIX   = ChatColor.BLACK.toString() + ChatColor.MAGIC + "u:";

    @Override public void markAsPluginItem(ItemMeta meta) {
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        if (!lore.contains(PLUGIN_MARKER)) {
            lore.add(PLUGIN_MARKER);
            meta.setLore(lore);
        }
    }

    @Override public boolean isPluginItem(ItemMeta meta) {
        if (meta == null) return false;
        List<String> lore = meta.getLore();
        if (lore == null) return false;
        return lore.contains(PLUGIN_MARKER);
    }

    @Override public void setTargetUuid(ItemMeta meta, String uuid) {
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(UUID_PREFIX + uuid);
        meta.setLore(lore);
    }

    @Override public String getTargetUuid(ItemMeta meta) {
        List<String> lore = meta.getLore();
        if (lore == null) return null;
        for (String line : lore) {
            if (line.startsWith(UUID_PREFIX)) {
                return line.substring(UUID_PREFIX.length());
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // isItemAir (no Material.isAir() in legacy)
    // -----------------------------------------------------------------------
    @Override public boolean isItemAir(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    // -----------------------------------------------------------------------
    // Helper: create ItemStack with legacy data value
    // -----------------------------------------------------------------------
    protected static ItemStack legacy(String materialName, int data) {
        return new ItemStack(Material.valueOf(materialName), 1, (short) data);
    }
}
