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
# スクリプト環境初期化
#=============================================================================
# 各スクリプトから呼び出して、SCRIPT_DIRを設定し必要なファイルを読み込みます
#
# 使用方法:
#   source "common.sh"
#   init_script_env
#=============================================================================
init_script_env() {
    # 呼び出し元スクリプトのディレクトリを取得
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[1]}")" && pwd)"

    # 共通関数の読み込み（既に読み込まれている場合はスキップ）
    if [ -z "$COMMON_SH_LOADED" ]; then
        COMMON_SH_LOADED=true
    fi

    # 設定ファイルの読み込み
    if [ -f "$SCRIPT_DIR/setting.sh" ]; then
        source "$SCRIPT_DIR/setting.sh"
    fi
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

#=============================================================================
# コマンド存在チェック
#=============================================================================
# 指定されたコマンドが存在するかチェックし、存在しない場合はエラー終了します
#
# 使用方法:
#   check_command <command_name> <install_hint>
#
# 例:
#   check_command "arduino-cli" "'brew install arduino-cli'でインストールしてください"
#=============================================================================
check_command() {
    local cmd="$1"
    local install_hint="$2"

    if ! command -v "$cmd" &> /dev/null; then
        log_error "${cmd}がインストールされていません。${install_hint}"
        exit 1
    fi
}

#=============================================================================
# リトライ処理
#=============================================================================
# コマンドを指定回数リトライします
#
# 使用方法:
#   retry_command <max_retries> <retry_delay> <command...>
#
# 例:
#   retry_command 3 5 arduino-cli upload --port "$PORT" medicine_case.ino
#=============================================================================
retry_command() {
    local max_retries="$1"
    local retry_delay="$2"
    shift 2

    local attempt=1
    while [ $attempt -le $max_retries ]; do
        if "$@"; then
            return 0
        fi

        if [ $attempt -lt $max_retries ]; then
            echo "コマンド失敗 ($attempt/$max_retries)、${retry_delay}秒待機してリトライします..." >&2
            sleep $retry_delay
        fi

        attempt=$((attempt + 1))
    done

    return 1
}
