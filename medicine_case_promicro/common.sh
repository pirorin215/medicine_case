#!/opt/homebrew/bin/bash
# Medicine Case Pro Micro 共通関数ライブラリ
# 各スクリプトから source コマンドで読み込まれます

#=============================================================================
# シリアルポートデバイスパターン（共通定義）
#=============================================================================
SERIAL_PORT_PATTERNS="/dev/cu.usbmodem* /dev/cu.usbserial* /dev/cu.wchusbserial* /dev/ttyUSB* /dev/ttyACM*"

#=============================================================================
# ログ関数
#=============================================================================
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
# コマンド存在チェック
#=============================================================================
check_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        log_error "'$1' が見つかりません。インストールしてください。"
        exit 1
    fi
}

#=============================================================================
# Medicine Case Pro Micro ポート設定チェック
#=============================================================================
# MEDICINE_CASE_PROMICRO_PORT が設定されていることをチェックし、未設定の場合は
# 利用可能なシリアルポートの候補を表示してエラー終了します
#
# 使用方法:
#   source "common.sh"
#   check_medicine_case_promicro_port
#=============================================================================
check_medicine_case_promicro_port() {
    if [ -z "$MEDICINE_CASE_PROMICRO_PORT" ]; then
        log_error "MEDICINE_CASE_PROMICRO_PORT が設定されていません。"
        echo "" >&2

        echo "利用可能なシリアルポートの候補：" >&2
        FOUND=false
        for pattern in $SERIAL_PORT_PATTERNS; do
            if compgen -G "$pattern" >/dev/null; then
                for port in $pattern; do
                    printf "  \033[32m%s\033[0m\n" "$port" >&2
                    FOUND=true
                done
            fi
        done

        if [ "$FOUND" = false ]; then
            echo "  (シリアルポートが見つかりません)" >&2
        fi

        echo "" >&2
        echo "setting.sh を作成し、MEDICINE_CASE_PROMICRO_PORT を設定してください:" >&2
        echo "  cp setting.sh.example setting.sh" >&2
        exit 1
    fi

    # 固定ポートの保護（~/dev/Arduino/AGENTS.md §8）
    # 開発共通ポート 212101 以外の usbmodem への書込は意図確認が必要
    case "$MEDICINE_CASE_PROMICRO_PORT" in
        /dev/cu.usbmodemHIDEF1|/dev/cu.usbmodemHIDPC1|/dev/cu.usbmodem212301)
            log_error "$MEDICINE_CASE_PROMICRO_PORT は固定ポート（§8 保護対象）です。書き込めません。"
            exit 1
            ;;
    esac
}

#=============================================================================
# リトライ付きコマンド実行
#=============================================================================
retry_command() {
    local retries=$1
    local delay=$2
    shift 2
    local n=0
    until "$@"; do
        n=$((n + 1))
        if [ $n -ge $retries ]; then
            log_error "コマンドが ${retries} 回失敗しました: $*"
            return 1
        fi
        log_warning "リトライ ${n}/${retries} (${delay}秒後)..."
        sleep $delay
    done
}
