package checkpoint.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable data class representing a checkpoint location.
 */
public final class Checkpoint {
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String description;

    public Checkpoint(String worldName, double x, double y, double z, float yaw, float pitch) {
        this(worldName, x, y, z, yaw, pitch, Instant.now(), Instant.now(), "");
    }

    public Checkpoint(String worldName, double x, double y, double z, float yaw, float pitch,
                      Instant createdAt, Instant updatedAt) {
        this(worldName, x, y, z, yaw, pitch, createdAt, updatedAt, "");
    }

    public Checkpoint(String worldName, double x, double y, double z, float yaw, float pitch,
                      Instant createdAt, Instant updatedAt, String description) {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must be provided");
        }
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.description = description == null ? "" : description.trim();
    }

    public String worldName() { return worldName; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String description() { return description; }

    /** Returns a new Checkpoint with the given timestamps. */
    public Checkpoint withTimestamps(Instant newCreatedAt, Instant newUpdatedAt) {
        return new Checkpoint(worldName, x, y, z, yaw, pitch, newCreatedAt, newUpdatedAt, description);
    }

    /** Returns a new Checkpoint with the given description and refreshed updatedAt. */
    public Checkpoint withDescription(String newDescription) {
        return new Checkpoint(worldName, x, y, z, yaw, pitch, createdAt, Instant.now(), newDescription);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Checkpoint c)) return false;
        return Double.compare(x, c.x) == 0 && Double.compare(y, c.y) == 0
            && Double.compare(z, c.z) == 0
            && Float.compare(yaw, c.yaw) == 0 && Float.compare(pitch, c.pitch) == 0
            && worldName.equals(c.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, y, z, yaw, pitch);
    }

    @Override
    public String toString() {
        return "Checkpoint{world=" + worldName + ", x=" + x + ", y=" + y + ", z=" + z + "}";
    }
}
