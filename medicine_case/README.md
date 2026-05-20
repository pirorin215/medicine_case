# Medicine Case

スマート薬ケース - XIAO BLE Senseを使った服薬リマインダーデバイス

## 概要

Medicine Caseは、6軸センサーを搭載したXIAO BLE Senseマイコンを使用したスマート薬ケースです。薬ケースの傾きを検知して服薬を記録し、スマートフォンアプリとBLEで連携します。

### アーキテクチャ

ファームウェアは「服薬検出」のみに専念し、スケジュール管理・通知・履歴はすべてスマートフォンアプリ側で行います。

```
ファームウェア（シンプル）         スマートフォンアプリ（管理担当）
─────────────────────         ─────────────────────
服薬を検出                      スケジュール管理
  ↓                              ↓
timestampを1つだけ保持           服薬記録をDBに保存
  ↓                              ↓
INTAKE:timestamp をBLE通知       マイコンのtimestampをクリア
                                  ↓
                                通知判定・履歴表示
```

## ハードウェア

- **マイコン**: Seeed Studio XIAO BLE Sense
- **センサー**: LSM6DS3TRC (6軸IMU: 3軸加速度計 + 3軸ジャイロスコープ)
- **電源**: USB給電
- **通信**: Bluetooth Low Energy (BLE)

## 機能要件

### マイコン側（medicine_case/）

1. **服薬検出**
   - 6軸センサーで薬ケースの傾きを検知
   - 設定した角度以上の変化を「服薬動作」として判定
   - クールダウン期間による連続検出防止

2. **服薬記録**
   - 服薬検出時にtimestampを1つだけ保持
   - スマホアプリから取得可能（`GET:intake`）
   - 取得後、スマホからクリア（`CLR:intake`）

3. **BLE通信**
   - スマートフォンアプリとのBLE接続
   - 時刻同期
   - 検出設定（角度・クールダウン）の変更
   - 服薬timestampの送受信

### スマートフォンアプリ側（MedicineCaseMob/）

1. **BLE接続・時刻同期**
2. **服薬時刻設定（朝・昼・夜）**
3. **服薬スケジュールの有効/無効設定**
4. **服薬記録管理**
   - マイコンからのINTAKE通知を受信
   - 服薬時刻から朝/昼/夜を自動判定
   - ローカルDBに記録
5. **服薬履歴表示**
   - 1日1行で表示
   - 朝・昼・夜を1行で一括表示
   - 服薬状況（済/未済）を視認
6. **服薬リマインド通知**（未実装）
   - スケジュール時刻を過ぎても服薬されていない場合に通知

## BLEプロトコル

### Service UUID

```
4fafc201-1fb5-459e-8fcc-c5c9c331914d
```

### Characteristic UUIDs

- **Command**: `beb5483e-36e1-4688-b7f5-ea07361b26a0` (Read / Write / Notify)
- **Response**: `beb5483e-36e1-4688-b7f5-ea07361b26a2` (Notify)
- **Sensor**: `beb5483e-36e1-4688-b7f5-ea07361b26a3` (Notify)

### コマンドフォーマット

#### 時刻同期
```
SET:time:<unix_timestamp>
→ OK: Time synced
```

#### 検出設定
```
SET:detection:angle:<degrees>
→ OK: Detection angle updated

SET:detection:cooldown:<milliseconds>
→ OK: Detection cooldown updated
```

#### 服薬記録取得
```
GET:intake
→ INTAKE:<timestamp>   （未取得の服薬がある場合）
→ NONE                  （未取得の服薬がない場合）
```

#### 服薬記録クリア
```
CLR:intake
→ OK: Intake cleared
```

#### ステータス取得
```
GET:status
→ OK:status:connected=<0|1>,synced=<0|1>,pending_intake=<timestamp>
```

#### バージョン取得
```
GET:version
→ OK:version:<major>.<minor>.<patch>
```

### 通知フォーマット

#### 服薬検知通知（Sensor characteristic）
```
INTAKE:<timestamp>
```

## ソフトウェアアーキテクチャ

### ファイル構成

```
medicine_case/
├── medicine_case.ino          # メインファイル（ループ・設定・コマンドハンドラ）
├── medicine_case.h            # ヘッダーファイル
├── medicine_case_ble.ino      # BLE通信処理
├── medicine_case_sensor.ino   # 6軸センサー処理
├── medicine_case_utils.ino    # ユーティリティ（LED、ログ）
└── compile.sh                 # ビルドスクリプト
```

