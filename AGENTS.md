# AGENTS.md — リポジトリ情報・AI エージェント向けガイド

## プロジェクト概要

| 項目 | 内容 |
|------|------|
| 名前 | minecraft-checkpoint |
| 種別 | Spigot プラグイン（Minecraft 1.20.1） |
| 言語 | Java 17 |
| ビルド | Maven (`mvn package`) |
| テスト | JUnit 5 (`mvn test`) |
| メインクラス | `io.github.levyxx.checkpoint.CheckpointPlugin` |

---

## ディレクトリ構成

```
src/main/java/.../checkpoint/
  CheckpointPlugin.java    # プラグインエントリポイント（onEnable / onDisable）
  CheckpointManager.java   # チェックポイントのインメモリ管理ロジック
  CheckpointCommand.java   # /cp コマンドの実装（TabExecutor）
src/main/resources/
  plugin.yml               # Bukkit プラグイン設定（コマンド定義・バージョン）
src/test/java/.../checkpoint/
  CheckpointManagerTest.java  # CheckpointManager の単体テスト
pom.xml                    # Maven ビルド設定（バージョン管理）
```

> `target/classes/plugin.yml` はビルド時に `src/main/resources/plugin.yml` から自動生成されます。
> **`src/main/resources/plugin.yml` を編集した後、`mvn package` または `mvn process-resources` を実行して同期してください。**

---

## バージョン変更時に修正する箇所

バージョンは **`pom.xml` の1か所のみ** を変更します。`plugin.yml` の `version` フィールドは Maven の `${project.version}` を参照しているため自動で反映されます。

### pom.xml
```xml
<!-- 7〜9行目付近 -->
<version>1.1.0</version>  ← ここを変更
```

`plugin.yml` の `version: ${project.version}` は Maven のリソースフィルタリング（`pom.xml` の `<build><resources><filtering>true</filtering>`）によってビルド時に自動展開されます。
ビルド後の `target/classes/plugin.yml` に正しいバージョンが反映されていることを `mvn process-resources` で確認してください。

### バージョニング方針（セマンティックバージョニング）

| 種別 | 例 | 変更内容 |
|------|----|---------|
| パッチ (`x.y.Z`) | 1.1.0 → 1.1.1 | バグ修正 |
| マイナー (`x.Y.0`) | 1.1.0 → 1.2.0 | 後方互換のある機能追加 |
| メジャー (`X.0.0`) | 1.1.0 → 2.0.0 | 破壊的変更 |

---

## コマンド追加時に修正する箇所

`/cp <subcommand>` を新たに追加する際は **以下の4か所** を修正してください。

### 1. `CheckpointManager.java`（必要な場合）
ロジックを追加します。結果を表す enum（例: `RenameResult`）や新メソッドはこのクラスに実装します。

### 2. `CheckpointCommand.java`

#### ① `onCommand()` の switch 文にケースを追加
```java
case "newsubcmd" -> {
    // 引数バリデーション・ハンドラ呼び出し
}
```

#### ② ハンドラメソッドを追加
```java
private void handleNewSubCmd(Player player, UUID playerId, /* 引数 */) {
    // 処理
}
```

#### ③ `sendUsage()` にコマンドを追記
```java
sender.sendMessage(... + " /cp newsubcmd <引数>" + ...);
```

#### ④ `onTabComplete()` の候補リストに追加
```java
// 第1引数の候補
return List.of("set", "update", "delete", "rename", "newsubcmd", "items").stream()...

// 必要なら第2引数以降の補完も追加
```

### 3. `src/main/resources/plugin.yml`

`usage` フィールドに新しいコマンドの使い方を追記します。

```yaml
commands:
  cp:
    usage: |-
      /cp set <name>
      /cp update <name>
      /cp delete <name>
      /cp rename <old-name> <new-name>
      /cp newsubcmd <args>   ← 追加
      /cp items
```

> 追記後は `mvn process-resources`（または `mvn package`）を実行して `target/classes/plugin.yml` に反映してください。

### 4. `CheckpointManagerTest.java`

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
