/**
 * Medicine Case Pro Micro（スイッチ版）
 *
 * Pro Micro (ATmega32U4) + マイクロスイッチによる USB直結の服薬記録デバイス。
 * ボタンが押されたら「服薬した」として INTAKE を USB シリアルで送り、
 * オンボードLEDが点滅する。検知はこれ以上ないほど単純で、確実。
 *
 * 経緯: v2.0 は GY-BMI160 による傾き検知だったが、半年の運用で
 * 「反応するように傾けている」状態になり、稀な不検知もあったため
 * 2026-10-07 にマイクロスイッチ押下へ転換した（v2.1）。
 *
 * シリアル契約:
 *   MCU -> host:
 *     HELLO medcase <name> v<x.y.z>    接続確立時（再接続時も再送）
 *     CONFIG name=<name> v=<x.y.z>     HELLO 直後
 *     HB <uptime_s>                    60秒ごとの心拍
 *     INTAKE <age_ms>                  押下時（age_ms>0 は daemon 停止中押下の再送）
 *   host -> MCU:
 *     PING                             -> PONG
 *     GET:config                       -> CONFIG ...
 *     SET:name:<name>                  -> OK:name:<name>               (1-15文字)
 *     不明コマンド                      -> ERR:unknown
 *
 * 待機中は送信しない（v2.2）。TX LED はコアの CDC が Serial.write 毎に
 * パルスするため、1秒テレメトリがあると待機中も1Hzで点滅してウザい。
 * liveness は 60秒HB で足りる（daemon の stale 判定は 90秒）。
 */

#include "medicine_case_promicro.h"

// --- グローバル変数 ---
DeviceConfig g_cfg;
unsigned long g_currentMillis = 0;

bool g_intakePending = false;
unsigned long g_intakePendingAt = 0;

//=============================================================================
// 設定（EEPROM）
//=============================================================================
void configLoad() {
    EEPROM.get(0, g_cfg);
    if (g_cfg.magic != CONFIG_MAGIC || g_cfg.version != CONFIG_VERSION) {
        g_cfg.magic = CONFIG_MAGIC;
        g_cfg.version = CONFIG_VERSION;
        strncpy(g_cfg.name, "medcase1", sizeof(g_cfg.name) - 1);
        g_cfg.name[sizeof(g_cfg.name) - 1] = '\0';
        configSave();
        logInfo("CFG", F("EEPROM invalid, defaults written"));
    }
}

void configSave() {
    g_cfg.magic = CONFIG_MAGIC;
    g_cfg.version = CONFIG_VERSION;
    EEPROM.put(0, g_cfg);
}

const char* firmwareVersion() {
    static char buf[12];
    snprintf(buf, sizeof(buf), "%d.%d.%d",
             FIRMWARE_VERSION_MAJOR, FIRMWARE_VERSION_MINOR, FIRMWARE_VERSION_PATCH);
    return buf;
}

void logInfo(const char* tag, const String& msg) {
    Serial.print('[');
    Serial.print(tag);
    Serial.print(F("] "));
    Serial.println(msg);
}

//=============================================================================
// setup / loop
//=============================================================================
void setup() {
    Serial.begin(115200);
    // while(!Serial) は使わない（daemon 非接続でも動作必須）
    configLoad();
    setupSwitch();
    logInfo("BOOT", String(F("medicine_case_promicro v")) + firmwareVersion() +
            F(" name=") + g_cfg.name);
}

void loop() {
    g_currentMillis = millis();
    serialPoll();
    pollSwitch();
    updateBlink();
}
