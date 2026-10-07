#!/opt/homebrew/bin/bash

# Medicine Case Pro Micro Upload Script (ATmega32U4)

# 共通関数と設定ファイルを読み込み
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/common.sh" ]; then
    source "$SCRIPT_DIR/common.sh"
fi
if [ -f "$SCRIPT_DIR/setting.sh" ]; then
    source "$SCRIPT_DIR/setting.sh"
fi

check_command arduino-cli
check_medicine_case_promicro_port

echo "Medicine Case Pro Micro ファームウェアを $MEDICINE_CASE_PROMICRO_PORT にアップロード..."
echo "（Caterina ブートローダはリセット後8秒間のみ出現します。書き込めない場合は"
echo " リセットボタンを2回タップしてブートローダモードに入れてください）"
echo "========================================"

arduino-cli upload -p "$MEDICINE_CASE_PROMICRO_PORT" --fqbn arduino:avr:leonardo medicine_case_promicro.ino
UPLOAD_EXIT_CODE=$?

echo "========================================"
if [ $UPLOAD_EXIT_CODE -ne 0 ]; then
    echo "アップロード失敗"
    exit $UPLOAD_EXIT_CODE
fi

echo ""
echo "--- アップロード成功 ---"
echo "次: './consolelog.sh' でシリアル出力を監視、または daemon を起動"
