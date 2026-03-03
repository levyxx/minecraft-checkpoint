# MEMORY.md — AI エージェントの学習メモ

## プロジェクト構造

- Maven マルチモジュール構成（parent → common, plugin-1.8, plugin-1.12, plugin-1.21）
- 共通コードは `common/` モジュール、バージョン固有コードは各 `plugin-*/` モジュール
- `maven-shade-plugin` で common JAR を各バージョン JAR に同梱

## バージョン互換 (VersionCompat)

- `VersionCompat` は static singleton パターン（`init()` / `get()`）
- `CompatLegacy` は pre-1.13 (1.8/1.12) の共通基底クラス
- pre-1.13: 色付きアイテムは `Material.valueOf("WOOL")` + データ値 `(short)` で生成
- pre-1.13: アイテムタグは隠しロアマーカー（`§0§k` + ID 文字列）
- 1.21: `PersistentDataContainer` + `NamespacedKey` でアイテムタグ
- Enchantment フィールド名: `Enchantment.LUCK`（1.20.1 API）
- 1.8 Sound 名: `valueOf("CLICK")`, `valueOf("ITEM_PICKUP")` 等（1.9 以降とは異なる）
- `Material.SNOW_BALL` (1.8/1.12) vs `Material.SNOWBALL` (1.13+)
- `Material.BOOK_AND_QUILL` (1.8/1.12) vs `Material.WRITABLE_BOOK` (1.13+)

## 主な設計判断

- 全モジュールは Spigot 1.20.1 API でコンパイル（`Material.valueOf()` で旧名を実行時解決）
- `NamespacedKey` / `PersistentDataContainer` は common コードに直接 import しない
- `player.getLocale()` は 1.12+ のみ直接利用可能、1.8 はリフレクション
- `EquipmentSlot.HAND` は 1.9+ のみ、1.8 は常に mainHand=true
- CP リスト表示アイテム: ダイヤモンド (`Material.DIAMOND`)
- ページ送り矢印: 通常の矢 (`Material.ARROW`)
- ソートボタン: 全バージョン `COMPASS`（SPYGLASS は 1.8/1.12 に存在しないため統一）

## Java バージョン互換

- common / plugin-1.8 / plugin-1.12: `--release 8`（Java 8 バイトコード, major version 52）
- plugin-1.21: `--release 17`（Java 17 バイトコード, major version 61）
- **common モジュールのコードは Java 8 構文のみ使用すること**
  - `record` → 通常の `static class`（equals/hashCode/toString 手動実装）
  - `switch` 式・アロー → 従来の `switch` + `break`
  - `instanceof` パターンマッチ → `instanceof` + キャスト
  - `List.of()` / `Set.of()` / `Map.of()` → `Arrays.asList()` / `new HashSet<>()` / `new HashMap<>()`
  - `var` → 明示的な型宣言
  - テキストブロック `"""` → 通常の文字列連結

## ビルド

```bash
mvn clean package    # 3つの JAR を生成
mvn test             # 43 テスト (common のみ)
```

生成物:
- `plugin-1.8/target/minecraft-checkpoint-1.8.jar`（Java 8 互換）
- `plugin-1.12/target/minecraft-checkpoint-1.12.jar`（Java 8 互換）
- `plugin-1.21/target/minecraft-checkpoint-1.21.jar`（Java 17）
