package checkpoint.manager;

import checkpoint.model.Checkpoint;
import checkpoint.model.RenameResult;
import checkpoint.model.SortOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

        return RenameResult.SUCCESS;
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
