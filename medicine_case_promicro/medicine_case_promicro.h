#ifndef MEDICINE_CASE_PROMICRO_H
#define MEDICINE_CASE_PROMICRO_H

#include <Arduino.h>
#include <Wire.h>
#include <EEPROM.h>

//=============================================================================
// ファームウェアバージョン（コード変更時は PATCH を bump すること）
//=============================================================================
#define FIRMWARE_VERSION_MAJOR 2
#define FIRMWARE_VERSION_MINOR 0
#define FIRMWARE_VERSION_PATCH 1

//=============================================================================
// ハードウェア定数
//=============================================================================
// GY-BMI160 モジュールの I2C アドレス（SDO -> GND で 0x68 / VCC で 0x69）
#define IMU_I2C_ADDR 0x68

//=============================================================================
// 検知定数（BLE版 medicine_case/medicine_case.h から移植・既定値は変えない）
//=============================================================================
#define DEFAULT_MOVEMENT_THRESHOLD_DEG 70.0f  // 既定の検知角度 [deg]
#define MOVEMENT_STABILITY_MS    500          // 動き収束待ち時間 [ms]
#define SENSOR_UPDATE_INTERVAL_MS 100         // センサ更新間隔 [ms]
#define DEFAULT_COOLDOWN_TIME_MS 30000UL      // 既定クールダウン [ms]

//=============================================================================
// シリアル送出周期
//=============================================================================
#define HEARTBEAT_INTERVAL_MS  5000UL   // HB（心拍）送出周期
#define TELEMETRY_INTERVAL_MS  1000UL   // T（テレメトリ）送出周期

//=============================================================================
// 検知状態
//=============================================================================
enum DetectionState : uint8_t {
    DETECTION_STATE_IDLE = 0,
    DETECTION_STATE_MOVING = 1,
    DETECTION_STATE_CONFIRMED = 2,
};

//=============================================================================
// EEPROM 設定（32U4 の内蔵EEPROM。BLE版の InternalFileSystem 相当）
//=============================================================================
struct DeviceConfig {
    uint32_t magic;
    uint16_t version;
    float    angle;       // 検知角度 [deg]（範囲 10-180）
    uint32_t cooldownMs;  // クールダウン [ms]（範囲 1000-300000）
    char     name[16];    // デバイス名（複数台識別用・SET:name で変更）
};

#define CONFIG_MAGIC   0x4D43504DUL  // "MCPM"
#define CONFIG_VERSION 1

//=============================================================================
// グローバル変数
//=============================================================================
extern DeviceConfig g_cfg;
extern unsigned long g_currentMillis;
extern uint8_t g_detectionState;
extern float g_currentPitch;
extern float g_currentRoll;
extern bool g_imuEnabled;

// 未送信の検知（daemon 未接続中に検知した分・再接続時に1回だけ再送）
extern bool g_intakePending;
extern float g_intakePendingMax;
extern unsigned long g_intakePendingAt;

//=============================================================================
// プロトタイプ宣言
//=============================================================================
// medicine_case_promicro.ino
void configLoad();
void configSave();
const char* firmwareVersion();
const char* detectionStateName(uint8_t s);
void logInfo(const char* tag, const String& msg);

// medicine_case_promicro_imu.ino
void setupIMU();
void updateSensor();
bool detectMedicineIntake();

// medicine_case_promicro_serial.ino
void serialPoll();
void serialSendIntake(float maxChange);

#endif // MEDICINE_CASE_PROMICRO_H
