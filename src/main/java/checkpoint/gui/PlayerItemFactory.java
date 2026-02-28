package checkpoint.gui;

import checkpoint.i18n.Messages;
import checkpoint.model.PlayerSortOrder;
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

/**
 * Factory for creating player-related GUI ItemStacks (player heads,
 * player select items, operation items).
 * Extracted from {@link ItemFactory} for readability.
 */
public final class PlayerItemFactory {

    private PlayerItemFactory() {}

    // -----------------------------------------------------------------------
    // Player head in CP list
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

    // -----------------------------------------------------------------------
    // Operation wool items
    // -----------------------------------------------------------------------

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
}
