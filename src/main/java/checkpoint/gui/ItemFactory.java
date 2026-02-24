package checkpoint.gui;

import checkpoint.model.Checkpoint;
import checkpoint.model.SortOrder;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import static checkpoint.gui.GuiConstants.CP_DATE_FMT;

/**
 * Factory for creating all GUI ItemStacks.
 */
public final class ItemFactory {

    private ItemFactory() {}

    // -----------------------------------------------------------------------
    // Checkpoint papers
    // -----------------------------------------------------------------------

    public static ItemStack createCheckpointPaper(String name, Checkpoint checkpoint,
                                                  boolean selected, boolean isSelf) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ワールド: " + checkpoint.worldName());
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT,
                "X: %.1f Y: %.1f Z: %.1f", checkpoint.x(), checkpoint.y(), checkpoint.z()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT,
                "Yaw: %.1f Pitch: %.1f", checkpoint.yaw(), checkpoint.pitch()));
            lore.add(ChatColor.GRAY + "作成: " + formatInstant(checkpoint.createdAt()));
            lore.add(ChatColor.GRAY + "更新: " + formatInstant(checkpoint.updatedAt()));
            if (!checkpoint.description().isEmpty()) {
                lore.add("");
                lore.add(ChatColor.WHITE + checkpoint.description());
            }
            lore.add("");
            if (isSelf) {
                lore.add(selected ? ChatColor.AQUA + "現在選択中"
                    : ChatColor.YELLOW + "左クリックで選択 / 右クリックで操作");
            } else {
                lore.add(ChatColor.YELLOW + "左クリックでテレポート / 右クリックで操作");
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

    public static ItemStack createCheckpointPaperInfo(String name, Checkpoint checkpoint) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ワールド: " + checkpoint.worldName());
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT,
                "X: %.1f Y: %.1f Z: %.1f",
                checkpoint.x(), checkpoint.y(), checkpoint.z()));
            lore.add(ChatColor.GRAY + String.format(Locale.ROOT,
                "Yaw: %.1f Pitch: %.1f",
                checkpoint.yaw(), checkpoint.pitch()));
            lore.add(ChatColor.GRAY + "作成: " + formatInstant(checkpoint.createdAt()));
            lore.add(ChatColor.GRAY + "更新: " + formatInstant(checkpoint.updatedAt()));
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

    public static ItemStack createNavItem(boolean forward, int currentPage, int totalPages) {
        ItemStack item = new ItemStack(forward ? Material.SPECTRAL_ARROW : Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(forward ? ChatColor.GREEN + "次のページ" : ChatColor.GREEN + "前のページ");
            int targetPage = forward ? currentPage + 2 : currentPage;
            meta.setLore(List.of(
                ChatColor.GRAY + "ページ: " + targetPage + " / " + totalPages,
                ChatColor.YELLOW + "左クリックで移動"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createDisabledNavItem(String label) {
        ItemStack item = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(label);
            meta.setLore(List.of(ChatColor.DARK_GRAY + "ページ移動はできません"));
            item.setItemMeta(meta);
        }
        return item;
    }

    // -----------------------------------------------------------------------
    // Info / Sort / Search items
    // -----------------------------------------------------------------------

    public static ItemStack createInfoItem(int currentPage, int totalPages, int totalCheckpoints,
                                           SortOrder order, String query) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "ページ情報");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ページ: " + currentPage + " / " + totalPages);
            lore.add(ChatColor.GRAY + "表示数: " + totalCheckpoints);
            lore.add(ChatColor.GRAY + "ソート: " + ChatColor.AQUA + order.label);
            if (query != null && !query.isBlank()) {
                lore.add(ChatColor.GRAY + "検索: " + ChatColor.YELLOW + query);
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "紙を左クリックで選択");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createSortButtonItem(SortOrder current) {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "ソート方法を変更");
            meta.setLore(List.of(
                ChatColor.GRAY + "現在: " + ChatColor.YELLOW + current.label,
                ChatColor.YELLOW + "左クリックで選択画面を開く"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createAnvilSearchItem() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + "CP名を検索");
            meta.setLore(List.of(
                ChatColor.GRAY + "左クリックで検索バーを開く",
                ChatColor.GRAY + "右クリックで検索を解除",
                ChatColor.GRAY + "部分一致でフィルタリングします"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createSortDyeItem(Material dye, SortOrder order, boolean active) {
        ItemStack item = new ItemStack(dye);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((active ? ChatColor.AQUA : ChatColor.WHITE) + order.label);
            List<String> lore = new ArrayList<>();
            if (active) {
                lore.add(ChatColor.GREEN + "✔ 現在選択中");
                meta.addEnchant(Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(ChatColor.YELLOW + "左クリックで選択");
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
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

    public static ItemStack createEmptyNoticeItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "チェックポイントがありません");
            meta.setLore(List.of(
                ChatColor.GRAY + "コマンド /cp set <名前> で登録できます",
                ChatColor.GRAY + "登録後に海洋の心をクリックすると表示されます"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    // -----------------------------------------------------------------------
    // Player head / operation items
    // -----------------------------------------------------------------------

    public static ItemStack createPlayerHeadItem(OfflinePlayer target, boolean isSelf) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            String name = target.getName() != null ? target.getName() : "不明";
            meta.setDisplayName(ChatColor.YELLOW +
                (isSelf ? "自分（" + name + "）" : name + " のCP"));
            meta.setLore(List.of(
                ChatColor.GRAY + "クリックでプレイヤーを変更",
                isSelf
                    ? ChatColor.AQUA + "現在: 自分のCPを表示中"
                    : ChatColor.AQUA + "現在: " + name + " のCPを表示中"
            ));
            head.setItemMeta(meta);
        }
        return head;
    }

    public static ItemStack createOperationWoolItem(Material wool, String displayName, String loreText) {
        ItemStack item = new ItemStack(wool);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(List.of(
                ChatColor.GRAY + loreText,
                ChatColor.YELLOW + "クリックで実行"
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