### 主要コンポーネント

1. **medicine_case.ino** — メインループ、時刻管理、設定の保存/読み込み、BLEコマンドハンドラ
2. **medicine_case_ble.ino** — BLEサーバー、コマンド処理、応答通知
3. **medicine_case_sensor.ino** — 6軸センサー制御、傾き角度計算、服薬検出アルゴリズム
4. **medicine_case_utils.ino** — LED状態表示、ログ出力

## 設定

### 検出設定

- **検出角度**: 10〜180度（デフォルト: 70度）
- **クールダウン**: 1〜300秒（デフォルト: 30秒）

### 設定保存

設定は内部フラッシュ（InternalFS）に保存されます。

- ファイル: `/detection.dat`
- 形式: バイナリ（float threshold + unsigned long cooldown）

## LED状態

- **赤点灯**: 未接続 + 未同期
- **緑点灯**: 未接続 + 同期済み / 接続 + 同期済み
- **黄点灯**: 接続 + 未同期
- **赤高速点滅**: エラー

## ビルドと書き込み

### Arduino CLIを使用する場合

```bash
cd medicine_case && sh compile.sh
```

または

```bash
arduino-cli compile --fqbn Seeeduino:nrf52:xiaonRF52840Sense medicine_case.ino
```

### Arduino IDEを使用する場合

1. Seeed XIAO BLE Senseボードをインストール
2. 必要なライブラリをインストール：
   - Adafruit Bluefruit nRF52
   - Adafruit LittleFS
   - LSM6DS3 (Seeed)
3. `medicine_case.ino`を開いて書き込み

## 開発環境

- **Arduino CLI**: 推奨
- **Arduino IDE**: 対応

### 必要なライブラリ

- Adafruit Bluefruit nRF52 (Seeeduino v1.1.12)
- Adafruit LittleFS
- LSM6DS3 (Seeed)

## 実装状況

### マイコン側

| 機能 | 状態 | 説明 |
|------|------|------|
| 6軸センサー服薬検出 | ✅ 完了 | 角度変化による検出、クールダウン、LEDフィードバック |
| BLE通信 | ✅ 完了 | サービス3characteristics全て検出、書き込みキュー実装 |
| 時刻同期 | ✅ 完了 | `SET:time:` コマンド |
| 検出設定 | ✅ 完了 | `SET:detection:angle/cooldown` コマンド |
| 服薬timestamp保持 | ✅ 完了 | `GET:intake` / `CLR:intake` コマンド |
| 設定永続化 | ✅ 完了 | InternalFSに検出設定を保存 |
| ステータス/バージョン | ✅ 完了 | `GET:status` / `GET:version` |
| 服薬検出通知 | ✅ 完了 | `INTAKE:<timestamp>` BLE通知 |

### スマートフォンアプリ側

| 機能 | 状態 | 説明 |
|------|------|------|
| BLE接続・自動接続 | ✅ 完了 | バックグラウンドスキャンサービス |
| 時刻同期 | ✅ 完了 | 接続時の手動同期 |
| 検出設定画面 | ✅ 完了 | 角度・クールダウンスライダー、BLE送信 |
| BLE接続状態表示 | ✅ 完了 | メイン画面のアイコン |
| 服薬INTAKE受信 | ✅ 完了 | BLE通知受信→ローカルDB記録→マイコンクリア |
| 再接続時のIntake取得 | ✅ 完了 | 接続時に`GET:intake`でポーリング |
| 服薬スケジュール設定UI | ⬜ 未実装 | 朝/昼/夜の時刻設定UI |
| 本日の服用状況表示 | ⬜ 未実装 | スケジュール連動した服薬済/未済表示 |
| 服薬履歴表示 | ⬜ 未実装 | 1日1行、朝/昼/夜一括表示 |
| 服薬リマインド通知 | ⬜ 未実装 | スケジュール時刻経過後のプッシュ通知 |

## 参考プロジェクト

このプロジェクトは[BikeClock](https://github.com/pirorin215/btclock)プロジェクトを参考にしています。

## ライセンス

MIT License

## 作者

pirorin215
