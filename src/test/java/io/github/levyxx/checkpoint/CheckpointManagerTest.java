package io.github.levyxx.checkpoint;

import static org.junit.jupiter.api.Assertions.*;

import io.github.levyxx.checkpoint.CheckpointManager.Checkpoint;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CheckpointManagerTest {

    @Test
    @DisplayName("クイックチェックポイントを保存・取得できる")
    void shouldReturnStoredQuickCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 10.5, 64.0, -23.1, 90.0f, 45.0f);

        manager.setQuickCheckpoint(playerId, checkpoint);

        Optional<Checkpoint> actual = manager.getQuickCheckpoint(playerId);
        assertTrue(actual.isPresent(), "チェックポイントが存在するはず");
        assertEquals(checkpoint, actual.get(), "保存したチェックポイントと同じであるべき");
    }

    @Test
    @DisplayName("クイックチェックポイントをクリアできる")
    void shouldClearCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 0.0, 70.0, 0.0, 0.0f, 0.0f);
        manager.setQuickCheckpoint(playerId, checkpoint);

        manager.clearQuickCheckpoint(playerId);

        assertTrue(manager.getQuickCheckpoint(playerId).isEmpty(), "削除後はチェックポイントが存在しないはず");
    }

    @Test
    @DisplayName("nullのプレイヤーIDに対してはOptional.emptyを返す")
    void shouldHandleNullPlayerId() {
        CheckpointManager manager = new CheckpointManager();
        assertTrue(manager.getQuickCheckpoint(null).isEmpty(), "nullの場合は空のOptionalであるべき");
    }

    @Test
    @DisplayName("名前付きチェックポイントを重複登録できない")
    void shouldRejectDuplicateNamedCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 0.0, 64.0, 0.0, 0.0f, 0.0f);

        boolean first = manager.addNamedCheckpoint(playerId, "Home", checkpoint);
        boolean second = manager.addNamedCheckpoint(playerId, "home", checkpoint);

        assertTrue(first, "最初の登録は成功するはず");
        assertFalse(second, "大文字小文字が異なる同名は拒否されるべき");
    }

    @Test
    @DisplayName("既存の名前付きチェックポイントを更新できる")
    void shouldUpdateExistingNamedCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint original = new Checkpoint("world", 0.0, 64.0, 0.0, 0.0f, 0.0f);
        Checkpoint updated = new Checkpoint("world_nether", 10.0, 70.5, -5.0, 45.0f, 15.0f);
        manager.addNamedCheckpoint(playerId, "Home", original);

        boolean result = manager.updateNamedCheckpoint(playerId, "home", updated);
        Optional<Checkpoint> actual = manager.getNamedCheckpoint(playerId, "HOME");

        assertTrue(result, "更新は成功するはず");
        assertTrue(actual.isPresent(), "更新後もチェックポイントが取得できるはず");
        assertEquals(updated, actual.get(), "更新した内容が反映されるべき");
    }

    @Test
    @DisplayName("存在しない名前付きチェックポイントは更新できない")
    void shouldRejectUpdateForUnknownCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 0.0, 64.0, 0.0, 0.0f, 0.0f);

        boolean result = manager.updateNamedCheckpoint(playerId, "Unknown", checkpoint);

        assertFalse(result, "存在しないチェックポイントは更新できないはず");
    }

    @Test
    @DisplayName("名前付きチェックポイントを選択して取得できる")
    void shouldSelectNamedCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 100.0, 65.0, -30.0, 180.0f, 0.0f);
        manager.addNamedCheckpoint(playerId, "Home", checkpoint);

        boolean selected = manager.selectNamedCheckpoint(playerId, "HOME");
        Optional<Checkpoint> actual = manager.getSelectedNamedCheckpoint(playerId);

        assertTrue(selected, "選択は成功するはず");
        assertTrue(actual.isPresent(), "選択したチェックポイントを取得できるはず");
        assertEquals(checkpoint, actual.get());
    }

    @Test
    @DisplayName("名前付きチェックポイントの削除で選択が解除される")
    void shouldClearSelectionWhenNamedCheckpointRemoved() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 0.0, 70.0, 0.0, 0.0f, 0.0f);
        manager.addNamedCheckpoint(playerId, "Mine", checkpoint);
        manager.selectNamedCheckpoint(playerId, "Mine");

        boolean removed = manager.removeNamedCheckpoint(playerId, "mine");

        assertTrue(removed, "削除は成功するはず");
        assertTrue(manager.getSelectedNamedCheckpoint(playerId).isEmpty(), "削除後は選択が解除されるべき");
    }

    @Test
    @DisplayName("名前付きチェックポイントの名前を変更できる")
    void shouldRenameNamedCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 1.0, 65.0, 2.0, 0.0f, 0.0f);
        manager.addNamedCheckpoint(playerId, "Base", checkpoint);

        CheckpointManager.RenameResult result = manager.renameNamedCheckpoint(playerId, "Base", "Home");

        assertEquals(CheckpointManager.RenameResult.SUCCESS, result, "リネームは成功するはず");
        assertTrue(manager.getNamedCheckpoint(playerId, "Home").isPresent(), "新しい名前で取得できるはず");
        assertTrue(manager.getNamedCheckpoint(playerId, "Base").isEmpty(), "古い名前では取得できないはず");
        assertEquals(checkpoint, manager.getNamedCheckpoint(playerId, "Home").get(), "チェックポイントのデータが保持されるはず");
    }

    @Test
    @DisplayName("大文字小文字を無視してリネームできる")
    void shouldRenameWithCaseInsensitiveOldName() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        manager.addNamedCheckpoint(playerId, "Base", new Checkpoint("world", 0.0, 64.0, 0.0, 0.0f, 0.0f));

        CheckpointManager.RenameResult result = manager.renameNamedCheckpoint(playerId, "BASE", "NewBase");

        assertEquals(CheckpointManager.RenameResult.SUCCESS, result, "大文字小文字を無視してリネームできるはず");
    }

    @Test
    @DisplayName("存在しない名前付きチェックポイントはリネームできない")
    void shouldReturnOldNotFoundWhenRenamingUnknown() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();

        CheckpointManager.RenameResult result = manager.renameNamedCheckpoint(playerId, "Ghost", "NewName");

        assertEquals(CheckpointManager.RenameResult.OLD_NOT_FOUND, result, "存在しない場合はOLD_NOT_FOUNDを返すはず");
    }

    @Test
    @DisplayName("新しい名前が既に存在する場合はリネームできない")
    void shouldReturnNewAlreadyExistsWhenConflict() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 0.0, 64.0, 0.0, 0.0f, 0.0f);
        manager.addNamedCheckpoint(playerId, "Base", checkpoint);
        manager.addNamedCheckpoint(playerId, "Home", checkpoint);

        CheckpointManager.RenameResult result = manager.renameNamedCheckpoint(playerId, "Base", "home");

        assertEquals(CheckpointManager.RenameResult.NEW_ALREADY_EXISTS, result, "新名が既存の場合はNEW_ALREADY_EXISTSを返すはず");
    }

    @Test
    @DisplayName("選択中のチェックポイントをリネームすると選択も更新される")
    void shouldUpdateSelectionWhenSelectedCheckpointRenamed() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 5.0, 64.0, 5.0, 0.0f, 0.0f);
        manager.addNamedCheckpoint(playerId, "Mine", checkpoint);
        manager.selectNamedCheckpoint(playerId, "Mine");

        manager.renameNamedCheckpoint(playerId, "Mine", "Cave");

        assertEquals("Cave", manager.getSelectedNamedCheckpointName(playerId).orElse(null),
            "リネーム後も選択が新しい名前に追従するはず");
        assertEquals(checkpoint, manager.getSelectedNamedCheckpoint(playerId).orElse(null),
            "リネーム後も選択したチェックポイントが取得できるはず");
    }
}
