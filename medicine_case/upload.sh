#!/bin/bash

# Medicine Case Upload Script for XIAO BLE Sense (nRF52840)

# 共通関数と設定ファイルを読み込み
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/common.sh" ]; then
    source "$SCRIPT_DIR/common.sh"
fi
if [ -f "$SCRIPT_DIR/setting.sh" ]; then
    source "$SCRIPT_DIR/setting.sh"
fi

# Medicine Caseポートチェック
check_medicine_case_port

# Run arduino-cli upload
echo "Medicine Caseファームウェアを $MEDICINE_CASE_PORT にアップロード..."
echo "========================================"

UPLOAD_COMMAND="arduino-cli upload -p $MEDICINE_CASE_PORT --fqbn Seeeduino:nrf52:xiaonRF52840Sense medicine_case.ino"

$UPLOAD_COMMAND
UPLOAD_EXIT_CODE=$?

echo "========================================"
if [ $UPLOAD_EXIT_CODE -ne 0 ]; then
    echo "アップロード失敗"
    exit $UPLOAD_EXIT_CODE
fi

echo ""
echo "--- アップロード成功 ---"
echo "次: './consolelog.sh' を実行してシリアル出力を監視"
