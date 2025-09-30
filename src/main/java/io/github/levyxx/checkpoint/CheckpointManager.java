package io.github.levyxx.checkpoint;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of per-player checkpoints in memory.
 */
public class CheckpointManager {
    private final Map<UUID, Checkpoint> checkpoints = new ConcurrentHashMap<>();

    public void setCheckpoint(UUID playerId, Checkpoint checkpoint) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint cannot be null");
        }
        checkpoints.put(playerId, checkpoint);
    }

    public Optional<Checkpoint> getCheckpoint(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(checkpoints.get(playerId));
    }

    public void clearCheckpoint(UUID playerId) {
        if (playerId != null) {
            checkpoints.remove(playerId);
        }
    }

    public static record Checkpoint(String worldName, double x, double y, double z, float yaw, float pitch) {
        public Checkpoint {
            if (worldName == null || worldName.isBlank()) {
                throw new IllegalArgumentException("worldName must be provided");
            }
        }
    }
}
