# minecraft-checkpoint

Minecraft Java Edition (Spigot/Paper 1.20) 用のチェックポイント機能プラグインです。

## ✨ 機能

### アイテム操作
| アイテム | 操作 | 動作 |
|---------|------|------|
| スライムボール | 右クリック | 現在位置をクイックチェックポイントとして保存 |
| ネザースター | 右クリック | 最後に設定したチェックポイントへテレポート |
| ネザースター | 左クリック | チェックポイント一覧 GUI を開く |
| 海洋の心 | 右クリック | チェックポイント一覧 GUI を開く |
| 羽 | 右クリック | アドベンチャー⇔クリエイティブのゲームモード切り替え（スペクテイター時は変化なし） |

### チェックポイント一覧 GUI
- チェスト型 UI（54 スロット）でページ切り替えしながら名前付きチェックポイントを一覧表示
- **検索**：名前・説明文で絞り込み
- **ソート**：名前昇順 / 降順、作成日時昇順 / 降順、更新日時昇順 / 降順、距離昇順 の 7 種類
- **CP 操作メニュー**：紙をクリックするとポップアップ
  - 自分の CP：テレポート / 座標更新 / リネーム / 説明文変更 / 削除
  - 他プレイヤーの CP（プレイヤー選択メニュー経由）：テレポート / クローン（自分の CP として複製）
- 同 GUI からオンラインプレイヤーの一覧を開き、他プレイヤーの CP を閲覧・クローン可能

### コマンド
| コマンド | 説明 |
|---------|------|
| `/cp set <名前> [説明]` | 現在位置を名前付き CP として保存（説明文は省略可） |
| `/cp update <名前>` | 既存 CP の座標を現在位置で上書き |
| `/cp delete <名前>` | CP を削除 |
| `/cp rename <旧名> <新名>` | CP の名前を変更 |
| `/cp description <名前> <説明>` | CP に説明文を設定（スペース可） |
| `/cp items` | チェックポイント関連アイテムを一括付与 |
| `/cp help` | コマンド一覧を表示 |

### その他
- **作成日時 / 更新日時**を各 CP に記録し、一覧にも表示
- チェックポイントが未保存の場合や保存先ワールドが読み込まれていない場合はプレイヤーへ通知

## ⚙️ 動作要件

- Java 17 以上
- Spigot / Paper 1.20 互換サーバー

## 🔧 ビルド手順

```bash
mvn clean package
```

`target/minecraft-checkpoint-1.4.0.jar` をサーバーの `plugins/` ディレクトリに配置し、サーバーを再起動してください。

## 🎮 クイックスタート

1. サーバーを起動してプレイヤーとしてログイン
2. `/cp items` でネザースター・スライムボール・羽・海洋の心を受け取る
3. スライムボールを右クリックしてクイック CP を保存
4. ネザースターを右クリックで最後の CP へ瞬時にテレポート
5. ネザースター左クリック or 海洋の心右クリックで一覧 GUI を開く

## 🗂️ プロジェクト構成

```
src/main/java/checkpoint/
  CheckpointPlugin.java              # エントリポイント（onEnable / onDisable）
  model/
    Checkpoint.java                  # CP データモデル（不変クラス）
    SortOrder.java                   # ソート順 enum（7 種類）
    RenameResult.java                # リネーム結果 enum
  manager/
    CheckpointManager.java           # インメモリ CRUD・ソート・検索（Bukkit 非依存）
  command/
    CheckpointCommand.java           # /cp コマンド実装（TabExecutor）
  gui/
    GuiConstants.java                # GUI 定数（スロット番号・タイトル等）
    ItemFactory.java                 # GUI 用 ItemStack 生成
    MenuManager.java                 # 全 GUI の状態管理・メニュー表示・CP 操作
  listener/
    InventoryClickListener.java      # インベントリクリックイベントハンドラ
    ChatInputListener.java           # チャット入力ハンドラ（検索・リネーム・説明）
    PlayerListener.java              # アイテム操作・ドロップ防止・インベントリクローズ
src/main/resources/
  plugin.yml
src/test/java/checkpoint/manager/
  CheckpointManagerTest.java         # CheckpointManager の単体テスト（20 件）
```

## 🧪 テスト

```bash
mvn test
```
