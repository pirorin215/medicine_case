/**
 * USB シリアル通信
 *
 * 行ベースのテキストプロトコル。HELLO 直後に daemon が GET:config しなくても
 * CONFIG を送るので、daemon は接続するだけで名前とバージョンを把握できる。
 * daemon 未接続中の押下は g_intakePending に保持し、再接続時に1回だけ
 * age_ms（押下からの経過ミリ秒）付きで再送する。daemon は受信時刻から
 * age_ms を差し引いて元の時刻を復元する。
 */

#include "medicine_case_promicro.h"

static bool s_wasConnected = false;
static char s_lineBuf[64];
static uint8_t s_lineLen = 0;
static unsigned long s_lastHb = 0;
static unsigned long s_lastTelemetry = 0;

//=============================================================================
// 送信ヘルパ
//=============================================================================
static void sendConfigLine() {
    Serial.print(F("CONFIG name="));
    Serial.print(g_cfg.name);
    Serial.print(F(" v="));
    Serial.println(firmwareVersion());
}

static void sendHello() {
    Serial.print(F("HELLO medcase "));
    Serial.print(g_cfg.name);
    Serial.print(F(" v"));
    Serial.println(firmwareVersion());
    sendConfigLine();
}

static void sendTelemetry() {
    Serial.print(F("T "));
    Serial.println(mcuStateName(g_mcuState));
}

//=============================================================================
// INTAKE 送出（スイッチ押下時と再接続再送の両方から使う）
//=============================================================================
void serialSendIntake() {
    if (Serial) {
        Serial.print(F("INTAKE 0"));
        Serial.println();
        g_intakePending = false;
    } else {
        // daemon 未接続: 保持して再接続時に再送
        g_intakePending = true;
        g_intakePendingAt = g_currentMillis;
        logInfo("SER", F("intake kept as pending (daemon offline)"));
    }
}

//=============================================================================
// コマンド処理（host -> MCU）
//=============================================================================
static void handleLine(char* line) {
    if (strncmp(line, "PING", 4) == 0) {
        Serial.println(F("PONG"));
        return;
    }
    if (strncmp(line, "GET:config", 10) == 0) {
        sendConfigLine();
        return;
    }
    if (strncmp(line, "SET:name:", 9) == 0) {
        const char* n = line + 9;
        size_t len = strlen(n);
        if (len >= 1 && len <= sizeof(g_cfg.name) - 1) {
            memset(g_cfg.name, 0, sizeof(g_cfg.name));
            strncpy(g_cfg.name, n, sizeof(g_cfg.name) - 1);
            configSave();
            Serial.print(F("OK:name:"));
            Serial.println(g_cfg.name);
        } else {
            Serial.println(F("ERR:name"));
        }
        return;
    }
    Serial.println(F("ERR:unknown"));
}

//=============================================================================
// serialPoll — loop から毎回呼ぶ
//=============================================================================
void serialPoll() {
    bool connected = (bool)Serial;

    // 接続確立（初回・daemon 再起動・USB 再挿入のいずれも）
    if (connected && !s_wasConnected) {
        sendHello();
        if (g_intakePending) {
            unsigned long age = millis() - g_intakePendingAt;
            Serial.print(F("INTAKE "));
            Serial.println(age);
            g_intakePending = false;
            logInfo("SER", String(F("pending intake resent (age=")) + String(age) + F("ms)"));
        }
        s_lastHb = g_currentMillis;
        s_lastTelemetry = g_currentMillis;
    }
    s_wasConnected = connected;

    // 受信行の処理
    while (Serial.available() > 0) {
        char c = (char)Serial.read();
        if (c == '\n') {
            if (s_lineLen > 0) {
                s_lineBuf[s_lineLen] = '\0';
                handleLine(s_lineBuf);
            }
            s_lineLen = 0;
        } else if (c != '\r' && s_lineLen < sizeof(s_lineBuf) - 1) {
            s_lineBuf[s_lineLen++] = c;
        }
    }

    if (!connected) {
        return;
    }

    if (g_currentMillis - s_lastHb >= HEARTBEAT_INTERVAL_MS) {
        s_lastHb = g_currentMillis;
        Serial.print(F("HB "));
        Serial.println(g_currentMillis / 1000UL);
    }

    if (g_currentMillis - s_lastTelemetry >= TELEMETRY_INTERVAL_MS) {
        s_lastTelemetry = g_currentMillis;
        sendTelemetry();
    }
}
