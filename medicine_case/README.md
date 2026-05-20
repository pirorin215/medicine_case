# Medicine Case

スマート薬ケース - XIAO BLE Senseを使った服薬リマインダーデバイス

## 概要

Medicine Caseは、6軸センサーを搭載したXIAO BLE Senseマイコンを使用したスマート薬ケースです。薬ケースのフタを開けた動作（90度以上の傾き）を検知して服薬を記録し、スマートフォンアプリと連携して服薬リマインダー通知を送信します。

## ハードウェア

- **マイコン**: Seeed Studio XIAO BLE Sense
- **センサー**: LSM6DS3TRC (6軸IMU: 3軸加速度計 + 3軸ジャイロスコープ)
- **電源**: USB給電
- **通信**: Bluetooth Low Energy (BLE)

## 機能

### マイコン側機能

1. **服薬検出**
   - 6軸センサーで薬ケースの傾きを検知
   - 90度以上の変化を「服薬動作」として判定
   - 振動や単なる移動による誤検出を防止

2. **服薬スケジュール管理**
   - 朝・昼・夜の3つの時間帯を設定可能
   - 各スケジュールの有効/無効を個別に設定
   - 服薬記録（時刻保存）

3. **BLE通信**
   - スマートフォンアプリとのBLE接続
   - 時刻同期
   - スケジュール設定
   - 服薬データ送信

4. **通知機能**
   - 服薬時刻を過ぎても服薬されていない場合、1時間ごとに通知
   - 通知オプション：BLE通信できない時は通知しない
   - 1日最大24回の通知制限

### スマートフォンアプリ機能

1. **BLE接続・時刻同期**
2. **服薬時刻設定（朝・昼・夜）**
3. **服薬スケジュールの有効/無効設定**
4. **通知設定（BLE通信時のみ通知するオプション）**
5. **服薬履歴表示**
   - 1日1行で表示
   - 朝・昼・夜を1行で一括表示
   - 服薬状況（済/未済）を視認

## ソフトウェアアーキテクチャ

### ファイル構成

```
medicine_case/
├── medicine_case.ino          # メインファイル
├── medicine_case.h            # ヘッダーファイル
├── medicine_case_ble.ino      # BLE通信処理
├── medicine_case_sensor.ino   # 6軸センサー処理
├── medicine_case_utils.ino    # ユーティリティ（LED、通知、ログ）
└── platformio.ini             # PlatformIO設定
```

### 主要コンポーネント

1. **medicine_case.ino**
   - メインループ
   - 時刻管理
   - 設定の保存/読み込み

2. **medicine_case_ble.ino**
   - BLEサーバー
   - コマンド処理（時刻同期、スケジュール設定）
   - 応答通知

3. **medicine_case_sensor.ino**
   - 6軸センサー制御
   - 傾き角度計算
   - 服薬検出アルゴリズム

4. **medicine_case_utils.ino**
   - LED状態表示
   - 通知処理
   - ログ出力

## BLEプロトコル

### Service UUID

```
4fafc201-1fb5-459e-8fcc-c5c9c331914d
```

### Characteristic UUIDs

- **Command**: `beb5483e-36e1-4688-b7f5-ea07361b26a0` (Write)
- **Response**: `beb5483e-36e1-4688-b7f5-ea07361b26a2` (Notify)
- **Sensor**: `beb5483e-36e1-4688-b7f5-ea07361b26a3` (Notify)

### コマンドフォーマット

#### 時刻同期
```
SET:time:<unix_timestamp>
```

#### スケジュール設定
```
SET:schedule:<index>:<enabled>:<hour>:<minute>
```
- index: 0=朝, 1=昼, 2=夜
- enabled: 0=無効, 1=有効
- hour: 0-23
- minute: 0-59

#### 通知設定
```
SET:notification:<enabled>
```
- enabled: 0=無効, 1=有効

#### ステータス取得
```
GET:status
```

#### バージョン取得
```
GET:version
```

### 通知フォーマット

#### 服薬検知通知
```
INTAKE:<pitch>,<roll>,<timestamp>
TAKEN:<schedule_index>:<name>:<hour>:<minute>:<timestamp>
```

## 設定

### デフォルトスケジュール

- **朝**: 7:00
- **昼**: 12:00
- **夜**: 19:00

### 設定保存

設定は内部フラッシュ（InternalFS）に保存されます。

- ファイル: `/settings.dat`
- 形式: バイナリ（MedicineSchedule構造体）

## LED状態

- **赤点灯**: 起動中
- **赤点滅**: 未接続 + 未同期
- **緑点灯**: 未接続 + 同期済み
- **青点滅**: 接続中 + 未同期
- **青点灯**: 接続中 + 同期済み
- **赤高速点滅**: エラー

## ビルドと書き込み

### PlatformIOを使用する場合

```bash
# ビルド
pio run

# 書き込み
pio run --target upload

# シリアルモニター
pio device monitor
```

### Arduino IDEを使用する場合

1. Seeed XIAO BLE Senseボードをインストール
2. 必要なライブラリをインストール：
   - Adafruit LSM6DS3TRC
   - Adafruit Bluefruit nRF52
3. `medicine_case.ino`を開いて書き込み

## 開発環境

- **PlatformIO**: 推奨
- **Arduino IDE**: 対応

### 必要なライブラリ

- Adafruit LSM6DS3TRC
- Adafruit Bluefruit nRF52
- Adafruit LittleFS

## 参考プロジェクト

このプロジェクトは[BikeClock](https://github.com/pirorin215/btclock)プロジェクトを参考にしています。

## ライセンス

MIT License

## 作者

pirorin215

## バージョン

1.0.0 - 初版リリース
