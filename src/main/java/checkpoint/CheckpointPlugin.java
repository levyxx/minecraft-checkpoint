package checkpoint;

import checkpoint.command.CheckpointCommand;
import checkpoint.gui.MenuManager;
import checkpoint.listener.ChatInputListener;
import checkpoint.listener.InventoryClickListener;
import checkpoint.listener.PlayerListener;
import checkpoint.manager.CheckpointManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point. Wires together the manager, GUI, command and listener layers.
 */
public class CheckpointPlugin extends JavaPlugin {

    private CheckpointManager checkpointManager;
    private MenuManager menuManager;
    private NamespacedKey cpItemKey;

    @Override
    public void onEnable() {
        this.checkpointManager = new CheckpointManager();
        this.cpItemKey = new NamespacedKey(this, "cp_utility_item");
        this.menuManager = new MenuManager(this, checkpointManager);

        Bukkit.getPluginManager().registerEvents(new InventoryClickListener(menuManager), this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(menuManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(menuManager, cpItemKey), this);

        PluginCommand cpCommand = getCommand("cp");
        if (cpCommand != null) {
            CheckpointCommand executor = new CheckpointCommand(this, checkpointManager);
            cpCommand.setExecutor(executor);
            cpCommand.setTabCompleter(executor);
        } else {
            getLogger().severe("Command /cp is not defined in plugin.yml");
        }

        getLogger().info("Checkpoint plugin enabled.");
    }

    @Override
    public void onDisable() {
        menuManager.clearAll();
        this.checkpointManager = null;
        getLogger().info("Checkpoint plugin disabled.");
    }

    // -----------------------------------------------------------------------
    // Notification methods (called from CheckpointCommand)
    // -----------------------------------------------------------------------

    public void notifyNamedCheckpointSet(Player player, String rawName) {
        menuManager.notifyNamedCheckpointSet(player, rawName);
    }

    public void notifyNamedCheckpointDeleted(UUID playerId, String rawName) {
        menuManager.notifyNamedCheckpointDeleted(playerId, rawName);
    }

    // -----------------------------------------------------------------------
    // Give utility items (called from CheckpointCommand)
    // -----------------------------------------------------------------------

    public void giveCheckpointItems(Player player) {
        ItemStack netherStar = createUtilityItem(
            Material.NETHER_STAR, ChatColor.AQUA, "CheckPoint",
            List.of(ChatColor.GRAY + "左クリック: チェックポイント一覧",
                    ChatColor.GRAY + "右クリック: テレポート"));

        ItemStack slimeBall = createUtilityItem(
            Material.SLIME_BALL, ChatColor.GREEN, "Set CheckPoint",
            List.of(ChatColor.GRAY + "右クリック: 現在地を保存"));

        ItemStack feather = createUtilityItem(
            Material.FEATHER, ChatColor.GOLD, "Change Gamemode",
            List.of(ChatColor.GRAY + "右クリック: クリエ/アドベンチャー切替"));

        ItemStack heart = createUtilityItem(
            Material.HEART_OF_THE_SEA, ChatColor.LIGHT_PURPLE, "CheckPoint List",
            List.of(ChatColor.GRAY + "右クリック: チェックポイント一覧"));

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(netherStar, slimeBall, feather, heart);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(stack ->
                player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }

        player.sendMessage(ChatColor.AQUA + "チェックポイントアイテムを受け取りました。所持品を確認してください。");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.1f);
    }

    private ItemStack createUtilityItem(Material material, ChatColor color,
                                        String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + displayName);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(cpItemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }
}
