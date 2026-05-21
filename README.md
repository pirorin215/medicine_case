# Medicine Case プロジェクト

スマート薬ケース - XIAO BLE Senseを使った服薬リマインダーシステム

## プロジェクト構成

```
medicine_case/
├── medicine_case/              # マイコン用ファームウェア (Arduino/PlatformIO)
│   ├── medicine_case.ino       # メインファイル
│   ├── medicine_case.h         # ヘッダーファイル
│   ├── medicine_case_ble.ino   # BLE通信処理
│   ├── medicine_case_sensor.ino # 6軸センサー処理
│   ├── medicine_case_utils.ino # ユーティリティ
│   ├── platformio.ini          # PlatformIO設定
│   ├── README.md               # ファームウェア説明
│   └── CLAUDE.md               # 開発ドキュメント
│
└── MedicineCaseMob/            # スマートフォンアプリ (Android)
    ├── app/                    # アプリモジュール
    │   └── src/main/
    │       ├── java/.../medicinecasemob/
    │       │   ├── MainActivity.kt
    │       │   ├── MainApplication.kt
    │       │   ├── ui/
    │       │   │   ├── screen/     # 画面
    │       │   │   ├── viewModel/  # ViewModel
    │       │   │   ├── data/       # データモデル・DB
    │       │   │   └── theme/      # テーマ
    │       │   └── di/             # Dependency Injection
    │       └── res/                # リソース
    ├── build.gradle.kts          # プロジェクト設定
    ├── settings.gradle.kts       # プロジェクト設定
    └── gradle/                   # Gradle設定
```

## 機能

### マイコン側（medicine_case/）

1. **服薬検出**
   - 6軸センサー（LSM6DS3TRC）で薬ケースの傾きを検知
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
   - BLE通信できない時は通知しないオプション
   - 1日最大24回の通知制限

### スマートフォンアプリ側（MedicineCaseMob/）

1. **BLE接続・時刻同期**
2. **服薬時刻設定（朝・昼・夜）**
   - 各時間帯の開始時刻と終了時刻を設定可能
   - デフォルト: 朝(8:00-11:00)、昼(12:00-17:00)、夜(19:00-22:00)
3. **服薬スケジュールの有効/無効設定**
4. **通知設定**
   - BLE接続時のみ通知するオプション
   - 15分ごとの定期チェック（WorkManager使用）
   - 1日最大24回の通知制限
   - 60分間隔の通知（最終通知から60分経過しないと通知しない）
   - スケジュールの終了時刻を過ぎても未服薬の場合に通知
5. **服薬履歴表示・管理**
   - 1日1行で表示
   - 朝・昼・夜を1行で一括表示
   - 服薬状況（済/未済）を視認
   - INTAKEイベントの自動記録（30分以内の連続服薬を防止）
   - 選択モードによる履歴の一括削除
   - 全履歴の消去機能

## 開発環境

### マイコン側

- **PlatformIO**: 推奨
- **Arduino IDE**: 対応
- **ボード**: Seeed Studio XIAO BLE Sense

### スマートフォンアプリ側

- **Android Studio**: 推奨
- **Kotlin**: メイン言語
- **Jetpack Compose**: UIフレームワーク
- **Hilt**: DIフレームワーク
- **Room**: ローカルDB

## セットアップ

### マイコン側のセットアップ

1. XIAO BLE SenseをPCに接続
2. PlatformIOでビルド:
   ```bash
   cd medicine_case
   pio run
   ```
3. 書き込み:
   ```bash
   pio run --target upload
   ```
4. シリアルモニターで確認:
   ```bash
   pio device monitor -b 115200
   ```

### スマートフォンアプリのセットアップ

1. Android Studioでプロジェクトを開く:
   ```bash
   open MedicineCaseMob
   ```
2. Gradle同期
3. 実行:

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

#### ステータス取得
```
GET:status
GET:version
```

## 参考プロジェクト

[BikeClock](https://github.com/pirorin215/btclock) - BLE通信、設定管理の構造を参考にしています。

## ライセンス

MIT License

## 作者

pirorin215

## バージョン

1.0.0 - 初版リリース
