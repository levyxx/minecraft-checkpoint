# AGENTS.md — リポジトリ情報・AI エージェント向けガイド

詳細な開発者ガイドは [docs/README.dev.md](docs/README.dev.md)、アーキテクチャ詳細は [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) を参照してください。

---

## プロジェクト概要

| 項目 | 内容 |
|------|------|
| 名前 | minecraft-checkpoint |
| 種別 | Spigot プラグイン（Minecraft 1.8.x / 1.12.x / 1.21.x） |
| 言語 | Java 17 |
| ビルド | Maven マルチモジュール (`mvn package`) |
| テスト | JUnit 5 (`mvn test`) |
| メインクラス | `checkpoint.CheckpointPlugin`（各バージョンモジュール） |
| ベースクラス | `checkpoint.CheckpointPluginBase`（common モジュール） |
| テスト件数 | 43 件（`CheckpointManagerTest`） |

---

## ディレクトリ構成

```
pom.xml                              # 親 POM（マルチモジュール定義）
common/
  pom.xml                            # 共通モジュール POM
  src/main/java/checkpoint/
    CheckpointPluginBase.java        # プラグインベースクラス（onEnable / onDisable）
    compat/
      VersionCompat.java             # バージョン互換抽象クラス
      CompatLegacy.java              # pre-1.13 共通実装（1.8/1.12 共通）
    model/
      Checkpoint.java                # チェックポイントデータモデル（不変クラス）
      SortOrder.java                 # CP ソート順 enum（7 種類）
      PlayerSortOrder.java           # プレイヤーソート順 enum（7 種類）
      ClearSortOrder.java            # クリアソート順 enum
      RenameResult.java              # リネーム結果 enum
    manager/
      CheckpointManager.java         # チェックポイントのインメモリ CRUD・ソート・検索
    command/
      CheckpointCommand.java         # /cp コマンドの実装（TabExecutor・ディスパッチャ）
      SubcommandHandlers.java        # 各サブコマンドのハンドラ実装
    gui/
      GuiConstants.java              # GUI 定数（スロット番号・タイトル Set・isOurMenu() 等）
      ItemFactory.java               # GUI 用 ItemStack 生成（CP アイテム・ナビ・ソート等）
      PlayerItemFactory.java         # プレイヤー関連 ItemStack 生成（ヘッド・操作ウール等）
      MenuManager.java               # 全 GUI 状態管理・ファサード（ハンドラへ委譲）
      MenuRenderer.java              # メニュー表示（インベントリ構築・表示）
      MenuClickHandler.java          # インベントリクリックハンドラ
      ChatInputHandler.java          # チャット入力ハンドラ（検索・リネーム・説明）
      TeleportHandler.java           # テレポート・CP 操作実行
    i18n/
      Messages.java                  # 多言語メッセージ管理（JP / EN）
    listener/
      InventoryClickListener.java    # インベントリクリックイベントハンドラ
      ChatInputListener.java         # チャット入力イベントハンドラ（検索・リネーム・説明）
      PlayerListener.java            # アイテム操作・ドロップ防止・参加時言語検出・退出時クリーンアップ
    storage/
      CheckpointStorage.java         # チェックポイントデータの永続化
  src/test/java/checkpoint/manager/
    CheckpointManagerTest.java       # CheckpointManager の単体テスト（43 件）
plugin-1.8/
  pom.xml                            # 1.8 モジュール POM（shade で common を同梱）
  src/main/java/checkpoint/
    CheckpointPlugin.java            # 1.8 用エントリポイント
    compat/Compat1_8.java            # 1.8 用互換実装
  src/main/resources/plugin.yml      # 1.8 用プラグイン設定
plugin-1.12/
  pom.xml                            # 1.12 モジュール POM
  src/main/java/checkpoint/
    CheckpointPlugin.java            # 1.12 用エントリポイント
    compat/Compat1_12.java           # 1.12 用互換実装
  src/main/resources/plugin.yml      # 1.12 用プラグイン設定
plugin-1.21/
  pom.xml                            # 1.21 モジュール POM
  src/main/java/checkpoint/
    CheckpointPlugin.java            # 1.21 用エントリポイント
    compat/Compat1_21.java           # 1.21 用互換実装（PersistentDataContainer 使用）
  src/main/resources/plugin.yml      # 1.21 用プラグイン設定（api-version: '1.20'）
docs/
  README.dev.md                      # 開発者向けガイド（ビルド・構成・拡張手順）
  ARCHITECTURE.md                    # アーキテクチャ・設計方針
```

> 新たに機能が追加されたり、コードが嵩むような変更が起こる場合には、ディレクトリ構成やファイル構成を見直し、適宜リファクタリングしてください。リファクタリングの際はdocs/ディレクトリのドキュメントやREADME.md、AGENTS.md の内容も最新の状態に保つようにしてください。
> 各バージョンの `plugin.yml` は `plugin-{version}/src/main/resources/plugin.yml` にあります。
> **`plugin.yml` を編集した後、`mvn package` を実行して各 JAR に反映してください。**

---

## バージョン変更時に修正する箇所

バージョンは **親 `pom.xml` の1か所のみ** を変更します。各 `plugin.yml` の `version` フィールドは Maven の `${project.version}` を参照しているため自動で反映されます。

```xml
<!-- pom.xml 9 行目付近 -->
<version>2.1.2</version>  ← ここを変更
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

### 4. `plugin-{version}/src/main/resources/plugin.yml`

各バージョンの `usage` フィールドに新しいコマンドの使い方を追記します。

```yaml
commands:
  cp:
    usage: |-
      /cp set <name> [-d description]
      /cp newsubcmd <args>   ← 追加
      /cp help
```

> 追記後は `mvn clean package` を実行して各 JAR に反映してください。

### 5. `manager/CheckpointManagerTest.java`

新しいロジックに対応する単体テストを追加します。`CheckpointManager` に新メソッドを追加した場合は必ずテストを書いてください。
---

## ビルド・テストコマンド

```bash
# テストのみ実行
mvn test

# JAR ビルド（target/*.jar が生成される）
mvn clean package

# リソースのみ再生成（plugin.yml を target/classes/ に同期する際など）
mvn process-resources
```

ビルド成功時に以下の JAR が生成されます：
- `plugin-1.8/target/minecraft-checkpoint-1.8.jar`
- `plugin-1.12/target/minecraft-checkpoint-1.12.jar`
- `plugin-1.21/target/minecraft-checkpoint-1.21.jar`

---

## MEMORY.md の活用

AI エージェントが学んだ内容を記録するためのファイルです。エージェントがプロジェクトの構造やルールを理解しやすくするために、重要な情報や変更点などをここに記録してください。困ったときに適切にこのファイルを参照することで、エージェントがより効率的にタスクを遂行できるようになります。