package checkpoint.manager;

import checkpoint.model.Checkpoint;
import checkpoint.model.RenameResult;
import checkpoint.model.SortOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of per-player checkpoints in memory. Supports quick checkpoints
 * (slime ball) and named checkpoints (via commands / GUI).
 */
public class CheckpointManager {
    private final Map<UUID, Checkpoint> quickCheckpoints = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Checkpoint>> namedCheckpoints = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedNamedCheckpoints = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Instant>> cloneHistory = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> clonedCounts = new ConcurrentHashMap<>();
    private Runnable onDataChanged;

    // -----------------------------------------------------------------------
    // Persistence support
    // -----------------------------------------------------------------------

    /** Register a callback that is invoked whenever persistent data changes. */
    public void setOnDataChanged(Runnable callback) {
        this.onDataChanged = callback;
    }

    private void notifyDataChanged() {
        if (onDataChanged != null) onDataChanged.run();
    }

    /** Returns an unmodifiable snapshot of all quick checkpoints. */
    public Map<UUID, Checkpoint> getAllQuickCheckpoints() {
        return Map.copyOf(quickCheckpoints);
    }

    /** Returns a deep-copy snapshot of all named checkpoints. */
    public Map<UUID, Map<String, Checkpoint>> getAllNamedCheckpoints() {
        Map<UUID, Map<String, Checkpoint>> copy = new HashMap<>();
        for (var entry : namedCheckpoints.entrySet()) {
            copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    /** Returns an unmodifiable snapshot of all selected checkpoint names. */
    public Map<UUID, String> getAllSelectedCheckpoints() {
        return Map.copyOf(selectedNamedCheckpoints);
    }

    /** Returns a deep-copy snapshot of all clone history. */
    public Map<UUID, Map<UUID, Instant>> getAllCloneHistory() {
        Map<UUID, Map<UUID, Instant>> copy = new HashMap<>();
        for (var entry : cloneHistory.entrySet()) {
            copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    /** Returns an unmodifiable snapshot of all cloned counts. */
    public Map<UUID, Integer> getAllClonedCounts() {
        return Map.copyOf(clonedCounts);
    }

    /**
     * Bulk-load persisted data into this manager, replacing any existing data.
     * Does NOT trigger the onDataChanged callback.
     */
    public void loadData(
            Map<UUID, Checkpoint> quickCps,
            Map<UUID, Map<String, Checkpoint>> namedCps,
            Map<UUID, String> selected,
            Map<UUID, Map<UUID, Instant>> clones,
            Map<UUID, Integer> counts) {
        quickCheckpoints.clear();
        if (quickCps != null) quickCheckpoints.putAll(quickCps);

        namedCheckpoints.clear();
        if (namedCps != null) {
            for (var entry : namedCps.entrySet()) {
                namedCheckpoints.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
            }
        }

        selectedNamedCheckpoints.clear();
        if (selected != null) selectedNamedCheckpoints.putAll(selected);

        cloneHistory.clear();
        if (clones != null) {
            for (var entry : clones.entrySet()) {
                cloneHistory.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
            }
        }

        clonedCounts.clear();
        if (counts != null) clonedCounts.putAll(counts);
    }

    /** Returns all player UUIDs that have any data (quick, named, or clone). */
    public Set<UUID> getAllPlayerUuids() {
        Set<UUID> all = new HashSet<>();
        all.addAll(quickCheckpoints.keySet());
        all.addAll(namedCheckpoints.keySet());
        all.addAll(selectedNamedCheckpoints.keySet());
        return Set.copyOf(all);
    }

    // -----------------------------------------------------------------------
    // Sort / Search
    // -----------------------------------------------------------------------

    /**
     * Returns a sorted (and optionally filtered) list of checkpoint names for
     * the given player. If {@code query} is non-null and non-empty the list is
     * filtered to names that contain the query string (case-insensitive).
     */
    public List<String> getSortedFilteredCheckpointNames(
            UUID playerId, SortOrder order, String query,
            double playerX, double playerZ) {

        if (playerId == null) return List.of();

        Map<String, Checkpoint> playerMap = namedCheckpoints.get(playerId);
        if (playerMap == null || playerMap.isEmpty()) return List.of();

        List<Map.Entry<String, Checkpoint>> entries = new ArrayList<>(playerMap.entrySet());

        // Filter by query
        if (query != null && !query.isBlank()) {
            String lower = query.toLowerCase();
            entries.removeIf(e -> !e.getKey().toLowerCase().contains(lower));
        }

        Comparator<Map.Entry<String, Checkpoint>> comparator;
        if (order == SortOrder.NAME_DESC) {
            Comparator<Map.Entry<String, Checkpoint>> c = Comparator.comparing(
                e -> e.getKey(), String.CASE_INSENSITIVE_ORDER);
            comparator = c.reversed();
        } else if (order == SortOrder.CREATED_ASC) {
            Comparator<Map.Entry<String, Checkpoint>> c = Comparator.comparing(e -> e.getValue().createdAt());
            comparator = c;
        } else if (order == SortOrder.CREATED_DESC) {
            Comparator<Map.Entry<String, Checkpoint>> c = Comparator.comparing(e -> e.getValue().createdAt());
            comparator = c.reversed();
        } else if (order == SortOrder.UPDATED_ASC) {
            Comparator<Map.Entry<String, Checkpoint>> c = Comparator.comparing(e -> e.getValue().updatedAt());
            comparator = c;
        } else if (order == SortOrder.UPDATED_DESC) {
            Comparator<Map.Entry<String, Checkpoint>> c = Comparator.comparing(e -> e.getValue().updatedAt());
            comparator = c.reversed();
        } else if (order == SortOrder.DISTANCE_ASC) {
            comparator = Comparator.comparingDouble(e -> distanceSq(e.getValue(), playerX, playerZ));
        } else {
            // NAME_ASC (default)
            Comparator<Map.Entry<String, Checkpoint>> c = Comparator.comparing(
                e -> e.getKey(), String.CASE_INSENSITIVE_ORDER);
            comparator = c;
        }

        entries.sort(comparator);

        List<String> result = new ArrayList<>(entries.size());
        for (Map.Entry<String, Checkpoint> e : entries) result.add(e.getKey());
        return Collections.unmodifiableList(result);
    }

    private static double distanceSq(Checkpoint cp, double px, double pz) {
        double dx = cp.x() - px;
        double dz = cp.z() - pz;
        return dx * dx + dz * dz;
    }

    // -----------------------------------------------------------------------
    // Quick checkpoint
    // -----------------------------------------------------------------------

    public void setQuickCheckpoint(UUID playerId, Checkpoint checkpoint) {
        UUID validatedId = Objects.requireNonNull(playerId, "playerId cannot be null");
        Checkpoint validatedCheckpoint = Objects.requireNonNull(checkpoint, "checkpoint cannot be null");
        quickCheckpoints.put(validatedId, validatedCheckpoint);
        notifyDataChanged();
    }

    public Optional<Checkpoint> getQuickCheckpoint(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(quickCheckpoints.get(playerId));
    }

    public void clearQuickCheckpoint(UUID playerId) {
        if (playerId != null) {
            quickCheckpoints.remove(playerId);
            notifyDataChanged();
        }
    }

    // -----------------------------------------------------------------------
    // Named checkpoints
    // -----------------------------------------------------------------------

    public boolean addNamedCheckpoint(UUID playerId, String rawName, Checkpoint checkpoint) {
        UUID validatedId = Objects.requireNonNull(playerId, "playerId cannot be null");
        Checkpoint validatedCheckpoint = Objects.requireNonNull(checkpoint, "checkpoint cannot be null");
        String name = validateName(rawName);

        Map<String, Checkpoint> playerMap = namedCheckpoints.computeIfAbsent(validatedId, id -> new ConcurrentHashMap<>());
        Optional<String> existing = findExistingKey(playerMap, name);
        if (existing.isPresent()) {
            return false;
        }

        playerMap.put(name, validatedCheckpoint);
        notifyDataChanged();
        return true;
    }

    public boolean updateNamedCheckpoint(UUID playerId, String rawName, Checkpoint checkpoint) {
        UUID validatedId = Objects.requireNonNull(playerId, "playerId cannot be null");
        Checkpoint validatedCheckpoint = Objects.requireNonNull(checkpoint, "checkpoint cannot be null");
        String name = validateName(rawName);

        Map<String, Checkpoint> playerMap = namedCheckpoints.get(validatedId);
        if (playerMap == null || playerMap.isEmpty()) {
            return false;
        }

        Optional<String> actualKey = findExistingKey(playerMap, name);
        if (actualKey.isEmpty()) {
            return false;
        }

        // Preserve createdAt, refresh updatedAt
        Checkpoint existing = playerMap.get(actualKey.get());
        Checkpoint updated = validatedCheckpoint.withTimestamps(existing.createdAt(), Instant.now());
        playerMap.put(actualKey.get(), updated);
        notifyDataChanged();
        return true;
    }

    public boolean removeNamedCheckpoint(UUID playerId, String rawName) {
        if (playerId == null) {
            return false;
        }
        Map<String, Checkpoint> playerMap = namedCheckpoints.get(playerId);
        if (playerMap == null) {
            return false;
        }

        String name = validateName(rawName);
        Optional<String> actualKey = findExistingKey(playerMap, name);
        if (actualKey.isEmpty()) {
            return false;
        }

        playerMap.remove(actualKey.get());
        if (playerMap.isEmpty()) {
            namedCheckpoints.remove(playerId);
        }

        selectedNamedCheckpoints.computeIfPresent(playerId, (id, selected) -> selected.equalsIgnoreCase(name) ? null : selected);
        notifyDataChanged();
        return true;
    }

    public Optional<Checkpoint> getNamedCheckpoint(UUID playerId, String rawName) {
        if (playerId == null || rawName == null) {
            return Optional.empty();
        }
        Map<String, Checkpoint> playerMap = namedCheckpoints.get(playerId);
        if (playerMap == null) {
            return Optional.empty();
        }

        Optional<String> actualKey = findExistingKey(playerMap, rawName);
        return actualKey.map(playerMap::get);
    }

    public List<String> getNamedCheckpointNames(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        Map<String, Checkpoint> playerMap = namedCheckpoints.get(playerId);
        if (playerMap == null || playerMap.isEmpty()) {
            return List.of();
        }

        List<String> names = new ArrayList<>(playerMap.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return Collections.unmodifiableList(names);
    }

    // -----------------------------------------------------------------------
    // Selection
    // -----------------------------------------------------------------------

    public boolean selectNamedCheckpoint(UUID playerId, String rawName) {
        if (playerId == null) {
            return false;
        }
        Map<String, Checkpoint> playerMap = namedCheckpoints.get(playerId);
        if (playerMap == null) {
            return false;
        }

        String name = validateName(rawName);
        Optional<String> actualKey = findExistingKey(playerMap, name);
        if (actualKey.isEmpty()) {
            return false;
        }

        selectedNamedCheckpoints.put(playerId, actualKey.get());
        notifyDataChanged();
        return true;
    }

    public Optional<String> getSelectedNamedCheckpointName(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        String selected = selectedNamedCheckpoints.get(playerId);
        if (selected == null) {
            return Optional.empty();
        }
        Map<String, Checkpoint> playerMap = namedCheckpoints.get(playerId);
        if (playerMap == null) {
            selectedNamedCheckpoints.remove(playerId);
            return Optional.empty();
        }
        Optional<String> actualKey = findExistingKey(playerMap, selected);
        if (actualKey.isEmpty()) {
            selectedNamedCheckpoints.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(actualKey.get());
    }

    public Optional<Checkpoint> getSelectedNamedCheckpoint(UUID playerId) {
        return getSelectedNamedCheckpointName(playerId).flatMap(name -> getNamedCheckpoint(playerId, name));
    }

    public void clearSelectedNamedCheckpoint(UUID playerId) {
        if (playerId != null) {
            selectedNamedCheckpoints.remove(playerId);
            notifyDataChanged();
        }
    }

    // -----------------------------------------------------------------------
    // Description
    // -----------------------------------------------------------------------

    public boolean setNamedCheckpointDescription(UUID playerId, String rawName, String description) {
        if (playerId == null) return false;
        Map<String, Checkpoint> playerMap = namedCheckpoints.get(playerId);
        if (playerMap == null) return false;
        String name = validateName(rawName);
        Optional<String> actualKey = findExistingKey(playerMap, name);
        if (actualKey.isEmpty()) return false;
        Checkpoint existing = playerMap.get(actualKey.get());
        playerMap.put(actualKey.get(), existing.withDescription(description));
        notifyDataChanged();
        return true;
    }

    // -----------------------------------------------------------------------
    // Rename
    // -----------------------------------------------------------------------

    public RenameResult renameNamedCheckpoint(UUID playerId, String oldRawName, String newRawName) {
        UUID validatedId = Objects.requireNonNull(playerId, "playerId cannot be null");
        String oldName = validateName(oldRawName);
        String newName = validateName(newRawName);

        Map<String, Checkpoint> playerMap = namedCheckpoints.get(validatedId);
        if (playerMap == null) {
            return RenameResult.OLD_NOT_FOUND;
        }

        Optional<String> oldKey = findExistingKey(playerMap, oldName);
        if (oldKey.isEmpty()) {
            return RenameResult.OLD_NOT_FOUND;
        }

        Optional<String> newKey = findExistingKey(playerMap, newName);
        if (newKey.isPresent()) {
            return RenameResult.NEW_ALREADY_EXISTS;
        }

        Checkpoint checkpoint = playerMap.remove(oldKey.get());
        // Refresh updatedAt on rename
        Checkpoint renamed = checkpoint.withTimestamps(checkpoint.createdAt(), Instant.now());
        playerMap.put(newName, renamed);

        selectedNamedCheckpoints.computeIfPresent(validatedId,
            (id, selected) -> selected.equalsIgnoreCase(oldName) ? newName : selected);

        notifyDataChanged();
        return RenameResult.SUCCESS;
    }

    // -----------------------------------------------------------------------
    // Clone tracking
    // -----------------------------------------------------------------------

    public void recordClone(UUID clonerId, UUID sourcePlayerId) {
        Objects.requireNonNull(clonerId, "clonerId cannot be null");
        Objects.requireNonNull(sourcePlayerId, "sourcePlayerId cannot be null");
        cloneHistory.computeIfAbsent(clonerId, k -> new ConcurrentHashMap<>())
            .put(sourcePlayerId, Instant.now());
        clonedCounts.merge(sourcePlayerId, 1, Integer::sum);
        notifyDataChanged();
    }

    public Optional<Instant> getCloneTime(UUID clonerId, UUID sourcePlayerId) {
        if (clonerId == null || sourcePlayerId == null) return Optional.empty();
        Map<UUID, Instant> history = cloneHistory.get(clonerId);
        if (history == null) return Optional.empty();
        return Optional.ofNullable(history.get(sourcePlayerId));
    }

    public int getClonedCount(UUID playerId) {
        if (playerId == null) return 0;
        return clonedCounts.getOrDefault(playerId, 0);
    }

    // -----------------------------------------------------------------------
    // Player data queries
    // -----------------------------------------------------------------------

    public Set<UUID> getAllPlayersWithData() {
        return Set.copyOf(namedCheckpoints.keySet());
    }

    public Optional<Instant> getLastActivityTime(UUID playerId) {
        if (playerId == null) return Optional.empty();
        Map<String, Checkpoint> playerMap = namedCheckpoints.get(playerId);
        if (playerMap == null || playerMap.isEmpty()) return Optional.empty();
        return playerMap.values().stream()
            .map(Checkpoint::updatedAt)
            .max(Comparator.naturalOrder());
    }

    public double getNearestCpDistanceSq(UUID playerId, double px, double pz) {
        if (playerId == null) return Double.MAX_VALUE;
        Map<String, Checkpoint> playerMap = namedCheckpoints.get(playerId);
        if (playerMap == null || playerMap.isEmpty()) return Double.MAX_VALUE;
        return playerMap.values().stream()
            .mapToDouble(cp -> distanceSq(cp, px, pz))
            .min()
            .orElse(Double.MAX_VALUE);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private Optional<String> findExistingKey(Map<String, Checkpoint> playerMap, String rawName) {
        if (playerMap == null || rawName == null) {
            return Optional.empty();
        }
        String name = rawName.trim();
        return playerMap.keySet().stream()
            .filter(existing -> existing.equalsIgnoreCase(name))
            .findFirst();
    }

    private String validateName(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        return trimmed;
    }
}
