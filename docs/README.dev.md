# 開発者ガイド — minecraft-checkpoint

このドキュメントは **コードを読む・修正する・機能を追加する** 開発者向けです。  
プラグインの利用方法は [../README.md](../README.md) を参照してください。

---

## 目次

- [プロジェクト概要](#プロジェクト概要)
- [開発環境の要件](#開発環境の要件)
- [ビルド・テスト](#ビルドテスト)
- [ディレクトリ構成](#ディレクトリ構成)
- [バージョン管理](#バージョン管理)
- [コマンドの追加手順](#コマンドの追加手順)
- [i18n 文字列の追加手順](#i18n-文字列の追加手順)
- [GUI メニューの追加手順](#gui-メニューの追加手順)
- [plugin.yml の同期について](#pluginyml-の同期について)
- [アーキテクチャ詳細](#アーキテクチャ詳細)

---

## プロジェクト概要

| 項目 | 内容 |
|------|------|
| 名前 | minecraft-checkpoint |
| 種別 | Spigot プラグイン |
| 対応バージョン | Minecraft 1.20.1 |
| 言語 | Java 17 |
| ビルドツール | Maven |
| テストフレームワーク | JUnit 5 |
| メインクラス | `checkpoint.CheckpointPlugin` |
| 現在のバージョン | `pom.xml` の `<version>` を参照 |

---

## 開発環境の要件

- Java 17+
- Maven 3.8+
- IDE（IntelliJ IDEA / VS Code + Extension Pack for Java 推奨）

SpigotAPI の JAR は `pom.xml` で Spigot Maven リポジトリから自動取得されます。  
サーバー本体は不要です。

---

## ビルド・テスト

```bash
# テストのみ実行
mvn test

# JAR をビルド（target/minecraft-checkpoint-<version>.jar が生成される）
mvn clean package

# リソースのみ再生成（plugin.yml を target/classes/ に同期する場合）
mvn process-resources

# テストをスキップしてビルド
mvn clean package -DskipTests
```

生成された JAR をサーバーの `plugins/` に配置してサーバーを再起動することで動作確認できます。

---

## ディレクトリ構成

```
src/main/java/checkpoint/
  CheckpointPlugin.java              # プラグインエントリポイント（onEnable / onDisable）
  model/
    Checkpoint.java                  # CP データモデル（不変クラス: name, world, x/y/z, createdAt, updatedAt）
    SortOrder.java                   # CP ソート順 enum（7 種類）
    PlayerSortOrder.java             # プレイヤーソート順 enum（7 種類）
    ClearSortOrder.java              # クリアソート順 enum
    RenameResult.java                # リネーム操作の結果 enum（SUCCESS / OLD_NOT_FOUND / NEW_EXISTS）
  manager/
    CheckpointManager.java           # インメモリ CRUD・ソート・検索（Bukkit 非依存）
  command/
    CheckpointCommand.java           # /cp コマンド実装（TabExecutor・ディスパッチャ）
    SubcommandHandlers.java          # 各サブコマンドのハンドラ実装（set / update / delete / rename 等）
  gui/
    GuiConstants.java                # GUI 定数（スロット番号・タイトル名セット・isOurMenu() 等）
    ItemFactory.java                 # CP 関連 ItemStack 生成（ペーパー・ウール・ナビ・ソートボタン等）
    PlayerItemFactory.java           # プレイヤー関連 ItemStack 生成（ヘッド・操作ウール等）
    MenuManager.java                 # 全 GUI 状態管理ファサード（各ハンドラへ委譲）
    MenuRenderer.java                # メニュー表示（インベントリ構築・openInventory）
    MenuClickHandler.java            # インベントリクリックイベント処理
    ChatInputHandler.java            # チャット入力処理（検索・リネーム・説明変更）
    TeleportHandler.java             # テレポート・CP 操作実行（update / delete / clone）
  i18n/
    Messages.java                    # 多言語メッセージ管理（JP / EN）
  listener/
    InventoryClickListener.java      # インベントリクリックイベントハンドラ
    ChatInputListener.java           # チャット入力イベントハンドラ（検索・リネーム・説明変更）
    PlayerListener.java              # アイテム操作・ドロップ防止・参加時言語検出・退出時クリーンアップ
  storage/
    CheckpointStorage.java           # チェックポイントデータの永続化

src/main/resources/
  plugin.yml                         # Bukkit プラグイン設定（コマンド定義・バージョン）

src/test/java/checkpoint/manager/
  CheckpointManagerTest.java         # CheckpointManager の単体テスト

docs/
  README.dev.md                      # このファイル（開発者ガイド）
  ARCHITECTURE.md                    # アーキテクチャ詳細・設計方針

pom.xml                              # Maven ビルド設定
```

---

## バージョン管理

バージョンは **`pom.xml` の1か所のみ** を変更します。  
`plugin.yml` の `version: ${project.version}` は Maven リソースフィルタリングによってビルド時に自動展開されます。

```xml
<!-- pom.xml 7〜9 行目付近 -->
<version>1.6.0</version>  ← ここのみ変更
```

変更後は `mvn process-resources` または `mvn package` を実行して `target/classes/plugin.yml` に反映させてください。

### セマンティックバージョニング方針

| 種別 | 例 | 内容 |
|------|----|------|
| パッチ (`x.y.Z`) | 1.6.0 → 1.6.1 | バグ修正 |
| マイナー (`x.Y.0`) | 1.6.0 → 1.7.0 | 後方互換のある機能追加 |
| メジャー (`X.0.0`) | 1.6.0 → 2.0.0 | 破壊的変更 |

---

## コマンドの追加手順

`/cp <subcommand>` を新たに追加する際は **以下の5か所** を修正してください。

### 1. `manager/CheckpointManager.java`（ロジックが必要な場合）

結果を表す enum は `model/` パッケージに追加し、処理メソッドを `CheckpointManager` に実装します。

### 2. `command/CheckpointCommand.java`

#### ① `onCommand()` の switch 文にケースを追加

```java
case "newsubcmd" -> {
    // 引数バリデーション後、handlers に委譲
    handlers.handleNewSubCmd(player, playerId, /* 引数 */);
}
```

#### ② `onTabComplete()` の候補リストに追加

```java
return List.of("set", "update", "delete", "rename", "description",
               "items", "language", "newsubcmd", "help").stream()...
```

### 2′. `command/SubcommandHandlers.java`

#### ① ハンドラメソッドを追加

```java
void handleNewSubCmd(Player player, UUID playerId, /* 引数 */) {
    // 処理
}
```

#### ② `sendUsage()` / `sendHelp()` にコマンドを追記

`Messages.helpXxx()` にエントリを追加し、`sendHelp()` 内の呼び出し行へ追記します。

### 3. `i18n/Messages.java`

新しいコマンドに関連するすべてのメッセージを日本語・英語で追加します。  
→ [i18n 文字列の追加手順](#i18n-文字列の追加手順) を参照

### 4. `src/main/resources/plugin.yml`

`usage` フィールドに追記します。

```yaml
commands:
  cp:
    usage: |-
      /cp set <name> [-d description]
      /cp newsubcmd <args>   ← 追加
      /cp help
```

追記後は `mvn process-resources` を実行して同期してください。

### 5. `manager/CheckpointManagerTest.java`

新しいロジックに対応する単体テストを追加します。`CheckpointManager` に新メソッドを追加した場合は必ずテストを書いてください。

---

## i18n 文字列の追加手順

すべてのユーザー向け文字列は `i18n/Messages.java` に集約されています。

### 基本パターン

```java
// 引数なし
public static String myMessage(UUID id) {
    return get(id, "日本語のメッセージ", "English message");
}

// 動的な値を含む場合
public static String myMessage(UUID id, String name) {
    return get(id, "『" + name + "』を処理しました。", "Processed '" + name + "'.");
}
```

### ガイドライン

- メソッドはカテゴリ別にまとめてコメントで区切る（`// --- Category ---`）
- 引数には必ず `UUID id`（プレイヤーID）を含める
- 日本語は読点・句点を使い、`『』` で名前を囲む
- 英語は文末にピリオドを付ける。CP 名は `'name'` で囲む
- `/cp help` に表示する行は `HC`（YELLOW）と `HD`（GRAY）定数で色分けする

---

## GUI メニューの追加手順

新しい GUI 画面を追加する場合は以下を修正します。

### 1. `i18n/Messages.java`

新しいタイトル文字列を追加します。

```java
public static String myMenuTitle(UUID id) {
    return get(id, "新しいメニュー", "New Menu");
}
```

### 2. `gui/GuiConstants.java`

タイトルの Set と判定メソッドを追加します。

```java
private static final Set<String> MY_MENU_TITLES = Set.of(
    ChatColor.DARK_AQUA + "新しいメニュー",
    ChatColor.DARK_AQUA + "New Menu"
);
public static boolean isMyMenuTitle(String t) { return MY_MENU_TITLES.contains(t); }
```

`isOurMenu()` にも追加します。

```java
public static boolean isOurMenu(String t) {
    return isGuiTitle(t) || isSortTitle(t) || ... || isMyMenuTitle(t);
}
```

### 3. `gui/MenuRenderer.java` / `gui/ItemFactory.java` / `gui/PlayerItemFactory.java`

メニューを開くメソッドは `MenuRenderer` に追加し、必要な `ItemStack` は `ItemFactory`（CP 関連）または `PlayerItemFactory`（プレイヤー関連）に追加します。  
`MenuRenderer` 内の他のメニュー (`openSortMenu` など) を参考にしてください。

あわせて `gui/MenuClickHandler.java` にクリック処理、`gui/ChatInputHandler.java` にチャット入力処理を追加し、`gui/MenuManager.java` に委譲メソッド（1行）を追加します。

### 4. `listener/InventoryClickListener.java`

新しいタイトルに対応するクリック処理の分岐を追加します。

```java
if (GuiConstants.isMyMenuTitle(title)) {
    // クリック処理
}
```

---

## plugin.yml の同期について

`src/main/resources/plugin.yml` の `version` フィールドは `${project.version}` というプレースホルダーになっています。  
Maven のリソースフィルタリング機能（`pom.xml` の `<filtering>true</filtering>`）によってビルド時に展開され、`target/classes/plugin.yml` に正しいバージョンが書き込まれます。

**`src/main/resources/plugin.yml` を編集した後は必ず以下を実行してください。**

```bash
mvn process-resources
# または
mvn package
```

---

## アーキテクチャ詳細

詳細な設計方針とレイヤー構成は [ARCHITECTURE.md](ARCHITECTURE.md) を参照してください。
