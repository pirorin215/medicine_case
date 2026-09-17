#!/bin/bash
# Medicine Case 共通関数ライブラリ
# 各スクリプトから source コマンドで読み込まれます

#=============================================================================
# シリアルポートデバイスパターン（共通定義）
#=============================================================================
SERIAL_PORT_PATTERNS="/dev/cu.usbmodem* /dev/cu.usbserial* /dev/cu.wchusbserial* /dev/ttyUSB* /dev/ttyACM*"

#=============================================================================
# ログ関数
#=============================================================================
# 標準化されたエラー/警告/情報メッセージ出力

log_error() {
    printf "\033[31mエラー: %s\033[0m\n" "$1" >&2
}

log_warning() {
    printf "\033[33m警告: %s\033[0m\n" "$1" >&2
}

log_info() {
    printf "情報: %s\n" "$1"
}

#=============================================================================
# Medicine Caseポート設定チェック
#=============================================================================
# MEDICINE_CASE_PORT が設定されていることをチェックし、未設定の場合は
# 利用可能なシリアルポートの候補を表示してエラー終了します
#
# 使用方法:
#   source "common.sh"
#   check_medicine_case_port
#=============================================================================
check_medicine_case_port() {
    if [ -z "$MEDICINE_CASE_PORT" ]; then
        log_error "MEDICINE_CASE_PORT が設定されていません。"
        echo "" >&2

        # 利用可能なシリアルポートの候補を検索
        echo "利用可能なシリアルポートの候補：" >&2
        FOUND=false
        for pattern in $SERIAL_PORT_PATTERNS; do
            if [ -e "$pattern" ]; then
                # 緑色で強調表示
                printf "  \033[32m%s\033[0m\n" "$pattern" >&2
                FOUND=true
            fi
        done

        if [ "$FOUND" = false ]; then
            echo "  （見つかりませんでした）" >&2
        fi

        echo "" >&2
        echo "次の手順で設定してください：" >&2
        echo "1. cp medicine_case/setting.sh.example medicine_case/setting.sh" >&2
        echo "2. medicine_case/setting.sh を編集して MEDICINE_CASE_PORT を設定" >&2
        echo "" >&2
        echo "設定例：" >&2
        echo "  MEDICINE_CASE_PORT=\"/dev/cu.usbmodem2101\"" >&2
        exit 1
    fi
}

