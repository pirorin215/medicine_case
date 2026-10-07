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

## プラットフォーム情報

- **ボード**: Pro Micro互換 (ATmega32U4・5V/16MHz)
- **FQBN**: `arduino:avr:leonardo`
- **公式ビルド方式**: `arduino-cli`（`compile.sh` / `upload.sh` 経由）
- **書き込み**: Caterina ブートローダはリセット後8秒間のみ出現（書込に失敗する場合はリセットボタン2回タップ）。書込可否の既定は `~/dev/Arduino/AGENTS.md` §4 に従う
