# アーキテクチャ — minecraft-checkpoint

---

## マルチバージョン対応

Maven マルチモジュール構成により、Minecraft 1.8.x / 1.12.x / 1.21.x の3バージョンに対応しています。

```
pom.xml (parent)
├── common/          共通コード（VersionCompat 経由でバージョン差を吸収）
├── plugin-1.8/      1.8 用エントリポイント + Compat1_8
├── plugin-1.12/     1.12 用エントリポイント + Compat1_12
└── plugin-1.21/     1.21 用エントリポイント + Compat1_21
```

### VersionCompat パターン

バージョン間で異なる Bukkit API を抽象化する `VersionCompat` クラスを common モジュールに配置し、
各バージョンモジュールが具象実装を提供します。

```
VersionCompat (abstract, common)
├── CompatLegacy (abstract, common)  ← pre-1.13 共通処理
│   ├── Compat1_8 (plugin-1.8)      ← 1.8 固有：旧Sound名, リフレクション
│   └── Compat1_12 (plugin-1.12)    ← 1.12 固有：新Sound名, EquipmentSlot
└── Compat1_21 (plugin-1.21)        ← 1.21 固有：新Material名, PersistentDataContainer
```

**抽象化される差異：**
- 色付きブロック・ウール・ガラス・インクサック（データ値 vs Flattened Material）
- サウンド名（1.8: `CLICK` → 1.9+: `UI_BUTTON_CLICK`）
- アイテムタグ付け（1.8/1.12: ロア隠しマーカー → 1.21: PersistentDataContainer）
- プレイヤーヘッド（SKULL_ITEM:3 vs PLAYER_HEAD）
- スカルオーナー設定（setOwner vs setOwningPlayer）
- プレイヤーロケール取得（リフレクション vs getLocale()）
- 装備スロット判定（常にメインハンド vs EquipmentSlot.HAND）
- テレポート前処理（isGliding / wakeup の有無）

---

## レイヤー構成

```
┌────────────────────────────────────────────────┐
│  Bukkit イベントシステム / コマンドシステム         │
└────────────┬───────────────────────────────────┘
             │ イベント / コマンド
┌────────────▼───────────────────────────────────┐
│  listener/   command/   (エントリレイヤー)        │
│  PlayerListener                                │
│  InventoryClickListener                        │
│  ChatInputListener                             │
│  CheckpointCommand                             │
└────────────┬───────────────────────────────────┘
             │ GUI 操作 / CP 操作の委譲
┌────────────▼───────────────────────────────────┐
│  gui/  (プレゼンテーションレイヤー)               │
│  MenuManager (facade)  MenuRenderer             │
│  MenuClickHandler  ChatInputHandler             │
│  TeleportHandler   ItemFactory                  │
│  PlayerItemFactory  GuiConstants                │
└────────────┬───────────────────────────────────┘
             │ CP データの読み書き
┌────────────▼───────────────────────────────────┐
│  manager/  (ドメインロジックレイヤー)             │
│  CheckpointManager  ← Bukkit 非依存             │
└────────────┬───────────────────────────────────┘
             │ 不変データオブジェクト
┌────────────▼───────────────────────────────────┐
│  model/  (データモデルレイヤー)                  │
│  Checkpoint   SortOrder   PlayerSortOrder       │
│  RenameResult                                  │
└────────────────────────────────────────────────┘

             横断的関心事
┌────────────────────────────────────────────────┐
│  i18n/Messages  (全レイヤーから参照)             │
│  CheckpointPluginBase  (DI・ライフサイクル管理)  │
│  VersionCompat  (バージョン差分吸収)             │
└────────────────────────────────────────────────┘
```

---

## 各パッケージの責務

### `model/`

純粋なデータクラスと列挙型のみを置くパッケージです。Bukkit への依存は一切ありません。

| クラス | 役割 |
|-------|------|
| `Checkpoint` | 不変の CP データ（name, world, x/y/z, description, createdAt, updatedAt） |
| `SortOrder` | CP リストのソート順を表す enum（7 種類） |
| `PlayerSortOrder` | プレイヤーリストのソート順を表す enum（7 種類） |
| `RenameResult` | リネーム操作の結果 enum（`SUCCESS` / `OLD_NOT_FOUND` / `NEW_EXISTS`） |

### `manager/`

ビジネスロジックを担当します。**Bukkit API を一切使わない**ため単体テストが容易です。

| クラス | 役割 |
|-------|------|
| `CheckpointManager` | プレイヤーごとの CP リストをインメモリで管理（add / update / delete / rename / search / sort） |

- データ構造：`Map<UUID, List<Checkpoint>>`
- 検索・ソートは毎回計算（永続化なし）

### `command/`

Bukkit の `TabExecutor` を実装します。**引数のパース・バリデーションのみ**を行い、ロジックは `CheckpointManager`・`MenuManager` に委譲します。

| クラス | 役割 |
|-------|------|
| `CheckpointCommand` | `/cp <subcommand>` 全体のルーティングと Tab 補完（ディスパッチャ） |
| `SubcommandHandlers` | 各サブコマンドの実装（set / update / delete / rename / language 等） |

### `gui/`

Bukkit Inventory API を使った GUI の構築・状態管理を担当します。

