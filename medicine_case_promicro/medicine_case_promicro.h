#ifndef MEDICINE_CASE_PROMICRO_H
#define MEDICINE_CASE_PROMICRO_H

#include <Arduino.h>
#include <EEPROM.h>

//=============================================================================
// ファームウェアバージョン（コード変更時は PATCH を bump すること・機構変更は MINOR）
//=============================================================================
#define FIRMWARE_VERSION_MAJOR 2
#define FIRMWARE_VERSION_MINOR 3
#define FIRMWARE_VERSION_PATCH 1

//=============================================================================
// マイクロスイッチ（服薬ボタン・複数対応）
//=============================================================================
// COM -> GND / NO -> GPIO の1極接続。INPUT_PULLUP で押すと LOW。
// idx は配列順（D4=0, D5=1, D6=2, D7=3, D8=4, D9=5）。未配線のピンは放置でよい。
// スイッチ名はファームでは持たない（Mac 側 config.json の switches で管理）。
#define SWITCH_PINS {4, 5, 6, 7, 8, 9}
#define SWITCH_COUNT 6
#define SWITCH_DEBOUNCE_MS       30    // チャタリング除去 [ms]
#define SWITCH_MIN_INTERVAL_MS   2000  // 同一スイッチ連打による二重記録の防止 [ms]

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
// 待機中の送信は TX LED のパルス（コアの CDC が Serial.write 毎に点灯）になるため
// 最小限にする。HB 60秒 = 1分1回の微点滅だけが待機中の見た目。
#define HEARTBEAT_INTERVAL_MS  60000UL   // HB（心拍）送出周期

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

// 未送信の検知（daemon 未接続中に押された分・再接続時に再送）。
// スイッチ単位で保持する（v2.3.1: 旧単数スロットは未接続中の複数押下で
// 古い分を上書き消失していた。別スイッチの押下は全て残る・同一スイッチの
// 複数回は初回時刻を優先し1件に束ねる）
extern bool g_intakePending[SWITCH_COUNT];
extern unsigned long g_intakePendingAt[SWITCH_COUNT];

//=============================================================================
// プロトタイプ宣言
//=============================================================================
// medicine_case_promicro.ino
void configLoad();
void configSave();
const char* firmwareVersion();
void logInfo(const char* tag, const String& msg);

// medicine_case_promicro_switch.ino
void setupSwitch();
void pollSwitch();
void updateBlink();
void blinkStart();

// medicine_case_promicro_serial.ino
void serialPoll();
void serialSendIntake(uint8_t idx);

#endif // MEDICINE_CASE_PROMICRO_H
