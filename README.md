# Medicine Case プロジェクト

スマート薬ケース - XIAO BLE Senseを使った服薬リマインダーシステム

## プロジェクト構成

```
medicine_case/
├── README.md                   # プロジェクト概要（本ファイル）
├── medicine_case/              # マイコン用ファームウェア (Arduino/PlatformIO)
│   ├── README.md               # ファームウェア概要
│   ├── PROTOCOL_SPEC.md         # BLE通信プロトコル仕様 ★ NEW
│   ├── DATA_FLOW_SPEC.md        # データフロー仕様 ★ NEW
│   └── INTAKE_DETECTION_SPEC.md # 服薬検出仕様 ★ NEW
│
└── MedicineCaseMob/            # スマートフォンアプリ (Android)
    └── INTAKE_NOTIFICATION_SPEC.md # 通知・履歴管理仕様
```

## システム概要

このプロジェクトは、加速度センサーを搭載した薬ケースとスマートフォンアプリが連携し、日々の服薬をサポートするシステムです。

### 1. マイコン側 (Firmware)
- **服薬検出**: 薬ケースの傾きを検知し、自動的に服用時刻を記録
- **データ保持**: スマホ未接続時でも最後の服用時刻を保持し、再接続時に自動同期
- **BLE通信**: 角度やクールダウン設定をスマホから変更可能

### 2. スマートフォンアプリ側 (Android)
- **スマート通知**: スケジュール（朝・昼・夜）に合わせて、飲み忘れをリマインド
- **3状態履歴管理**: 「服用済」「未服用」に加え、設定オフの枠を「対象外」として管理
- **一括管理**: メイン画面で当日の予定と履歴をひと目で確認

## 開発環境

- **Firmware**: PlatformIO / C++ (Arduino)
- **App**: Android Studio / Kotlin (Jetpack Compose)
- **Persistence**: Room DB / Jetpack DataStore

## ドキュメント一覧

### 通信・プロトコル
- **[BLE通信プロトコル仕様](medicine_case/PROTOCOL_SPEC.md)** ★ NEW
  - Service UUID, Characteristic UUID
  - コマンドフォーマット（SET:time, GET:intakeなど）
  - 応答フォーマット

- **[データフロー仕様](medicine_case/DATA_FLOW_SPEC.md)** ★ NEW
  - システムアーキテクチャ
  - 通信シーケンス
  - タイミング図
  - データ管理

### 機能仕様
- **[服薬検出仕様](medicine_case/INTAKE_DETECTION_SPEC.md)** ★ NEW
  - 検出原理（6軸センサー）
  - 判定基準（角度閾値、クールダウン）
  - 状態遷移（IDLE → MOVING → CONFIRMED）
  - LEDフィードバック

- **[アプリ通知・履歴仕様](MedicineCaseMob/INTAKE_NOTIFICATION_SPEC.md)**
  - スケジュール管理
  - 通知ロジック
  - 履歴管理

### 概要
- **[ファームウェア概要](medicine_case/README.md)**
  - ハードウェア構成
  - ソフトウェア構成
  - セットアップ手順

## クイックスタート

1. **ファームウェア**: `medicine_case/README.md` 参照
2. **アプリ**: `MedicineCaseMob/README.md` 参照
3. **通信**: `medicine_case/PROTOCOL_SPEC.md` 参照

## ライセンス
MIT License

## 作者
pirorin215
