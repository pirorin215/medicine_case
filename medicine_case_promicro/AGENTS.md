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

- v2.0 は GY-BMI160 傾き検知（bikeclock_esp32 のドライバ移植）だったが、
  2026-10-07 に v2.1 マイクロスイッチ押下へ転換（半年の運用で「反応するように
  傾けている」自覚・稀な不検知のため）。IMU には戻さないこと。
- 検知は `medicine_case_promicro_switch.ino` に集約（30ms debounce・2秒最小間隔・
  LED点滅フィードバック）。押下イベントの送信契約（INTAKE <age_ms>・pending再送）
  は `medicine_case_promicro_serial.ino`。
- LED は RXLED0/TXLED0 が点灯（active-low）。実機で逆に見えたら LED_ON/LED_OFF を入れ替える。
- BLE 層は存在しない。プロトコル仕様は README.md。
