# minecraft-checkpoint

> [日本語](#日本語) | [English](#english)

---

## 日本語

Minecraft Java Edition (Spigot/Paper 1.20) 用のチェックポイント機能プラグインです。  
プレイヤーごとに名前付きチェックポイントを保存・テレポート・管理でき、GUI メニューや他プレイヤーの CP 閲覧・クローン機能を備えています。  
**日本語・英語の2言語に対応**しており、クライアントのロケールに応じて自動で切り替わります。

### ✨ 機能

#### アイテム操作
| アイテム | 操作 | 動作 |
|---------|------|------|
| スライムボール | 右クリック | 現在位置をクイックチェックポイントとして保存 |
| ネザースター | 右クリック | 最後に設定したチェックポイントへテレポート |
| ネザースター | 左クリック | チェックポイント一覧 GUI を開く |
| 海洋の心 | 右クリック | チェックポイント一覧 GUI を開く |
| 羽 | 右クリック | アドベンチャー⇔クリエイティブのゲームモード切り替え（スペクテイター時は変化なし） |

#### チェックポイント一覧 GUI
- チェスト型 UI（54 スロット）でページ切り替えしながら名前付きチェックポイントを一覧表示
- **検索**：名前・説明文で絞り込み
- **ソート**：名前昇順 / 降順、作成日時昇順 / 降順、更新日時昇順 / 降順、距離昇順 の 7 種類
- **CP 操作メニュー**：紙をクリックするとポップアップ
  - 自分の CP：テレポート / 座標更新 / リネーム / 説明文変更 / 削除
  - 他プレイヤーの CP（プレイヤー選択メニュー経由）：テレポート / クローン（自分の CP として複製）

#### プレイヤー選択メニュー
- オンラインプレイヤーの一覧を開き、他プレイヤーの CP を閲覧・クローン可能
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
| `/cp language <ja\|en>` | 表示言語を日本語 / 英語に切り替え |
| `/cp help` | コマンド一覧を表示 |

#### 多言語対応（i18n）
- **日本語**と**英語**の 2 言語をサポート
- プレイヤーの Minecraft クライアントロケールに基づいて自動検出（`ja_*` → 日本語、それ以外 → 英語）
- `/cp language <ja|en>` で手動切り替え可能

#### その他
- **作成日時 / 更新日時**を各 CP に記録し、一覧にも表示
- チェックポイントが未保存の場合や保存先ワールドが読み込まれていない場合はプレイヤーへ通知

### ⚙️ 動作要件

- Java 17 以上
- Spigot / Paper 1.20 互換サーバー

### 📦 インストール

1. [Releases](../../releases) から最新の `minecraft-checkpoint-<version>.jar` をダウンロード
2. サーバーの `plugins/` ディレクトリに配置
3. サーバーを再起動（または `/reload confirm`）

> 開発者向けのビルド手順は [docs/README.dev.md](docs/README.dev.md) を参照してください。

### 🎮 クイックスタート

1. サーバーを起動してプレイヤーとしてログイン
2. `/cp items` でネザースター・スライムボール・羽・海洋の心を受け取る
3. スライムボールを右クリックしてクイック CP を保存
4. ネザースターを右クリックで最後の CP へ瞬時にテレポート
5. ネザースター左クリック or 海洋の心右クリックで一覧 GUI を開く
6. `/cp language en` で英語表示に切り替え

---

## English

A checkpoint plugin for Minecraft Java Edition (Spigot/Paper 1.20).  
Players can save, teleport to, and manage named checkpoints per player, with a chest-based GUI, cross-player checkpoint browsing, and cloning.  
**Supports Japanese and English** — the language is auto-detected from the player's Minecraft client locale.

### ✨ Features

#### Item Controls
| Item | Action | Behavior |
|------|--------|----------|
| Slime Ball | Right-click | Save current location as a quick checkpoint |
| Nether Star | Right-click | Teleport to the last-set checkpoint |
| Nether Star | Left-click | Open the checkpoint list GUI |
| Heart of the Sea | Right-click | Open the checkpoint list GUI |
| Feather | Right-click | Toggle Adventure ⇔ Creative game mode (no effect in Spectator) |

#### Checkpoint List GUI
- Chest-based UI (54 slots) with pagination for named checkpoints
- **Search**: Filter by name or description
- **Sort**: Name asc/desc, created asc/desc, updated asc/desc, distance asc — 7 options
- **CP Operation Menu**: Click a paper item to open
  - Own CPs: Teleport / Update coordinates / Rename / Edit description / Delete
  - Other players' CPs (via Player Select Menu): Teleport / Clone (copy as your own CP)

#### Player Select Menu
- Browse online players' checkpoints and clone them
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
| `/cp language <ja\|en>` | Switch display language to Japanese / English |
| `/cp help` | Show command list |

#### Internationalization (i18n)
- **Japanese** and **English** fully supported
- Auto-detected from the player's Minecraft client locale (`ja_*` → Japanese, otherwise → English)
- Override manually with `/cp language <ja|en>`

#### Other
- **Created / Updated timestamps** are recorded per CP and shown in the list
- Players are notified when no checkpoint exists or the target world is unloaded

### ⚙️ Requirements

- Java 17+
- Spigot / Paper 1.20-compatible server

### 📦 Installation

1. Download the latest `minecraft-checkpoint-<version>.jar` from [Releases](../../releases)
2. Place it in the server's `plugins/` directory
3. Restart the server (or run `/reload confirm`)

> For build instructions, see [docs/README.dev.md](docs/README.dev.md).

### 🎮 Quick Start

1. Start the server and join as a player
2. Run `/cp items` to receive the Nether Star, Slime Ball, Feather, and Heart of the Sea
3. Right-click the Slime Ball to save a quick checkpoint
4. Right-click the Nether Star to teleport instantly to the last CP
5. Left-click the Nether Star or right-click the Heart of the Sea to open the list GUI
6. Run `/cp language ja` to switch to Japanese