| クラス | 役割 |
|-------|------|
| `GuiConstants` | スロット番号・GUI タイトルの Set・`isOurMenu()` など定数とユーティリティ |
| `ItemFactory` | CP 関連 `ItemStack` 生成（ペーパー・ウール・ナビ・ソートボタンなど。全メソッドが `UUID viewerId` を受け取り言語対応） |
| `PlayerItemFactory` | プレイヤー関連 `ItemStack` 生成（ヘッド・操作ウール・プレイヤー選択メニュー用アイテム） |
| `MenuManager` | 全状態マップを保持するファサードクラス。外部からの呈口は変わらず、内部操作が各ハンドラクラスへ委譲される |
| `MenuRenderer` | 全 GUI 画面のインベントリ構築・`openInventory` 呼び出し（open○○Menu メソッド群） |
| `MenuClickHandler` | `InventoryClickEvent` のクリックロジック（全画面分） |
| `ChatInputHandler` | 検索・リネーム・説明変更のチャット入力処理および入力プロンプト送信 |
| `TeleportHandler` | テレポート・クイックセーブ、CP 操作 (update / delete / clone) の実行 |

**状態管理のマップ（`MenuManager` 内）：**

| フィールド | 型 | 内容 |
|-----------|---|------|
| `menuPages` | `Map<UUID, Integer>` | プレイヤーごとの現在ページ |
| `searchQueries` | `Map<UUID, String>` | 検索クエリ |
| `selectedCps` | `Map<UUID, Checkpoint>` | 選択中 CP |
| `viewingPlayerId` | `Map<UUID, UUID>` | 閲覧対象プレイヤー |
| `playerMenuPages` | `Map<UUID, Integer>` | プレイヤー選択メニューのページ |
| `playerSearchQueries` | `Map<UUID, String>` | プレイヤー検索クエリ |
| `pendingChatInputs` | `Map<UUID, PendingInputType>` | チャット入力待ち状態 |

### `listener/`

Bukkit イベントを受け取り、適切なハンドラへ委譲します。GUI ロジックや CP ロジックを自分では持ちません。

| クラス | 役割 |
|-------|------|
| `PlayerListener` | アイテム右クリック / 左クリック処理、アイテムドロップ防止、インベントリクローズ後のクリーンアップ、参加時言語検出、退出時の言語データ削除 |
| `InventoryClickListener` | GUI クリックイベントのルーティング（タイトルで GUI 種別を判定して `MenuManager` に委譲） |
| `ChatInputListener` | チャット入力待ち状態（検索・リネーム・説明変更）のプレイヤーからの入力を受け取り `MenuManager` に委譲 |

### `i18n/`

全レイヤーから参照される横断的な関心事です。

| クラス | 役割 |
|-------|------|
| `Messages` | プレイヤーごとの言語設定（`ConcurrentHashMap<UUID, Lang>`）を保持し、すべてのユーザー向け文字列を JP / EN の2言語で提供 |

**言語検出フロー：**

```
PlayerJoinEvent
  └─ PlayerListener.onPlayerJoin()
       └─ Messages.setLang(uuid, Messages.detectLang(VersionCompat.get().getPlayerLocale(player)))
            └─ locale が "ja" で始まる → Lang.JP
               それ以外               → Lang.EN

/cp language <jp|en>
  └─ CheckpointCommand.handleLanguage()
       └─ Messages.setLang(uuid, Lang.XX)  ← 手動上書き

PlayerQuitEvent
  └─ PlayerListener.onPlayerQuit()
       └─ Messages.removeLang(uuid)  ← メモリ解放
```

### `CheckpointPluginBase`（エントリポイント）

`onEnable` でオブジェクトグラフを構築し（簡易 DI）、リスナー・コマンドを登録します。  
`onDisable` で `MenuManager.clearAll()` と `Messages.clearAll()` を呼びます。  
各バージョンモジュールの `CheckpointPlugin` はこのクラスを継承し、`onEnable()` で `VersionCompat.init()` を呼んでから `super.onEnable()` を実行します。

---

## GUI タイトルの多言語対応

GUI タイトルは `MenuManager` で `ChatColor.DARK_AQUA + Messages.xxxTitle(viewerId)` として動的に生成されます。  
クリックイベント側でタイトルから GUI 種別を判定する必要があるため、`GuiConstants` に **JP / EN 両方のタイトルを持つ `Set<String>`** を定義しています。

```
GuiConstants.GUI_TITLES = Set.of(
    ChatColor.DARK_AQUA + "チェックポイント一覧",   // JP
    ChatColor.DARK_AQUA + "Checkpoint List"       // EN
)
```

`isOurMenu(title)` はすべての Set をまとめて確認するユーティリティメソッドです。  
新しい GUI を追加したら必ず `isOurMenu()` にも反映させてください。

---

## データ永続化について

現在のバージョンはデータを `plugins/minecraft-checkpoint/checkpoints.yml` に自動保存します（データ変更時）。  
`CheckpointManager` の `setOnDataChanged()` コールバック経由で `CheckpointStorage.save()` が呼び出されます。

---

## テスト方針

`CheckpointManager` は Bukkit 非依存のため、JUnit 5 + MockBukkit なしでテストできます。  
GUI・リスナー・コマンドレイヤーは Bukkit の実行環境に依存するため、現時点では手動テスト（サーバー上での動作確認）を主体としています。

テストファイル：`common/src/test/java/checkpoint/manager/CheckpointManagerTest.java`（43 件）
