package io.github.levyxx.checkpoint;

import java.util.ArrayList;
import java.util.Collections;
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

        playerMap.put(actualKey.get(), validatedCheckpoint);
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

    public static record Checkpoint(String worldName, double x, double y, double z, float yaw, float pitch) {
        public Checkpoint {
            if (worldName == null || worldName.isBlank()) {
                throw new IllegalArgumentException("worldName must be provided");
            }
        }
    }
}
