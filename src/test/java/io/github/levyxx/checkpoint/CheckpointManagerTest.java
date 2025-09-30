package io.github.levyxx.checkpoint;

import static org.junit.jupiter.api.Assertions.*;

import io.github.levyxx.checkpoint.CheckpointManager.Checkpoint;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CheckpointManagerTest {

    @Test
    @DisplayName("保存したチェックポイントを取得できる")
    void shouldReturnStoredCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 10.5, 64.0, -23.1, 90.0f, 45.0f);

        manager.setCheckpoint(playerId, checkpoint);

        Optional<Checkpoint> actual = manager.getCheckpoint(playerId);
        assertTrue(actual.isPresent(), "チェックポイントが存在するはず");
        assertEquals(checkpoint, actual.get(), "保存したチェックポイントと同じであるべき");
    }

    @Test
    @DisplayName("clearCheckpointでチェックポイントが削除される")
    void shouldClearCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 0.0, 70.0, 0.0, 0.0f, 0.0f);
        manager.setCheckpoint(playerId, checkpoint);

        manager.clearCheckpoint(playerId);

        assertTrue(manager.getCheckpoint(playerId).isEmpty(), "削除後はチェックポイントが存在しないはず");
    }

    @Test
    @DisplayName("nullのプレイヤーIDに対してはOptional.emptyを返す")
    void shouldHandleNullPlayerId() {
        CheckpointManager manager = new CheckpointManager();
        assertTrue(manager.getCheckpoint(null).isEmpty(), "nullの場合は空のOptionalであるべき");
    }
}
