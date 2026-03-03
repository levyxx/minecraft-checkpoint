package checkpoint;

import checkpoint.command.CheckpointCommand;
import checkpoint.compat.VersionCompat;
import checkpoint.gui.MenuManager;
import checkpoint.i18n.Messages;
import checkpoint.i18n.Messages.Lang;
import checkpoint.listener.ChatInputListener;
import checkpoint.listener.InventoryClickListener;
import checkpoint.listener.PlayerListener;
import checkpoint.manager.CheckpointManager;
import checkpoint.storage.CheckpointStorage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base plugin entry point. Wires together the manager, GUI, command and listener layers.
 * Version-specific subclass provides the VersionCompat implementation.
 */
public abstract class CheckpointPluginBase extends JavaPlugin {

    private CheckpointManager checkpointManager;
    private MenuManager menuManager;
    private File languagesFile;
    private File checkpointsFile;

    @Override
    public void onEnable() {
        this.checkpointManager = new CheckpointManager();
        this.menuManager = new MenuManager(this, checkpointManager);

        // Load persisted checkpoint data
        this.checkpointsFile = new File(getDataFolder(), "checkpoints.yml");
        CheckpointStorage.load(checkpointsFile, checkpointManager, getLogger());

        // Auto-save on every data change
        checkpointManager.setOnDataChanged(() ->
            CheckpointStorage.save(checkpointsFile, checkpointManager, getLogger())
        );

        Bukkit.getPluginManager().registerEvents(new InventoryClickListener(menuManager), this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(menuManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(menuManager), this);

        this.languagesFile = new File(getDataFolder(), "languages.yml");
        loadLanguagePreferences();

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
        // Final save before shutdown
        if (checkpointManager != null && checkpointsFile != null) {
            CheckpointStorage.save(checkpointsFile, checkpointManager, getLogger());
        }
        menuManager.clearAll();
        Messages.clearAll();
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
        UUID playerId = player.getUniqueId();

        // Detect and set language from client locale if not already set
        initPlayerLang(player);

        ItemStack netherStar = createUtilityItem(
            Material.NETHER_STAR, ChatColor.AQUA, "CheckPoint",
            Arrays.asList(ChatColor.GRAY + Messages.itemNetherStarLoreL(playerId),
                    ChatColor.GRAY + Messages.itemNetherStarLoreR(playerId)));

        ItemStack slimeBall = createUtilityItem(
            Material.SLIME_BALL, ChatColor.GREEN, "Set CheckPoint",
            Arrays.asList(ChatColor.GRAY + Messages.itemSlimeLore(playerId)));

        ItemStack feather = createUtilityItem(
            Material.FEATHER, ChatColor.GOLD, "Change Gamemode",
            Arrays.asList(ChatColor.GRAY + Messages.itemFeatherLore(playerId)));

        ItemStack diamond = createUtilityItem(
            Material.DIAMOND, ChatColor.LIGHT_PURPLE, "CheckPoint List",
            Arrays.asList(ChatColor.GRAY + Messages.itemDiamondLore(playerId)));

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(netherStar, slimeBall, feather, diamond);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(stack ->
                player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }

        player.sendMessage(ChatColor.AQUA + Messages.itemsReceived(playerId));
        player.playSound(player.getLocation(), VersionCompat.get().soundItemPickup(), 0.7f, 1.1f);
    }

    /**
     * Initialize player language from Minecraft client locale if not already set.
     */
    public void initPlayerLang(Player player) {
        UUID playerId = player.getUniqueId();
        if (Messages.isManuallySet(playerId)) {
            Messages.setLang(playerId, Messages.getManualLang(playerId));
        } else {
            Messages.setLang(playerId, Messages.detectLang(VersionCompat.get().getPlayerLocale(player)));
        }
    }

    // -----------------------------------------------------------------------
    // Manual language preference — exposed for CheckpointCommand
    // -----------------------------------------------------------------------

    public void setPlayerLanguageManual(UUID playerId, Lang lang) {
        Messages.setLangManual(playerId, lang);
        saveLanguagePreference(playerId, lang);
    }

    // -----------------------------------------------------------------------
    // Language persistence helpers
    // -----------------------------------------------------------------------

    private void loadLanguagePreferences() {
        if (!languagesFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(languagesFile);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String langStr = cfg.getString(key, "JP");
                Lang lang = "EN".equalsIgnoreCase(langStr) ? Lang.EN : Lang.JP;
                Messages.loadManualLang(uuid, lang);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed keys
            }
        }
    }

    private void saveLanguagePreference(UUID playerId, Lang lang) {
        if (!languagesFile.getParentFile().exists()) {
            languagesFile.getParentFile().mkdirs();
        }
        YamlConfiguration cfg = languagesFile.exists()
            ? YamlConfiguration.loadConfiguration(languagesFile)
            : new YamlConfiguration();
        cfg.set(playerId.toString(), lang.name());
        try {
            cfg.save(languagesFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save language preferences: " + e.getMessage());
        }
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
            VersionCompat.get().markAsPluginItem(meta);
            item.setItemMeta(meta);
        }
        return item;
    }
}
