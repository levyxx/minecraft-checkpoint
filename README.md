# minecraft-checkpoint

> [日本語](#日本語) | [English](#english)

---

## 日本語

Minecraft Java Edition (Spigot/Paper **1.8.x / 1.12.x / 1.21.x**) 用のチェックポイント機能プラグインです。  
プレイヤーごとに名前付きチェックポイントを保存・テレポート・管理でき、GUI メニューや他プレイヤーの CP 閲覧・クローン機能を備えています。  
**日本語・英語の2言語に対応**しており、クライアントのロケールに応じて自動で切り替わります。  
チェックポイントデータはサーバー再起動後も**永続化**されます。

### ✨ 機能

#### アイテム操作
| アイテム | 操作 | 動作 |
|---------|------|------|
| スライムボール | 右クリック | 現在位置をクイックチェックポイントとして保存 |
| ネザースター | 右クリック | 最後に設定したチェックポイントへテレポート |
| ネザースター | 左クリック | チェックポイント一覧 GUI を開く |
| ダイヤモンド | 右クリック | チェックポイント一覧 GUI を開く |
| 羽 | 右クリック | アドベンチャー⇔クリエイティブのゲームモード切り替え（スペクテイター時は変化なし） |

テレポート時はプレイヤーの速度が自動リセットされ、高速移動や落下による事故を防ぎます。

#### チェックポイント一覧 GUI
- チェスト型 UI（54 スロット）でページ切り替えしながら名前付きチェックポイントを一覧表示
- **検索**：名前・説明文で絞り込み
- **ソート**（望遠鏡ボタン）：名前昇順 / 降順、作成日時昇順 / 降順、更新日時昇順 / 降順、距離昇順 の 7 種類
- **表示モード切替**（上段スロット2）：スノーボール = 紙表示 / マグマクリーム = クリア状況ウール表示
  - ライムウール = クリア済み、赤ウール = 未クリア
- **クリアソート**（上段スロット6・ブレイズパウダー）：クリア状況でグループ化してソート
  - 左クリック：クリア済み先 / 未クリア先 を選択するサブメニューを開く
  - 右クリック：クリアソートを解除
  - クリアソート有効時は、グループ内を通常ソート順でさらに並び替え
- **CP 操作メニュー**：紙またはウールを右クリックするとポップアップ
  - 自分の CP：テレポート / 座標更新 / リネーム / 説明文変更 / 削除
  - 他プレイヤーの CP（プレイヤー選択メニュー経由）：テレポート / クローン（自分の CP として複製）
- 各 CP のツールチップに **X / Y / Z / F（向き）** を小数点以下5桁で表示（ピッチは非表示）
- クリア済みステータスをツールチップに表示

#### クリア済みチェックポイント管理
- `/cp did`：現在選択中の名前付き CP をクリア済みにマークする
- `/cp didnt`：現在選択中の名前付き CP のクリア済みマークを解除する
- クリア状況はサーバー再起動後も保持されます

#### プレイヤー選択メニュー
- 全プレイヤーの一覧を開き、他プレイヤーの CP を閲覧・クローン可能
- **プレイヤーソート**：名前昇順 / 降順、クローンされた数順、自分がクローンした数順、距離昇順、最終アクティビティ順（昇順 / 降順） の 7 種類
- プレイヤーヘッドに CP 数・最終アクティビティ・クローン統計を表示

#### コマンド
| コマンド | 説明 |
|---------|------|
| `/cp set <名前> [-d 説明]` | 現在位置を名前付き CP として保存（名前にスペース可、説明文は省略可） |
| `/cp update <名前>` | 既存 CP の座標を現在位置で上書き |
| `/cp delete <名前>` | CP を削除 |
| `/cp rename <旧名> -n <新名>` | CP の名前を変更（名前にスペース可） |
| `/cp description <名前> -d <説明>` | CP に説明文を設定（名前にスペース可） |
| `/cp items` | チェックポイント関連アイテムを一括付与 |
| `/cp did` | 現在選択中の名前付き CP をクリア済みにマーク |
| `/cp didnt` | 現在選択中の名前付き CP のクリア済みマークを解除 |
| `/cp language <ja\|en>` | 表示言語を日本語 / 英語に切り替え |
| `/cp help` | コマンド一覧を表示 |

#### データ永続化
- チェックポイントデータ（名前付き CP・クイック CP・選択状態・クローン履歴・**クリア済み状態**）を `plugins/minecraft-checkpoint/checkpoints.yml` に自動保存
- データ変更時に非同期で保存されるためサーバー負荷を最小限に抑えます

#### 多言語対応（i18n）
- **日本語**と**英語**の 2 言語をサポート
- プレイヤーの Minecraft クライアントロケールに基づいて自動検出（`ja_*` → 日本語、それ以外 → 英語）
- `/cp language <ja|en>` で手動切り替え可能

#### その他
- **作成日時 / 更新日時**を各 CP に記録し、一覧にも表示
- チェックポイントが未保存の場合や保存先ワールドが読み込まれていない場合はプレイヤーへ通知

### ⚙️ 動作要件

- Java 17 以上
- Spigot / Paper 1.8.x / 1.12.x / 1.21.x 互換サーバー

### 📦 インストール

1. [Releases](../../releases) から対応バージョンの JAR をダウンロード
   - `minecraft-checkpoint-1.8.jar` — Minecraft 1.8.x 用
   - `minecraft-checkpoint-1.12.jar` — Minecraft 1.12.x 用
   - `minecraft-checkpoint-1.21.jar` — Minecraft 1.21.x 用
2. サーバーの `plugins/` ディレクトリに配置
3. サーバーを再起動（または `/reload confirm`）

> 開発者向けのビルド手順は [docs/README.dev.md](docs/README.dev.md) を参照してください。

### 🎮 クイックスタート

1. サーバーを起動してプレイヤーとしてログイン
2. `/cp items` でネザースター・スライムボール・羽・ダイヤモンドを受け取る
3. スライムボールを右クリックしてクイック CP を保存
4. ネザースターを右クリックで最後の CP へ瞬時にテレポート
5. ネザースター左クリック or ダイヤモンド右クリックで一覧 GUI を開く
6. GUI 上段スロット2のスノーボールをクリックして表示モードを切り替える
7. CP を攻略したら `/cp did` でクリア済みにマーク
8. `/cp language en` で英語表示に切り替え

---

## English

A checkpoint plugin for Minecraft Java Edition (Spigot/Paper **1.8.x / 1.12.x / 1.21.x**).  
Players can save, teleport to, and manage named checkpoints per player, with a chest-based GUI, cross-player checkpoint browsing, and cloning.  
**Supports Japanese and English** — the language is auto-detected from the player's Minecraft client locale.  
All checkpoint data is **persisted** across server restarts.

### ✨ Features

#### Item Controls
| Item | Action | Behavior |
|------|--------|----------|
| Slime Ball | Right-click | Save current location as a quick checkpoint |
| Nether Star | Right-click | Teleport to the last-set checkpoint |
| Nether Star | Left-click | Open the checkpoint list GUI |
| Diamond | Right-click | Open the checkpoint list GUI |
| Feather | Right-click | Toggle Adventure ⇔ Creative game mode (no effect in Spectator) |

Player velocity is automatically reset on teleport to prevent accidents from high-speed movement or fall damage.

#### Checkpoint List GUI
- Chest-based UI (54 slots) with pagination for named checkpoints
- **Search**: Filter by name or description
- **Sort** (spyglass button): Name asc/desc, created asc/desc, updated asc/desc, distance asc — 7 options
- **Display mode toggle** (top row slot 2): Snowball = paper view / Magma Cream = clear-status wool view
  - Lime wool = cleared, Red wool = not cleared
- **Clear sort** (top row slot 6, blaze powder): Group checkpoints by clear status
  - Left-click: Open submenu to select Cleared First / Uncleared First
  - Right-click: Remove clear sort
  - Within each group, items are further sorted by the active spyglass sort order
- **CP Operation Menu**: Right-click a paper or wool item to open
  - Own CPs: Teleport / Update coordinates / Rename / Edit description / Delete
  - Other players' CPs (via Player Select Menu): Teleport / Clone (copy as your own CP)
- Each CP tooltip shows **X / Y / Z / F (facing)** to 5 decimal places (pitch hidden)
- Cleared status is shown in each CP's tooltip

#### Cleared Checkpoint Tracking
- `/cp did`: Mark the currently selected named CP as cleared
- `/cp didnt`: Remove the cleared mark from the currently selected named CP
- Clear status is preserved across server restarts

#### Player Select Menu
- Browse all players' checkpoints and clone them
- **Player Sort**: Name asc/desc, cloned-count desc, cloned-by-me desc, distance asc, last activity asc/desc — 7 options
- Player heads display CP count, last activity, and clone statistics

#### Commands
| Command | Description |
|---------|-------------|
| `/cp set <name> [-d description]` | Save current location as a named CP (spaces allowed in name, description optional) |
| `/cp update <name>` | Overwrite existing CP coordinates with current location |
| `/cp delete <name>` | Delete a CP |
| `/cp rename <old> -n <new>` | Rename a CP (spaces allowed in names) |
| `/cp description <name> -d <desc>` | Set a CP description (spaces allowed in name) |
| `/cp items` | Receive checkpoint utility items |
| `/cp did` | Mark the currently selected named CP as cleared |
| `/cp didnt` | Remove the cleared mark from the currently selected named CP |
| `/cp language <ja\|en>` | Switch display language to Japanese / English |
| `/cp help` | Show command list |

#### Data Persistence
- All checkpoint data (named CPs, quick CPs, selection state, clone history, **cleared status**) is auto-saved to `plugins/minecraft-checkpoint/checkpoints.yml`
- Saving is done asynchronously on data change to minimize server load

#### Internationalization (i18n)
- **Japanese** and **English** fully supported
- Auto-detected from the player's Minecraft client locale (`ja_*` → Japanese, otherwise → English)
- Override manually with `/cp language <ja|en>`

#### Other
- **Created / Updated timestamps** are recorded per CP and shown in the list
- Players are notified when no checkpoint exists or the target world is unloaded

### ⚙️ Requirements

- Java 17+
- Spigot / Paper 1.8.x / 1.12.x / 1.21.x compatible server

### 📦 Installation

1. Download the JAR for your server version from [Releases](../../releases)
   - `minecraft-checkpoint-1.8.jar` — for Minecraft 1.8.x
   - `minecraft-checkpoint-1.12.jar` — for Minecraft 1.12.x
   - `minecraft-checkpoint-1.21.jar` — for Minecraft 1.21.x
2. Place it in the server's `plugins/` directory
3. Restart the server (or run `/reload confirm`)

> For build instructions, see [docs/README.dev.md](docs/README.dev.md).

### 🎮 Quick Start

1. Start the server and join as a player
2. Run `/cp items` to receive the Nether Star, Slime Ball, Feather, and Diamond
3. Right-click the Slime Ball to save a quick checkpoint
4. Right-click the Nether Star to teleport instantly to the last CP
5. Left-click the Nether Star or right-click the Diamond to open the list GUI
6. Click the Snowball in the top row (slot 2) of the GUI to toggle display mode
7. After clearing a CP, run `/cp did` to mark it as cleared
8. Run `/cp language ja` to switch to Japanese
