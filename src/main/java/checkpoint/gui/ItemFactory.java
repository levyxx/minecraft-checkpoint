package checkpoint.gui;

import checkpoint.i18n.Messages;
import checkpoint.model.Checkpoint;
import checkpoint.model.ClearSortOrder;
import checkpoint.model.SortOrder;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import static checkpoint.gui.GuiConstants.CP_DATE_FMT;

/**
 * Factory for creating all GUI ItemStacks.
 */
public final class ItemFactory {

    private ItemFactory() {}

    // -----------------------------------------------------------------------
    // Checkpoint papers
    // -----------------------------------------------------------------------

    public static ItemStack createCheckpointPaper(UUID viewerId, String name, Checkpoint checkpoint,
                                                  boolean selected, boolean isSelf, boolean cleared) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + Messages.cpWorld(viewerId) + checkpoint.worldName());
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "X: %.5f", checkpoint.x()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "Y: %.5f", checkpoint.y()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "Z: %.5f", checkpoint.z()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "F: %.5f", checkpoint.yaw()));
            lore.add(ChatColor.GRAY + Messages.cpCreated(viewerId) + formatInstant(checkpoint.createdAt()));
            lore.add(ChatColor.GRAY + Messages.cpUpdated(viewerId) + formatInstant(checkpoint.updatedAt()));
            if (!checkpoint.description().isEmpty()) {
                lore.add("");
                lore.add(ChatColor.WHITE + checkpoint.description());
            }
            lore.add("");
            lore.add(cleared
                ? ChatColor.GREEN + Messages.clearStatusCleared(viewerId)
                : ChatColor.RED + Messages.clearStatusNotCleared(viewerId));
            lore.add("");
            if (isSelf) {
                lore.add(selected ? ChatColor.AQUA + Messages.cpCurrentSelection(viewerId)
                    : ChatColor.YELLOW + Messages.cpLeftSelectRight(viewerId));
            } else {
                lore.add(ChatColor.YELLOW + Messages.cpLeftTpRight(viewerId));
            }
            meta.setLore(lore);
            if (selected) {
                meta.addEnchant(Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            paper.setItemMeta(meta);
        }
        return paper;
    }

    public static ItemStack createCheckpointPaperInfo(UUID viewerId, String name, Checkpoint checkpoint) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + Messages.cpWorld(viewerId) + checkpoint.worldName());
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "X: %.5f", checkpoint.x()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "Y: %.5f", checkpoint.y()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "Z: %.5f", checkpoint.z()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "F: %.5f", checkpoint.yaw()));
            lore.add(ChatColor.GRAY + Messages.cpCreated(viewerId) + formatInstant(checkpoint.createdAt()));
            lore.add(ChatColor.GRAY + Messages.cpUpdated(viewerId) + formatInstant(checkpoint.updatedAt()));
            if (!checkpoint.description().isEmpty()) {
                lore.add("");
                lore.add(ChatColor.WHITE + checkpoint.description());
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            paper.setItemMeta(meta);
        }
        return paper;
    }

    // -----------------------------------------------------------------------
    // Navigation items
    // -----------------------------------------------------------------------

    public static ItemStack createNavItem(UUID viewerId, boolean forward, int currentPage, int totalPages) {
        ItemStack item = new ItemStack(forward ? Material.SPECTRAL_ARROW : Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(forward ? ChatColor.GREEN + Messages.navNext(viewerId) : ChatColor.GREEN + Messages.navPrevious(viewerId));
            int targetPage = forward ? currentPage + 2 : currentPage;
            meta.setLore(List.of(
                ChatColor.GRAY + Messages.navPage(viewerId) + targetPage + " / " + totalPages,
                ChatColor.YELLOW + Messages.navLeftClickMove(viewerId)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createDisabledNavItem(UUID viewerId, boolean forward) {
        ItemStack item = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_GRAY + (forward ? Messages.navNoNext(viewerId) : Messages.navNoPrev(viewerId)));
            meta.setLore(List.of(ChatColor.DARK_GRAY + Messages.navNoMove(viewerId)));
            item.setItemMeta(meta);
        }
        return item;
    }

    // -----------------------------------------------------------------------
    // Info / Sort / Search items
    // -----------------------------------------------------------------------

    public static ItemStack createInfoItem(UUID viewerId, int currentPage, int totalPages, int totalCheckpoints,
                                           SortOrder order, String query) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + Messages.infoTitle(viewerId));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + Messages.navPage(viewerId) + currentPage + " / " + totalPages);
            lore.add(ChatColor.GRAY + Messages.infoCount(viewerId) + totalCheckpoints);
            lore.add(ChatColor.GRAY + Messages.infoSort(viewerId) + ChatColor.AQUA + Messages.sortOrderLabel(viewerId, order));
            if (query != null && !query.isBlank()) {
                lore.add(ChatColor.GRAY + Messages.infoSearch(viewerId) + ChatColor.YELLOW + query);
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + Messages.infoClickHint(viewerId));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createSortButtonItem(UUID viewerId, SortOrder current) {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + Messages.sortChange(viewerId));
            meta.setLore(List.of(
                ChatColor.GRAY + Messages.sortCurrent(viewerId) + ChatColor.YELLOW + Messages.sortOrderLabel(viewerId, current),
                ChatColor.YELLOW + Messages.sortOpenSelect(viewerId)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createAnvilSearchItem(UUID viewerId) {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + Messages.searchCpName(viewerId));
            meta.setLore(List.of(
                ChatColor.GRAY + Messages.searchOpen(viewerId),
                ChatColor.GRAY + Messages.searchClear(viewerId),
                ChatColor.GRAY + Messages.searchPartial(viewerId)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createSortDyeItem(UUID viewerId, Material dye, String label, boolean active) {
        ItemStack item = new ItemStack(dye);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((active ? ChatColor.AQUA : ChatColor.WHITE) + label);
            List<String> lore = new ArrayList<>();
            if (active) {
                lore.add(ChatColor.GREEN + Messages.sortCurrentMark(viewerId));
                meta.addEnchant(Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(ChatColor.YELLOW + Messages.sortLeftClick(viewerId));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createSortDyeItem(UUID viewerId, Material dye, SortOrder order, boolean active) {
        return createSortDyeItem(viewerId, dye, Messages.sortOrderLabel(viewerId, order), active);
    }

    // -----------------------------------------------------------------------
    // Decorative / utility items
    // -----------------------------------------------------------------------

    public static ItemStack createGlassDeco(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createEmptyNoticeItem(UUID viewerId) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + Messages.emptyTitle(viewerId));
            meta.setLore(List.of(
                ChatColor.GRAY + Messages.emptyHint1(viewerId),
                ChatColor.GRAY + Messages.emptyHint2(viewerId)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }



    // -----------------------------------------------------------------------
    // Display mode toggle & clear sort items
    // -----------------------------------------------------------------------

    /** Creates the display mode toggle button (snowball or magma cream). */
    public static ItemStack createDisplayModeToggle(UUID viewerId, boolean woolMode) {
        Material mat = woolMode ? Material.MAGMA_CREAM : Material.SNOWBALL;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + (woolMode
                ? Messages.displayModeWool(viewerId)
                : Messages.displayModePaper(viewerId)));
            meta.setLore(List.of(
                ChatColor.YELLOW + (woolMode
                    ? Messages.displayModeClickToPaper(viewerId)
                    : Messages.displayModeClickToWool(viewerId))
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Creates the clear sort button (blaze powder). */
    public static ItemStack createClearSortButton(UUID viewerId, ClearSortOrder current) {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + Messages.clearSortButton(viewerId));
            meta.setLore(List.of(
                ChatColor.GRAY + Messages.clearSortCurrent(viewerId)
                    + ChatColor.YELLOW + Messages.clearSortOrderLabel(viewerId, current),
                ChatColor.YELLOW + Messages.clearSortLeftClick(viewerId),
                ChatColor.GRAY + Messages.clearSortRightClick(viewerId)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Creates a CP item displayed as wool (lime=cleared, red=not cleared). */
    public static ItemStack createCheckpointWool(UUID viewerId, String name, Checkpoint checkpoint,
                                                  boolean selected, boolean isSelf, boolean cleared) {
        Material wool = cleared ? Material.LIME_WOOL : Material.RED_WOOL;
        ItemStack item = new ItemStack(wool);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + Messages.cpWorld(viewerId) + checkpoint.worldName());
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "X: %.5f", checkpoint.x()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "Y: %.5f", checkpoint.y()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "Z: %.5f", checkpoint.z()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT, "F: %.5f", checkpoint.yaw()));
            lore.add(ChatColor.GRAY + Messages.cpCreated(viewerId) + formatInstant(checkpoint.createdAt()));
            lore.add(ChatColor.GRAY + Messages.cpUpdated(viewerId) + formatInstant(checkpoint.updatedAt()));
            if (!checkpoint.description().isEmpty()) {
                lore.add("");
                lore.add(ChatColor.WHITE + checkpoint.description());
            }
            lore.add("");
            lore.add(cleared
                ? ChatColor.GREEN + Messages.clearStatusCleared(viewerId)
                : ChatColor.RED + Messages.clearStatusNotCleared(viewerId));
            lore.add("");
            if (isSelf) {
                lore.add(selected ? ChatColor.AQUA + Messages.cpCurrentSelection(viewerId)
                    : ChatColor.YELLOW + Messages.cpLeftSelectRight(viewerId));
            } else {
                lore.add(ChatColor.YELLOW + Messages.cpLeftTpRight(viewerId));
            }
            meta.setLore(lore);
            if (selected) {
                meta.addEnchant(Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Creates a clear sort option item (lime or red wool) for the clear sort selection menu. */
    public static ItemStack createClearSortOption(UUID viewerId, ClearSortOrder order, boolean active) {
        Material wool = (order == ClearSortOrder.CLEARED_FIRST) ? Material.LIME_WOOL : Material.RED_WOOL;
        ItemStack item = new ItemStack(wool);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String label = Messages.clearSortOrderLabel(viewerId, order);
            meta.setDisplayName((active ? ChatColor.AQUA : ChatColor.WHITE) + label);
            List<String> lore = new ArrayList<>();
            if (active) {
                lore.add(ChatColor.GREEN + Messages.sortCurrentMark(viewerId));
                meta.addEnchant(Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(ChatColor.YELLOW + Messages.sortLeftClick(viewerId));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    static String formatInstant(java.time.Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).format(CP_DATE_FMT);
    }
}
