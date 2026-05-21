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
timestampを1つだけ保持           服薬データを取得
  ↓                              ↓
INTAKE:timestamp をBLE通知       マイコンのtimestampをクリア
                                  ↓
                                通知判定・履歴表示
```

## ハードウェア

- **マイコン**: Seeed Studio XIAO BLE Sense
- **センサー**: LSM6DS3TRC (6軸IMU: 3軸加速度計 + 3軸ジャイロスコープ)
- **電源**: USB給電 / バッテリー（要ハードウェア対応）
- **通信**: Bluetooth Low Energy (BLE)

## 機能要件（ファームウェア）

### 1. 服薬検出
- 6軸センサーで薬ケースの傾きを検知
- 設定した角度以上の変化を「服薬動作」として判定
- クールダウン期間による連続検出防止

### 2. 服薬記録
- 服薬検出時にUnixタイムスタンプを1つだけ保持
- スマホアプリから取得可能（`GET:intake`）
- 取得後、スマホ側からの指示でクリア（`CLR:intake`）

### 3. BLE通信
- スマートフォンアプリとのBLE接続・通信
- 時刻同期
- 検出設定（角度・クールダウン）の保持
- 服薬イベントの通知

## BLEプロトコル

### Service UUID
`4fafc201-1fb5-459e-8fcc-c5c9c331914d`

### Characteristic UUIDs
- **Command**: `beb5483e-36e1-4688-b7f5-ea07361b26a0` (Write)
- **Response**: `beb5483e-36e1-4688-b7f5-ea07361b26a2` (Notify)
- **Sensor**: `beb5483e-36e1-4688-b7f5-ea07361b26a3` (Notify)

### コマンドフォーマット（抜粋）
- `SET:time:<unix_timestamp>`: 時刻同期
- `SET:detection:angle:<degrees>`: 検出角度設定
- `SET:detection:cooldown:<ms>`: クールダウン設定
- `GET:intake`: 服薬データ取得
- `CLR:intake`: 服薬データクリア

## ソフトウェア構成

```
medicine_case/
├── medicine_case.ino          # メインファイル（ループ・設定・コマンド）
├── medicine_case.h            # ヘッダーファイル
├── medicine_case_ble.ino      # BLE通信・プロトコル処理
├── medicine_case_sensor.ino   # 6軸センサー・判定処理
└── medicine_case_utils.ino    # LED・ログ・ユーティリティ
```

## セットアップ

### 開発環境
- **PlatformIO** (推奨)
- **Arduino IDE** (要ライブラリ: Adafruit Bluefruit nRF52, Adafruit LittleFS, LSM6DS3)

### ビルド・書き込み (PlatformIO)
```bash
pio run --target upload
```

## ライセンス
MIT License

## 作者
pirorin215
