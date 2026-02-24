package checkpoint.gui;

import checkpoint.i18n.Messages;
import checkpoint.model.Checkpoint;
import checkpoint.model.PlayerSortOrder;
import checkpoint.model.SortOrder;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

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
                                                  boolean selected, boolean isSelf) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + Messages.cpWorld(viewerId) + checkpoint.worldName());
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT,
                "X: %.1f Y: %.1f Z: %.1f", checkpoint.x(), checkpoint.y(), checkpoint.z()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT,
                "Yaw: %.1f Pitch: %.1f", checkpoint.yaw(), checkpoint.pitch()));
            lore.add(ChatColor.GRAY + Messages.cpCreated(viewerId) + formatInstant(checkpoint.createdAt()));
            lore.add(ChatColor.GRAY + Messages.cpUpdated(viewerId) + formatInstant(checkpoint.updatedAt()));
            if (!checkpoint.description().isEmpty()) {
                lore.add("");
                lore.add(ChatColor.WHITE + checkpoint.description());
            }
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
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT,
                "X: %.1f Y: %.1f Z: %.1f",
                checkpoint.x(), checkpoint.y(), checkpoint.z()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT,
                "Yaw: %.1f Pitch: %.1f",
                checkpoint.yaw(), checkpoint.pitch()));
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
    // Player head / operation items
    // -----------------------------------------------------------------------

    public static ItemStack createPlayerHeadItem(UUID viewerId, OfflinePlayer target, boolean isSelf) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            String name = target.getName() != null ? target.getName() : Messages.psUnknown(viewerId);
            meta.setDisplayName(ChatColor.YELLOW +
                (isSelf ? Messages.headSelf(viewerId, name) : Messages.headOther(viewerId, name)));
            meta.setLore(List.of(
                ChatColor.GRAY + Messages.headClickChange(viewerId),
                isSelf
                    ? ChatColor.AQUA + Messages.headViewingSelf(viewerId)
                    : ChatColor.AQUA + Messages.headViewingOther(viewerId, name)
            ));
            head.setItemMeta(meta);
        }
        return head;
    }

    public static ItemStack createOperationWoolItem(UUID viewerId, Material wool, String displayName, String loreText) {
        ItemStack item = new ItemStack(wool);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(List.of(
                ChatColor.GRAY + loreText,
                ChatColor.YELLOW + Messages.opClickExecute(viewerId)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    // -----------------------------------------------------------------------
    // Player select menu items
    // -----------------------------------------------------------------------

    public static ItemStack createPlayerSelectHead(UUID viewerId, OfflinePlayer target, boolean isSelf,
            boolean isViewing, int cpCount, String lastCloneStr, int totalClonedCount,
            double nearestDistance, String lastActivityStr, NamespacedKey uuidKey) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            String name = target.getName() != null ? target.getName() : Messages.psUnknown(viewerId);
            meta.setDisplayName((isSelf ? ChatColor.AQUA : ChatColor.YELLOW) + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + Messages.psCpCount(viewerId) + cpCount);
            lore.add(ChatColor.GRAY + Messages.psLastClone(viewerId) + (lastCloneStr != null ? lastCloneStr : Messages.psNone(viewerId)));
            lore.add(ChatColor.GRAY + Messages.psClonedCount(viewerId) + totalClonedCount);
            String distStr = nearestDistance >= 1_000_000 ? "?" : String.format(Locale.ROOT, "%.0f", nearestDistance);
            lore.add(ChatColor.GRAY + Messages.psNearestDist(viewerId) + distStr + Messages.psBlocks(viewerId));
            lore.add(ChatColor.GRAY + Messages.psLastActivity(viewerId) + (lastActivityStr != null ? lastActivityStr : Messages.psNone(viewerId)));
            lore.add("");
            if (isViewing) {
                lore.add(ChatColor.GREEN + Messages.psCurrentlyViewing(viewerId));
                meta.addEnchant(Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(ChatColor.YELLOW + Messages.psClickToView(viewerId));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer()
                .set(uuidKey, PersistentDataType.STRING, target.getUniqueId().toString());
            head.setItemMeta(meta);
        }
        return head;
    }

    public static ItemStack createPlayerSearchItem(UUID viewerId) {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + Messages.playerSearchName(viewerId));
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

    public static ItemStack createPlayerInfoItem(UUID viewerId, int currentPage, int totalPages, int totalPlayers,
                                                  PlayerSortOrder order, String query) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + Messages.infoTitle(viewerId));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + Messages.navPage(viewerId) + currentPage + " / " + totalPages);
            lore.add(ChatColor.GRAY + Messages.psPlayerCount(viewerId) + totalPlayers);
            lore.add(ChatColor.GRAY + Messages.infoSort(viewerId) + ChatColor.AQUA + Messages.playerSortOrderLabel(viewerId, order));
            if (query != null && !query.isBlank()) {
                lore.add(ChatColor.GRAY + Messages.infoSearch(viewerId) + ChatColor.YELLOW + query);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createPlayerSortButtonItem(UUID viewerId, PlayerSortOrder current) {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + Messages.sortChange(viewerId));
            meta.setLore(List.of(
                ChatColor.GRAY + Messages.sortCurrent(viewerId) + ChatColor.YELLOW + Messages.playerSortOrderLabel(viewerId, current),
                ChatColor.YELLOW + Messages.sortOpenSelect(viewerId)
            ));
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
