package checkpoint.storage;

import checkpoint.manager.CheckpointManager;
import checkpoint.model.Checkpoint;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Handles reading and writing checkpoint data to a YAML file.
 * <p>
 * File format:
 * <pre>
 * quick:
 *   "uuid": { world, x, y, z, yaw, pitch, createdAt, updatedAt, description }
 * named:
 *   "uuid":
 *     - { name, world, x, y, z, yaw, pitch, createdAt, updatedAt, description }
 * selected:
 *   "uuid": "checkpointName"
 * cloneHistory:
 *   "clonerUuid":
 *     - { source: "sourceUuid", time: epochMillis }
 * clonedCounts:
 *   "uuid": count
 * </pre>
 */
public final class CheckpointStorage {

    private CheckpointStorage() {}

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    /**
     * Saves all checkpoint data from the given manager to a YAML file.
     */
    public static void save(File file, CheckpointManager manager, Logger logger) {
        YamlConfiguration config = new YamlConfiguration();

        Map<UUID, Checkpoint> quickCps = manager.getAllQuickCheckpoints();
        Map<UUID, Map<String, Checkpoint>> namedCps = manager.getAllNamedCheckpoints();
        Map<UUID, String> selected = manager.getAllSelectedCheckpoints();
        Map<UUID, Map<UUID, Instant>> clones = manager.getAllCloneHistory();
        Map<UUID, Integer> counts = manager.getAllClonedCounts();
        Map<UUID, Set<String>> cleared = manager.getAllClearedCheckpoints();

        // --- Quick checkpoints ---
        for (var entry : quickCps.entrySet()) {
            String path = "quick." + entry.getKey().toString();
            saveCheckpoint(config, path, entry.getValue());
        }

        // --- Named checkpoints (list format to avoid YAML path-separator issues) ---
        for (var entry : namedCps.entrySet()) {
            String path = "named." + entry.getKey().toString();
            List<Map<String, Object>> cpList = new ArrayList<>();
            for (var cpEntry : entry.getValue().entrySet()) {
                Map<String, Object> cpMap = serializeCheckpoint(cpEntry.getKey(), cpEntry.getValue());
                cpList.add(cpMap);
            }
            config.set(path, cpList);
        }

        // --- Selected ---
        for (var entry : selected.entrySet()) {
            config.set("selected." + entry.getKey().toString(), entry.getValue());
        }

        // --- Clone history ---
        for (var entry : clones.entrySet()) {
            String path = "cloneHistory." + entry.getKey().toString();
            List<Map<String, Object>> list = new ArrayList<>();
            for (var cloneEntry : entry.getValue().entrySet()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("source", cloneEntry.getKey().toString());
                map.put("time", cloneEntry.getValue().toEpochMilli());
                list.add(map);
            }
            config.set(path, list);
        }

        // --- Cloned counts ---
        for (var entry : counts.entrySet()) {
            config.set("clonedCounts." + entry.getKey().toString(), entry.getValue());
        }

        // --- Cleared checkpoints ---
        for (var entry : cleared.entrySet()) {
            config.set("cleared." + entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }

        // Write to disk
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            logger.warning("Failed to save checkpoint data: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    /**
     * Loads checkpoint data from a YAML file and populates the given manager.
     * If the file does not exist, this is a no-op.
     */
    public static void load(File file, CheckpointManager manager, Logger logger) {
        if (!file.exists()) {
            logger.info("No checkpoint data file found; starting fresh.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        Map<UUID, Checkpoint> quickCps = new HashMap<>();
        Map<UUID, Map<String, Checkpoint>> namedCps = new HashMap<>();
        Map<UUID, String> selected = new HashMap<>();
        Map<UUID, Map<UUID, Instant>> clones = new HashMap<>();
        Map<UUID, Integer> counts = new HashMap<>();
        Map<UUID, Set<String>> cleared = new HashMap<>();

        // --- Quick checkpoints ---
        ConfigurationSection quickSection = config.getConfigurationSection("quick");
        if (quickSection != null) {
            for (String uuidStr : quickSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    Checkpoint cp = loadCheckpoint(quickSection.getConfigurationSection(uuidStr));
                    if (cp != null) quickCps.put(playerId, cp);
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping invalid quick checkpoint UUID: " + uuidStr);
                }
            }
        }

        // --- Named checkpoints ---
        ConfigurationSection namedSection = config.getConfigurationSection("named");
        if (namedSection != null) {
            for (String uuidStr : namedSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    List<?> cpList = namedSection.getList(uuidStr);
                    if (cpList != null) {
                        Map<String, Checkpoint> playerMap = new HashMap<>();
                        for (Object item : cpList) {
                            if (item instanceof Map<?, ?> map) {
                                Checkpoint cp = deserializeCheckpoint(map);
                                String name = String.valueOf(map.get("name"));
                                if (cp != null && name != null && !name.isBlank()) {
                                    playerMap.put(name, cp);
                                }
                            }
                        }
                        if (!playerMap.isEmpty()) {
                            namedCps.put(playerId, playerMap);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping invalid named checkpoint UUID: " + uuidStr);
                }
            }
        }

        // --- Selected ---
        ConfigurationSection selectedSection = config.getConfigurationSection("selected");
        if (selectedSection != null) {
            for (String uuidStr : selectedSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    String name = selectedSection.getString(uuidStr);
                    if (name != null && !name.isBlank()) {
                        selected.put(playerId, name);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping invalid selected checkpoint UUID: " + uuidStr);
                }
            }
        }

        // --- Clone history ---
        ConfigurationSection cloneSection = config.getConfigurationSection("cloneHistory");
        if (cloneSection != null) {
            for (String clonerStr : cloneSection.getKeys(false)) {
                try {
                    UUID clonerId = UUID.fromString(clonerStr);
                    List<?> list = cloneSection.getList(clonerStr);
                    if (list != null) {
                        Map<UUID, Instant> playerClones = new HashMap<>();
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> map) {
                                String sourceStr = String.valueOf(map.get("source"));
                                Object timeObj = map.get("time");
                                if (sourceStr != null && timeObj != null) {
                                    try {
                                        UUID sourceId = UUID.fromString(sourceStr);
                                        long millis = ((Number) timeObj).longValue();
                                        playerClones.put(sourceId, Instant.ofEpochMilli(millis));
                                    } catch (Exception ignored) {
                                        // Skip malformed entries
                                    }
                                }
                            }
                        }
                        if (!playerClones.isEmpty()) {
                            clones.put(clonerId, playerClones);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping invalid clone history UUID: " + clonerStr);
                }
            }
        }

        // --- Cloned counts ---
        ConfigurationSection countsSection = config.getConfigurationSection("clonedCounts");
        if (countsSection != null) {
            for (String uuidStr : countsSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    int count = countsSection.getInt(uuidStr, 0);
                    if (count > 0) counts.put(playerId, count);
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping invalid clonedCounts UUID: " + uuidStr);
                }
            }
        }

        // --- Cleared checkpoints ---
        ConfigurationSection clearedSection = config.getConfigurationSection("cleared");
        if (clearedSection != null) {
            for (String uuidStr : clearedSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    List<?> list = clearedSection.getList(uuidStr);
                    if (list != null) {
                        Set<String> names = new HashSet<>();
                        for (Object item : list) {
                            if (item != null) names.add(String.valueOf(item));
                        }
                        if (!names.isEmpty()) cleared.put(playerId, names);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping invalid cleared UUID: " + uuidStr);
                }
            }
        }

        manager.loadData(quickCps, namedCps, selected, clones, counts, cleared);

        int totalNamed = namedCps.values().stream().mapToInt(Map::size).sum();
        int totalCleared = cleared.values().stream().mapToInt(Set::size).sum();
        logger.info("Loaded checkpoint data: " + quickCps.size() + " quick, "
                + totalNamed + " named, "
                + selected.size() + " selected, "
                + clones.size() + " clone histories, "
                + counts.size() + " cloned counts, "
                + totalCleared + " cleared.");
    }

    // -----------------------------------------------------------------------
    // Serialization helpers
    // -----------------------------------------------------------------------

    private static void saveCheckpoint(YamlConfiguration config, String path, Checkpoint cp) {
        config.set(path + ".world", cp.worldName());
        config.set(path + ".x", cp.x());
        config.set(path + ".y", cp.y());
        config.set(path + ".z", cp.z());
        config.set(path + ".yaw", (double) cp.yaw());
        config.set(path + ".pitch", (double) cp.pitch());
        config.set(path + ".createdAt", cp.createdAt().toEpochMilli());
        config.set(path + ".updatedAt", cp.updatedAt().toEpochMilli());
        config.set(path + ".description", cp.description());
    }

    private static Map<String, Object> serializeCheckpoint(String name, Checkpoint cp) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("world", cp.worldName());
        map.put("x", cp.x());
        map.put("y", cp.y());
        map.put("z", cp.z());
        map.put("yaw", (double) cp.yaw());
        map.put("pitch", (double) cp.pitch());
        map.put("createdAt", cp.createdAt().toEpochMilli());
        map.put("updatedAt", cp.updatedAt().toEpochMilli());
        map.put("description", cp.description());
        return map;
    }

    private static Checkpoint loadCheckpoint(ConfigurationSection section) {
        if (section == null) return null;
        try {
            String world = section.getString("world");
            if (world == null || world.isBlank()) return null;
            double x = section.getDouble("x");
            double y = section.getDouble("y");
            double z = section.getDouble("z");
            float yaw = (float) section.getDouble("yaw");
            float pitch = (float) section.getDouble("pitch");
            long createdMs = section.getLong("createdAt", Instant.now().toEpochMilli());
            long updatedMs = section.getLong("updatedAt", createdMs);
            String description = section.getString("description", "");
            return new Checkpoint(world, x, y, z, yaw, pitch,
                    Instant.ofEpochMilli(createdMs), Instant.ofEpochMilli(updatedMs), description);
        } catch (Exception e) {
            return null;
        }
    }

    private static Checkpoint deserializeCheckpoint(Map<?, ?> map) {
        try {
            String world = String.valueOf(map.get("world"));
            if (world == null || world.isBlank() || "null".equals(world)) return null;
            double x = toDouble(map.get("x"));
            double y = toDouble(map.get("y"));
            double z = toDouble(map.get("z"));
            float yaw = (float) toDouble(map.get("yaw"));
            float pitch = (float) toDouble(map.get("pitch"));
            long createdMs = toLong(map.get("createdAt"), Instant.now().toEpochMilli());
            long updatedMs = toLong(map.get("updatedAt"), createdMs);
            String description = map.get("description") != null ? String.valueOf(map.get("description")) : "";
            return new Checkpoint(world, x, y, z, yaw, pitch,
                    Instant.ofEpochMilli(createdMs), Instant.ofEpochMilli(updatedMs), description);
        } catch (Exception e) {
            return null;
        }
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(obj));
    }

    private static long toLong(Object obj, long defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(obj)); } catch (NumberFormatException e) { return defaultValue; }
    }
}
