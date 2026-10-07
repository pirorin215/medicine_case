#ifndef MEDICINE_CASE_PROMICRO_H
#define MEDICINE_CASE_PROMICRO_H

#include <Arduino.h>
#include <EEPROM.h>

//=============================================================================
// ファームウェアバージョン（コード変更時は PATCH を bump すること・機構変更は MINOR）
//=============================================================================
#define FIRMWARE_VERSION_MAJOR 2
#define FIRMWARE_VERSION_MINOR 1
#define FIRMWARE_VERSION_PATCH 0

//=============================================================================
// マイクロスイッチ（服薬ボタン）
//=============================================================================
// COM -> GND / NO -> D4 の1極接続。INPUT_PULLUP で押すと LOW。
#define SWITCH_PIN 4
#define SWITCH_DEBOUNCE_MS       30    // チャタリング除去 [ms]
#define SWITCH_MIN_INTERVAL_MS   2000  // 連打による二重記録の防止 [ms]

//=============================================================================
// オンボードLED点滅（押下フィードバック）
//=============================================================================
// RX/TX LED は active-low: RXLED0/TXLED0 で点灯・RXLED1/TXLED1 で消灯。
// なお TX LED は USB CDC が送信のたびに短くパルスするため、日常運用でも
// テレメトリ(1秒毎)で微点滅する（押下時は RX LED も含めてはっきり点滅する）。
#define LED_BLINK_ON_MS   150
#define LED_BLINK_OFF_MS  100
#define LED_BLINK_COUNT   6

//=============================================================================
// シリアル送出周期
//=============================================================================
#define HEARTBEAT_INTERVAL_MS  5000UL   // HB（心拍）送出周期
#define TELEMETRY_INTERVAL_MS  1000UL   // T（テレメトリ）送出周期

//=============================================================================
// MCU状態（テレメトリの state・BLINK は押下フィードバック点滅中）
//=============================================================================
enum McuState : uint8_t {
    MCU_STATE_IDLE = 0,
    MCU_STATE_BLINK = 1,
};

//=============================================================================
// EEPROM 設定（名前のみ。しきい値はスイッチ化で不要になった）
//=============================================================================
struct DeviceConfig {
    uint32_t magic;
    uint16_t version;
    char     name[16];    // デバイス名（複数台識別用・SET:name で変更）
};

#define CONFIG_MAGIC   0x4D43504DUL  // "MCPM"
#define CONFIG_VERSION 2

//=============================================================================
// グローバル変数
//=============================================================================
extern DeviceConfig g_cfg;
extern unsigned long g_currentMillis;
extern uint8_t g_mcuState;

// 未送信の検知（daemon 未接続中に押された分・再接続時に1回だけ再送）
extern bool g_intakePending;
extern unsigned long g_intakePendingAt;

//=============================================================================
// プロトタイプ宣言
//=============================================================================
// medicine_case_promicro.ino
void configLoad();
void configSave();
const char* firmwareVersion();
const char* mcuStateName(uint8_t s);
void logInfo(const char* tag, const String& msg);

// medicine_case_promicro_switch.ino
void setupSwitch();
void pollSwitch();
void updateBlink();
void blinkStart();

// medicine_case_promicro_serial.ino
void serialPoll();
void serialSendIntake();

#endif // MEDICINE_CASE_PROMICRO_H
