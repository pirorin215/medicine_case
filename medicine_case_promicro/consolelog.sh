#!/opt/homebrew/bin/bash

# Medicine Case Pro Micro シリアルモニタ

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/common.sh" ]; then
    source "$SCRIPT_DIR/common.sh"
fi
if [ -f "$SCRIPT_DIR/setting.sh" ]; then
    source "$SCRIPT_DIR/setting.sh"
fi

check_medicine_case_promicro_port

echo "シリアルモニタを開きます: $MEDICINE_CASE_PROMICRO_PORT (115200 baud)"
echo "終了: Ctrl-C"
echo "========================================"

arduino-cli monitor -p "$MEDICINE_CASE_PROMICRO_PORT" --config 115200
