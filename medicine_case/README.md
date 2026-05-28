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
timestampを1つだけ保持           ①服薬データを取得
  ↓                              ↓
INTAKE:timestamp をBLE通知       ②時刻補完（timestamp=0なら受信時刻使用）
（マイコン側はデータを保持したまま）↓
                                ③重複チェック（既に処理済みなら無視）
                                  ↓
                                ④通知判定・履歴表示

※データ消失防止のため、スマホ側で重複排除を実装
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
- **時刻同期前でもINTAKEイベントを記録します**（timestamp=0として記録し、スマホ側で受信時刻を使用）

### 2. 服薬記録
- 服薬検出時にUnixタイムスタンプを1つだけ保持
- スマホアプリから取得可能（`GET:intake`）
- **データ消失防止**: マイコン側はデータを保持したまま、スマホ側で重複排除を実装

### 3. BLE通信
- スマートフォンアプリとのBLE接続・通信
- **詳細**: [BLE通信プロトコル仕様](PROTOCOL_SPEC.md)を参照

## 仕様ドキュメント

- **[システム仕様書](../docs/SYSTEM_SPEC.md)**: システム全体のアーキテクチャ、服薬検出仕様、通知・履歴管理仕様
- **[BLE通信プロトコル仕様](PROTOCOL_SPEC.md)**: Service UUID, Characteristic UUID, コマンドフォーマットの詳細

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
