# AGENTS.md — リポジトリ情報・AI エージェント向けガイド

詳細な開発者ガイドは [docs/README.dev.md](docs/README.dev.md)、アーキテクチャ詳細は [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) を参照してください。

---

## プロジェクト概要

| 項目 | 内容 |
|------|------|
| 名前 | minecraft-checkpoint |
| 種別 | Spigot プラグイン（Minecraft 1.20.1） |
| 言語 | Java 17 |
| ビルド | Maven (`mvn package`) |
| テスト | JUnit 5 (`mvn test`) |
| メインクラス | `checkpoint.CheckpointPlugin` |
| テスト件数 | 43 件（`CheckpointManagerTest`） |

---

## ディレクトリ構成

```
src/main/java/checkpoint/
  CheckpointPlugin.java              # プラグインエントリポイント（onEnable / onDisable）
  model/
    Checkpoint.java                  # チェックポイントデータモデル（不変クラス）
    SortOrder.java                   # CP ソート順 enum（7 種類）
    PlayerSortOrder.java             # プレイヤーソート順 enum（7 種類）
    ClearSortOrder.java              # クリアソート順 enum
    RenameResult.java                # リネーム結果 enum
  manager/
    CheckpointManager.java           # チェックポイントのインメモリ CRUD・ソート・検索
  command/
    CheckpointCommand.java           # /cp コマンドの実装（TabExecutor・ディスパッチャ）
    SubcommandHandlers.java          # 各サブコマンドのハンドラ実装
  gui/
    GuiConstants.java                # GUI 定数（スロット番号・タイトル Set・isOurMenu() 等）
    ItemFactory.java                 # GUI 用 ItemStack 生成（CP アイテム・ナビ・ソート等）
    PlayerItemFactory.java           # プレイヤー関連 ItemStack 生成（ヘッド・操作ウール等）
    MenuManager.java                 # 全 GUI 状態管理・ファサード（ハンドラへ委譲）
    MenuRenderer.java                # メニュー表示（インベントリ構築・表示）
    MenuClickHandler.java            # インベントリクリックハンドラ
    ChatInputHandler.java            # チャット入力ハンドラ（検索・リネーム・説明）
    TeleportHandler.java             # テレポート・CP 操作実行
  i18n/
    Messages.java                    # 多言語メッセージ管理（JP / EN）
  listener/
    InventoryClickListener.java      # インベントリクリックイベントハンドラ
    ChatInputListener.java           # チャット入力イベントハンドラ（検索・リネーム・説明）
    PlayerListener.java              # アイテム操作・ドロップ防止・参加時言語検出・退出時クリーンアップ
  storage/
    CheckpointStorage.java           # チェックポイントデータの永続化
src/main/resources/
  plugin.yml                         # Bukkit プラグイン設定（コマンド定義・バージョン）
src/test/java/checkpoint/manager/
  CheckpointManagerTest.java         # CheckpointManager の単体テスト（28 件）
docs/
  README.dev.md                      # 開発者向けガイド（ビルド・構成・拡張手順）
  ARCHITECTURE.md                    # アーキテクチャ・設計方針
pom.xml                              # Maven ビルド設定（バージョン管理）
```

> 新たに機能が追加されたり、コードが嵩むような変更が起こる場合には、ディレクトリ構成やファイル構成を見直し、適宜リファクタリングしてください。リファクタリングの際はdocs/ディレクトリのドキュメントやREADME.md、AGENTS.md の内容も最新の状態に保つようにしてください。
> `target/classes/plugin.yml` はビルド時に `src/main/resources/plugin.yml` から自動生成されます。  
> **`src/main/resources/plugin.yml` を編集した後、`mvn package` または `mvn process-resources` を実行して同期してください。**

---

## バージョン変更時に修正する箇所

バージョンは **`pom.xml` の1か所のみ** を変更します。`plugin.yml` の `version` フィールドは Maven の `${project.version}` を参照しているため自動で反映されます。

```xml
<!-- pom.xml 7〜9 行目付近 -->
<version>1.6.0</version>  ← ここを変更
```

### バージョニング方針（セマンティックバージョニング）

| 種別 | 例 | 変更内容 |
|------|----|---------|
| パッチ (`x.y.Z`) | 1.6.0 → 1.6.1 | バグ修正 |
| マイナー (`x.Y.0`) | 1.6.0 → 1.7.0 | 後方互換のある機能追加 |
| メジャー (`X.0.0`) | 1.6.0 → 2.0.0 | 破壊的変更 |

---

## コマンド追加時に修正する箇所

`/cp <subcommand>` を新たに追加する際は **以下の6か所** を修正してください。

### 1. `manager/CheckpointManager.java`（必要な場合）
ロジックを追加します。結果を表す enum は `model/` パッケージに、新メソッドはこのクラスに実装します。

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
`Messages.helpXxx()` にエントリを追加し、`sendHelp()` で呼び出す。

### 3. `i18n/Messages.java`

新しいコマンドに関連するすべてのメッセージを日本語・英語で追加します。

```java
public static String helpNewSubCmd(UUID id, String l) { return get(id,
    HC + "/" + l + " newsubcmd <引数>" + HD + "  説明",
    HC + "/" + l + " newsubcmd <args>" + HD + "  Description"); }
```

### 4. `src/main/resources/plugin.yml`

`usage` フィールドに新しいコマンドの使い方を追記します。

```yaml
commands:
  cp:
    usage: |-
      /cp set <name> [-d description]
      /cp newsubcmd <args>   ← 追加
      /cp help
```

> 追記後は `mvn process-resources`（または `mvn package`）を実行して `target/classes/plugin.yml` に反映してください。

### 5. `manager/CheckpointManagerTest.java`

新しいロジックに対応する単体テストを追加します。`CheckpointManager` に新メソッドを追加した場合は必ずテストを書いてください。

---

## ビルド・テストコマンド

```bash
# テストのみ実行
mvn test

# JAR ビルド（target/*.jar が生成される）
mvn package

# リソースのみ再生成（plugin.yml を target/classes/ に同期する際など）
mvn process-resources
```
