/**
 * Medicine Case Pro Micro
 *
 * Pro Micro (ATmega32U4) + GY-BMI160 の USB直結版 Medicine Case。
 * 検知ロジックは BLE版(medicine_case/) から移植し、BLE の代わりに
 * USB シリアルで Mac daemon (meds-daemon) と通信する。
 * 電源は Mac の USB ポートから直接供給されるため、バッテリー管理は不要。
 *
 * シリアル契約:
 *   MCU -> host:
 *     HELLO medcase <name> v<x.y.z>    接続確立時（再接続時も再送）
 *     CONFIG angle=<deg> cooldown=<ms> name=<name> v=<x.y.z>   HELLO 直後
 *     HB <uptime_s>                    5秒ごとの心拍
 *     T <pitch> <roll> <state>         1秒ごとのテレメトリ
 *     INTAKE <maxChange> <age_ms>      検知時（age_ms>0 は daemon 停止中検知の再送）
 *   host -> MCU:
 *     PING                             -> PONG
 *     GET:config                       -> CONFIG ...
 *     SET:angle:<deg>                  -> OK:angle:<deg> / ERR:angle   (10-180)
 *     SET:cooldown:<ms>                -> OK:cooldown:<ms> / ERR:cooldown (1000-300000)
 *     SET:name:<name>                  -> OK:name:<name>               (1-15文字)
 *     不明コマンド                      -> ERR:unknown
 */

#include "medicine_case_promicro.h"

// --- グローバル変数 ---
DeviceConfig g_cfg;
unsigned long g_currentMillis = 0;
uint8_t g_detectionState = DETECTION_STATE_IDLE;
float g_currentPitch = 0.0f;
float g_currentRoll = 0.0f;

bool g_intakePending = false;
float g_intakePendingMax = 0.0f;
unsigned long g_intakePendingAt = 0;

//=============================================================================
// 設定（EEPROM）
//=============================================================================
void configLoad() {
    EEPROM.get(0, g_cfg);
    if (g_cfg.magic != CONFIG_MAGIC || g_cfg.version != CONFIG_VERSION ||
        g_cfg.angle < 10.0f || g_cfg.angle > 180.0f ||
        g_cfg.cooldownMs < 1000UL || g_cfg.cooldownMs > 300000UL) {
        g_cfg.magic = CONFIG_MAGIC;
        g_cfg.version = CONFIG_VERSION;
        g_cfg.angle = DEFAULT_MOVEMENT_THRESHOLD_DEG;
        g_cfg.cooldownMs = DEFAULT_COOLDOWN_TIME_MS;
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

const char* detectionStateName(uint8_t s) {
    switch (s) {
        case DETECTION_STATE_MOVING:    return "MOVING";
        case DETECTION_STATE_CONFIRMED: return "CONFIRMED";
        default:                        return "IDLE";
    }
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
    setupIMU();
    logInfo("BOOT", String(F("medicine_case_promicro v")) + firmwareVersion() +
            F(" name=") + g_cfg.name);
}

void loop() {
    g_currentMillis = millis();
    serialPoll();
    updateSensor();
    detectMedicineIntake();
}
