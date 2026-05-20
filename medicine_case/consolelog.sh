#!/bin/bash

# Medicine Case シリアルコンソール監視スクリプト
# デバイスの接続/切断を自動検出し、シリアルログを表示します

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

CAT_ACTIVE=false
DEVICE_PATTERN="$MEDICINE_CASE_PORT"

echo "Medicine Case シリアルコンソール監視を開始します..."
echo "監視ポート: $MEDICINE_CASE_PORT"
echo "Ctrl+C で終了します"
echo ""

while true; do
  DEVICE_PRESENT=false
  for pattern in $DEVICE_PATTERN; do
    if ls $pattern 1> /dev/null 2>&1; then
      DEVICE_PRESENT=true
      break
    fi
  done

  UPLOAD_RUNNING=false
  if pgrep -f "arduino-cli upload" > /dev/null; then
    UPLOAD_RUNNING=true
  fi

  if $DEVICE_PRESENT && ! $UPLOAD_RUNNING; then
    # Should be active
    if ! $CAT_ACTIVE; then
      # デバイスを見つけて cat 開始
      DEVICE=$(ls $DEVICE_PATTERN 2>/dev/null | head -n 1)
      echo "--- Serial console started at $(date) ---"
      CAT_ACTIVE=true
    fi
    cat "$(ls $DEVICE_PATTERN 2>/dev/null | head -n 1)"
  else
    # Should be inactive
    if $CAT_ACTIVE; then
      echo ""
      echo "--- Serial console stopped at $(date) ---"
      CAT_ACTIVE=false
    fi
    sleep 0.1
  fi
done
