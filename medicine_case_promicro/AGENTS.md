# medicine_case_promicro - エージェントへの指示

## 自動ビルドルール（必須）

**重要:** Arduinoコード（`.ino`, `.cpp`, `.h`ファイル）を変更した場合、**必ず直後にビルドを実行すること。**

### 手順

1. コードを変更する
2. **`medicine_case_promicro.h` の `FIRMWARE_VERSION_PATCH` を1つ増やす**
3. **即座にビルドを実行**: `bash compile.sh`
4. ビルド結果をユーザーに報告する（成功・失敗問わず）

### ビルド結果の報告形式

**成功時:**
- ✅ ビルド成功
- Flash使用量 / RAM使用量を表示

**失敗時:**
- ❌ ビルド失敗
- エラーメッセージを表示
- 解決策を提示して修正

## 書き込みルール

- **開発中（ポートが `setting.sh` で開発共通ポート `/dev/cu.usbmodem212101` を指している場合）: エージェント書込可**（§8 の開発共通ポートはファーム入れ替え可のため）
- **運用配置後（専用固定ポートに接続された場合）: エージェントは書き込まない。** `sh upload.sh` をユーザーが実行する
- 固定ポート3つ（HIDEF1/HIDPC1/212301）は `common.sh` の `check_medicine_case_promicro_port` が機械的に拒否する
- 書き込み成功後は `./consolelog.sh` で `HELLO` / `HB` / `T` 行が出ることを確認する

## プラットフォーム情報

- **ボード**: Pro Micro互換 (ATmega32U4・5V/16MHz・Caterinaブートローダ)
- **FQBN**: `arduino:avr:leonardo`（Pro Micro 専用コア未導入の環境で Leonardo として扱う運用は promicro-presence と同じ）
- **公式ビルド方式**: `arduino-cli`（`compile.sh` / `upload.sh` 経由）
- **書き込みのクセ**: Caterina はリセット後8秒間のみブートローダが出る。連続書込失敗時はリセットボタン2回タップでブートローダ待機させる

## 移植元との対応

- 検知ロジック・定数: `../medicine_case/medicine_case_sensor.ino` + `medicine_case.h`（値は勝手に変えない）
- BMI160 ドライバ: `~/dev/Arduino/btclock/bikeclock_esp32/bikeclock_esp32_imu.ino`（CMD受理待ち・PMU完了待ちの罠と対策込み）
- BLE 層は削除し、`medicine_case_promicro_serial.ino` の行ベーステキストプロトコルに置換。プロトコル仕様は README.md
