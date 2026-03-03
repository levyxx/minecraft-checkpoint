package checkpoint.manager;

import static org.junit.jupiter.api.Assertions.*;

import checkpoint.model.Checkpoint;
import checkpoint.model.RenameResult;
import checkpoint.model.SortOrder;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

        assertFalse(manager.getQuickCheckpoint(playerId).isPresent(), "削除後はチェックポイントが存在しないはず");
    }

    @Test
    @DisplayName("nullのプレイヤーIDに対してはOptional.emptyを返す")
    void shouldHandleNullPlayerId() {
        CheckpointManager manager = new CheckpointManager();
        assertFalse(manager.getQuickCheckpoint(null).isPresent(), "nullの場合は空のOptionalであるべき");
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
        assertFalse(manager.getSelectedNamedCheckpoint(playerId).isPresent(), "削除後は選択が解除されるべき");
    }

    @Test
    @DisplayName("名前付きチェックポイントの名前を変更できる")
    void shouldRenameNamedCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 1.0, 65.0, 2.0, 0.0f, 0.0f);
        manager.addNamedCheckpoint(playerId, "Base", checkpoint);

        RenameResult result = manager.renameNamedCheckpoint(playerId, "Base", "Home");

        assertEquals(RenameResult.SUCCESS, result, "リネームは成功するはず");
        assertTrue(manager.getNamedCheckpoint(playerId, "Home").isPresent(), "新しい名前で取得できるはず");
        assertFalse(manager.getNamedCheckpoint(playerId, "Base").isPresent(), "古い名前では取得できないはず");
        assertEquals(checkpoint, manager.getNamedCheckpoint(playerId, "Home").get(), "チェックポイントのデータが保持されるはず");
    }

    @Test
    @DisplayName("大文字小文字を無視してリネームできる")
    void shouldRenameWithCaseInsensitiveOldName() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        manager.addNamedCheckpoint(playerId, "Base", new Checkpoint("world", 0.0, 64.0, 0.0, 0.0f, 0.0f));

        RenameResult result = manager.renameNamedCheckpoint(playerId, "BASE", "NewBase");

        assertEquals(RenameResult.SUCCESS, result, "大文字小文字を無視してリネームできるはず");
    }

    @Test
    @DisplayName("存在しない名前付きチェックポイントはリネームできない")
    void shouldReturnOldNotFoundWhenRenamingUnknown() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();

        RenameResult result = manager.renameNamedCheckpoint(playerId, "Ghost", "NewName");

        assertEquals(RenameResult.OLD_NOT_FOUND, result, "存在しない場合はOLD_NOT_FOUNDを返すはず");
    }

    @Test
    @DisplayName("新しい名前が既に存在する場合はリネームできない")
    void shouldReturnNewAlreadyExistsWhenConflict() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint checkpoint = new Checkpoint("world", 0.0, 64.0, 0.0, 0.0f, 0.0f);
        manager.addNamedCheckpoint(playerId, "Base", checkpoint);
        manager.addNamedCheckpoint(playerId, "Home", checkpoint);

        RenameResult result = manager.renameNamedCheckpoint(playerId, "Base", "home");

        assertEquals(RenameResult.NEW_ALREADY_EXISTS, result, "新名が既存の場合はNEW_ALREADY_EXISTSを返すはず");
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

    // -----------------------------------------------------------------------
    // Sort / Search tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("NAME_ASCで名前の昇順にソートされる")
    void shouldSortByNameAsc() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        manager.addNamedCheckpoint(playerId, "Zeta",  new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.addNamedCheckpoint(playerId, "Alpha", new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.addNamedCheckpoint(playerId, "Mango", new Checkpoint("world", 0, 64, 0, 0, 0));

        List<String> names = manager.getSortedFilteredCheckpointNames(
            playerId, SortOrder.NAME_ASC, null, 0, 0);

        assertEquals(Arrays.asList("Alpha", "Mango", "Zeta"), names);
    }

    @Test
    @DisplayName("NAME_DESCで名前の降順にソートされる")
    void shouldSortByNameDesc() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        manager.addNamedCheckpoint(playerId, "Zeta",  new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.addNamedCheckpoint(playerId, "Alpha", new Checkpoint("world", 0, 64, 0, 0, 0));

        List<String> names = manager.getSortedFilteredCheckpointNames(
            playerId, SortOrder.NAME_DESC, null, 0, 0);

        assertEquals(Arrays.asList("Zeta", "Alpha"), names);
    }

    @Test
    @DisplayName("部分一致でフィルタリングされる")
    void shouldFilterByQuery() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        manager.addNamedCheckpoint(playerId, "Home",   new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.addNamedCheckpoint(playerId, "HomeCave", new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.addNamedCheckpoint(playerId, "Base",   new Checkpoint("world", 0, 64, 0, 0, 0));

        List<String> names = manager.getSortedFilteredCheckpointNames(
            playerId, SortOrder.NAME_ASC, "home", 0, 0);

        assertEquals(2, names.size(), "homeを含む2件のみ");
        assertFalse(names.contains("Base"), "Baseは除外されるべき");
    }

    @Test
    @DisplayName("空クエリはフィルタリングしない")
    void shouldNotFilterOnBlankQuery() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        manager.addNamedCheckpoint(playerId, "A", new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.addNamedCheckpoint(playerId, "B", new Checkpoint("world", 0, 64, 0, 0, 0));

        List<String> names = manager.getSortedFilteredCheckpointNames(
            playerId, SortOrder.NAME_ASC, "  ", 0, 0);

        assertEquals(2, names.size());
    }

    @Test
    @DisplayName("DISTANCE_ASCでプレイヤーに近い順にソートされる")
    void shouldSortByDistanceAsc() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        // Far
        manager.addNamedCheckpoint(playerId, "Far",  new Checkpoint("world", 1000, 64, 0, 0, 0));
        // Near
        manager.addNamedCheckpoint(playerId, "Near", new Checkpoint("world", 1,    64, 0, 0, 0));

        List<String> names = manager.getSortedFilteredCheckpointNames(
            playerId, SortOrder.DISTANCE_ASC, null, 0, 0);

        assertEquals("Near", names.get(0), "近い方が先頭であるべき");
        assertEquals("Far",  names.get(1), "遠い方が末尾であるべき");
    }

    @Test
    @DisplayName("updateNamedCheckpointはcreatedAtを保持しupdatedAtを更新する")
    void shouldPreserveCreatedAtOnUpdate() throws InterruptedException {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint original = new Checkpoint("world", 0, 64, 0, 0, 0);
        manager.addNamedCheckpoint(playerId, "Base", original);

        Checkpoint atAdd = manager.getNamedCheckpoint(playerId, "Base").get();
        Thread.sleep(10); // ensure time passes

        Checkpoint newCp = new Checkpoint("world", 1, 65, 1, 0, 0);
        manager.updateNamedCheckpoint(playerId, "Base", newCp);

        Checkpoint afterUpdate = manager.getNamedCheckpoint(playerId, "Base").get();

        assertEquals(atAdd.createdAt(), afterUpdate.createdAt(), "createdAtは変わらないはず");
        assertTrue(afterUpdate.updatedAt().isAfter(atAdd.updatedAt()), "updatedAtが更新されるはず");
    }

    @Test
    @DisplayName("renameNamedCheckpointはupdatedAtを更新する")
    void shouldUpdateUpdatedAtOnRename() throws InterruptedException {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        manager.addNamedCheckpoint(playerId, "OldName", new Checkpoint("world", 0, 64, 0, 0, 0));

        Checkpoint before = manager.getNamedCheckpoint(playerId, "OldName").get();
        Thread.sleep(10);

        manager.renameNamedCheckpoint(playerId, "OldName", "NewName");
        Checkpoint after = manager.getNamedCheckpoint(playerId, "NewName").get();

        assertEquals(before.createdAt(), after.createdAt(), "createdAtは変わらないはず");
        assertTrue(after.updatedAt().isAfter(before.updatedAt()), "renameでupdatedAtが更新されるはず");
    }

    // -----------------------------------------------------------------------
    // Clone tracking tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("クローン記録を追加して取得できる")
    void shouldRecordAndRetrieveClone() {
        CheckpointManager manager = new CheckpointManager();
        UUID cloner = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        manager.addNamedCheckpoint(source, "Base", new Checkpoint("world", 0, 64, 0, 0, 0));

        manager.recordClone(cloner, source);

        assertTrue(manager.getCloneTime(cloner, source).isPresent(), "クローン時刻が取得できるはず");
        assertEquals(1, manager.getClonedCount(source), "被クローン回数は1");
    }

    @Test
    @DisplayName("複数回クローンするとカウントが増える")
    void shouldIncrementCloneCount() {
        CheckpointManager manager = new CheckpointManager();
        UUID cloner1 = UUID.randomUUID();
        UUID cloner2 = UUID.randomUUID();
        UUID source = UUID.randomUUID();

        manager.recordClone(cloner1, source);
        manager.recordClone(cloner2, source);

        assertEquals(2, manager.getClonedCount(source), "2回クローンされたはず");
    }

    @Test
    @DisplayName("クローンしていないプレイヤーのクローン時刻はempty")
    void shouldReturnEmptyCloneTimeForUncloned() {
        CheckpointManager manager = new CheckpointManager();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertFalse(manager.getCloneTime(a, b).isPresent());
        assertEquals(0, manager.getClonedCount(b));
    }

    @Test
    @DisplayName("データがある全プレイヤーを取得できる")
    void shouldReturnAllPlayersWithData() {
        CheckpointManager manager = new CheckpointManager();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        manager.addNamedCheckpoint(p1, "A", new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.addNamedCheckpoint(p2, "B", new Checkpoint("world", 10, 64, 10, 0, 0));

        Set<UUID> all = manager.getAllPlayersWithData();

        assertEquals(2, all.size());
        assertTrue(all.contains(p1));
        assertTrue(all.contains(p2));
    }

    @Test
    @DisplayName("最終操作日時を取得できる")
    void shouldReturnLastActivityTime() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        manager.addNamedCheckpoint(playerId, "A", new Checkpoint("world", 0, 64, 0, 0, 0));

        Optional<Instant> time = manager.getLastActivityTime(playerId);

        assertTrue(time.isPresent(), "最終操作日時が取得できるはず");
    }

    @Test
    @DisplayName("CPがないプレイヤーの最終操作日時はempty")
    void shouldReturnEmptyLastActivityForNoData() {
        CheckpointManager manager = new CheckpointManager();
        assertFalse(manager.getLastActivityTime(UUID.randomUUID()).isPresent());
    }

    @Test
    @DisplayName("最寄りCPの距離を計算できる")
    void shouldComputeNearestCpDistance() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        manager.addNamedCheckpoint(playerId, "Near", new Checkpoint("world", 10, 64, 0, 0, 0));
        manager.addNamedCheckpoint(playerId, "Far", new Checkpoint("world", 1000, 64, 0, 0, 0));

        double distSq = manager.getNearestCpDistanceSq(playerId, 0, 0);

        assertEquals(100.0, distSq, 0.001, "最寄りCP (10,0) との距離の2乗は100");
    }

    @Test
    @DisplayName("CPがないプレイヤーの最寄り距離はMAX_VALUE")
    void shouldReturnMaxDistanceForNoData() {
        CheckpointManager manager = new CheckpointManager();
        assertEquals(Double.MAX_VALUE, manager.getNearestCpDistanceSq(UUID.randomUUID(), 0, 0));
    }

    // -----------------------------------------------------------------------
    // Persistence support tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("loadDataでインポートしたデータが正しく取得できる")
    void shouldLoadDataCorrectly() {
        CheckpointManager manager = new CheckpointManager();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        Instant now = Instant.now();
        Checkpoint quickCp = new Checkpoint("world", 1, 64, 2, 0, 0, now, now, "");
        Checkpoint namedCp = new Checkpoint("world", 10, 65, 20, 90f, 0f, now, now, "Home base");

        Map<UUID, Checkpoint> quickMap = new HashMap<>();
        quickMap.put(p1, quickCp);
        Map<String, Checkpoint> homeMap = new HashMap<>();
        homeMap.put("Home", namedCp);
        Map<UUID, Map<String, Checkpoint>> namedMap = new HashMap<>();
        namedMap.put(p1, homeMap);
        Map<UUID, String> selected = new HashMap<>();
        selected.put(p1, "Home");
        Map<UUID, Instant> p2CloneInner = new HashMap<>();
        p2CloneInner.put(p1, now);
        Map<UUID, Map<UUID, Instant>> clones = new HashMap<>();
        clones.put(p2, p2CloneInner);
        Map<UUID, Integer> counts = new HashMap<>();
        counts.put(p1, 3);

        manager.loadData(quickMap, namedMap, selected, clones, counts, Collections.emptyMap());

        assertEquals(quickCp, manager.getQuickCheckpoint(p1).orElse(null));
        assertEquals(namedCp, manager.getNamedCheckpoint(p1, "Home").orElse(null));
        assertEquals("Home", manager.getSelectedNamedCheckpointName(p1).orElse(null));
        assertTrue(manager.getCloneTime(p2, p1).isPresent());
        assertEquals(3, manager.getClonedCount(p1));
    }

    @Test
    @DisplayName("getAllQuickCheckpointsがスナップショットを返す")
    void shouldReturnQuickCheckpointSnapshot() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint cp = new Checkpoint("world", 0, 64, 0, 0, 0);
        manager.setQuickCheckpoint(playerId, cp);

        Map<UUID, Checkpoint> snapshot = manager.getAllQuickCheckpoints();
        assertEquals(1, snapshot.size());
        assertEquals(cp, snapshot.get(playerId));
    }

    @Test
    @DisplayName("getAllNamedCheckpointsが深いコピーを返す")
    void shouldReturnNamedCheckpointDeepCopy() {
        CheckpointManager manager = new CheckpointManager();
        UUID playerId = UUID.randomUUID();
        Checkpoint cp = new Checkpoint("world", 0, 64, 0, 0, 0);
        manager.addNamedCheckpoint(playerId, "Base", cp);

        Map<UUID, Map<String, Checkpoint>> snapshot = manager.getAllNamedCheckpoints();
        assertEquals(1, snapshot.size());
        assertEquals(cp, snapshot.get(playerId).get("Base"));
    }

    @Test
    @DisplayName("loadDataは既存データを上書きする")
    void shouldOverwriteExistingDataOnLoad() {
        CheckpointManager manager = new CheckpointManager();
        UUID p = UUID.randomUUID();
        manager.addNamedCheckpoint(p, "Old", new Checkpoint("world", 0, 64, 0, 0, 0));

        Checkpoint newCp = new Checkpoint("world", 100, 70, 100, 0, 0);
        Map<String, Checkpoint> newInner = new HashMap<>();
        newInner.put("New", newCp);
        Map<UUID, Map<String, Checkpoint>> newNamed = new HashMap<>();
        newNamed.put(p, newInner);
        manager.loadData(
            Collections.emptyMap(),
            newNamed,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap()
        );

        assertFalse(manager.getNamedCheckpoint(p, "Old").isPresent(), "旧データは消えるはず");
        assertEquals(newCp, manager.getNamedCheckpoint(p, "New").orElse(null));
    }

    @Test
    @DisplayName("onDataChangedコールバックが変更時に呼ばれる")
    void shouldInvokeOnDataChangedCallback() {
        CheckpointManager manager = new CheckpointManager();
        AtomicInteger callCount = new AtomicInteger(0);
        manager.setOnDataChanged(callCount::incrementAndGet);

        UUID p = UUID.randomUUID();
        manager.setQuickCheckpoint(p, new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.addNamedCheckpoint(p, "A", new Checkpoint("world", 1, 64, 1, 0, 0));
        manager.selectNamedCheckpoint(p, "A");
        manager.renameNamedCheckpoint(p, "A", "B");
        manager.setNamedCheckpointDescription(p, "B", "desc");
        manager.removeNamedCheckpoint(p, "B");
        manager.clearQuickCheckpoint(p);

        assertEquals(7, callCount.get(), "全ての変更操作でコールバックが呼ばれるはず");
    }

    @Test
    @DisplayName("loadDataはonDataChangedを発火しない")
    void shouldNotFireCallbackOnLoad() {
        CheckpointManager manager = new CheckpointManager();
        AtomicInteger callCount = new AtomicInteger(0);
        manager.setOnDataChanged(callCount::incrementAndGet);

        manager.loadData(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        assertEquals(0, callCount.get(), "loadDataではコールバックは呼ばれないはず");
    }

    @Test
    @DisplayName("getAllPlayerUuidsが全プレイヤーUUIDを返す")
    void shouldReturnAllPlayerUuids() {
        CheckpointManager manager = new CheckpointManager();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        manager.setQuickCheckpoint(p1, new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.addNamedCheckpoint(p2, "A", new Checkpoint("world", 0, 64, 0, 0, 0));

        Set<UUID> all = manager.getAllPlayerUuids();
        assertTrue(all.contains(p1));
        assertTrue(all.contains(p2));
        assertFalse(all.contains(p3));
    }

    // -----------------------------------------------------------------------
    // Cleared checkpoint tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("markClearedでCPをクリア済みにできる")
    void shouldMarkClearedCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID p = UUID.randomUUID();
        manager.addNamedCheckpoint(p, "Alpha", new Checkpoint("world", 0, 64, 0, 0, 0));

        assertTrue(manager.markCleared(p, "Alpha"));
        assertTrue(manager.isCleared(p, "Alpha"));
        assertTrue(manager.isCleared(p, "alpha"), "大文字小文字を区別しないはず");
    }

    @Test
    @DisplayName("unmarkClearedでクリア判定を解除できる")
    void shouldUnmarkClearedCheckpoint() {
        CheckpointManager manager = new CheckpointManager();
        UUID p = UUID.randomUUID();
        manager.addNamedCheckpoint(p, "Beta", new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.markCleared(p, "Beta");

        assertTrue(manager.unmarkCleared(p, "Beta"));
        assertFalse(manager.isCleared(p, "Beta"));
    }

    @Test
    @DisplayName("クリア済みでないCPのunmarkClearedはfalseを返す")
    void shouldReturnFalseWhenUnmarkingNonCleared() {
        CheckpointManager manager = new CheckpointManager();
        UUID p = UUID.randomUUID();
        manager.addNamedCheckpoint(p, "Gamma", new Checkpoint("world", 0, 64, 0, 0, 0));

        assertFalse(manager.unmarkCleared(p, "Gamma"));
    }

    @Test
    @DisplayName("存在しないCPにmarkClearedするとfalseを返す")
    void shouldReturnFalseWhenMarkingNonExistentCp() {
        CheckpointManager manager = new CheckpointManager();
        UUID p = UUID.randomUUID();

        assertFalse(manager.markCleared(p, "NoSuchCp"));
        assertFalse(manager.isCleared(p, "NoSuchCp"));
    }

    @Test
    @DisplayName("CP削除時にクリア判定も削除される")
    void shouldRemoveClearedOnDelete() {
        CheckpointManager manager = new CheckpointManager();
        UUID p = UUID.randomUUID();
        manager.addNamedCheckpoint(p, "Del", new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.markCleared(p, "Del");

        manager.removeNamedCheckpoint(p, "Del");
        assertFalse(manager.isCleared(p, "Del"));
    }

    @Test
    @DisplayName("CPリネーム時にクリア判定も移行する")
    void shouldTransferClearedOnRename() {
        CheckpointManager manager = new CheckpointManager();
        UUID p = UUID.randomUUID();
        manager.addNamedCheckpoint(p, "OldName", new Checkpoint("world", 0, 64, 0, 0, 0));
        manager.markCleared(p, "OldName");

        RenameResult result = manager.renameNamedCheckpoint(p, "OldName", "NewName");
        assertEquals(RenameResult.SUCCESS, result);
        assertFalse(manager.isCleared(p, "OldName"));
        assertTrue(manager.isCleared(p, "NewName"));
    }

    @Test
    @DisplayName("loadDataでクリア済みデータをインポートできる")
    void shouldLoadClearedData() {
        CheckpointManager manager = new CheckpointManager();
        UUID p = UUID.randomUUID();
        Checkpoint cp = new Checkpoint("world", 0, 64, 0, 0, 0);

        Map<String, Checkpoint> abMap = new HashMap<>();
        abMap.put("A", cp);
        abMap.put("B", cp);
        Map<UUID, Map<String, Checkpoint>> namedMap = new HashMap<>();
        namedMap.put(p, abMap);
        Map<UUID, Set<String>> clearedMap = new HashMap<>();
        clearedMap.put(p, new HashSet<>(Arrays.asList("A")));
        manager.loadData(
            Collections.emptyMap(),
            namedMap,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            clearedMap
        );

        assertTrue(manager.isCleared(p, "A"));
        assertFalse(manager.isCleared(p, "B"));
    }

    @Test
    @DisplayName("markCleared/unmarkClearedがonDataChangedを発火する")
    void shouldFireCallbackOnClearedChange() {
        CheckpointManager manager = new CheckpointManager();
        AtomicInteger callCount = new AtomicInteger(0);
        manager.setOnDataChanged(callCount::incrementAndGet);

        UUID p = UUID.randomUUID();
        manager.addNamedCheckpoint(p, "X", new Checkpoint("world", 0, 64, 0, 0, 0));
        int before = callCount.get();

        manager.markCleared(p, "X");
        assertEquals(before + 1, callCount.get(), "markClearedでコールバックが呼ばれるはず");

        manager.unmarkCleared(p, "X");
        assertEquals(before + 2, callCount.get(), "unmarkClearedでコールバックが呼ばれるはず");
    }
}
