# medicine_case プロジェクト - エージェントへの指示

## 自動ビルドルール（必須）

**重要:** Arduinoコード（`.ino`, `.cpp`, `.h`ファイル）を変更した場合、**必ず直後にビルドを実行すること。** コードを変更しただけではマイコンに書き込まれない。

### 手順

1. コードを変更する
2. **`medicine_case.h` の `FIRMWARE_VERSION_PATCH` を1つ増やす**
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

### 書き込み

ビルドが成功したら、ユーザーが `sh upload.sh` でマイコンに書き込む。エージェントは勝手に書き込まない（デバイス接続状態に依存するため）。

## プラットフォーム情報

- **ボード**: Seeed XIAO BLE Sense (nRF52840)
- **FQBN**: `Seeeduino:nrf52:xiaonRF52840Sense`
- **公式ビルド方式**: `arduino-cli`（`compile.sh` 経由）
- **PlatformIO**: `platformio.ini` が併存するが、これは jlink デバッグ用途。通常のビルド・書き込みは `compile.sh` / `upload.sh` を使用すること。
