#ifndef MEDICINE_CASE_H
#define MEDICINE_CASE_H

#include <Arduino.h>
#include <bluefruit.h>
#include <Adafruit_TinyUSB.h>
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>
#include <LSM6DS3.h>

using namespace Adafruit_LittleFS_Namespace;

// --- Firmware Version Information ---
#define FIRMWARE_VERSION_MAJOR 1
#define FIRMWARE_VERSION_MINOR 1
#define FIRMWARE_VERSION_PATCH 0

// --- BLE Settings ---
#define BLE_DEVICE_NAME       "MedicineCase-0001"

// --- BLE UUIDs ---
// Service UUID
#define BLE_SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914d"

// Characteristic UUIDs
#define BLE_CHAR_COMMAND_UUID   "beb5483e-36e1-4688-b7f5-ea07361b26a0"  // Write: Command
#define BLE_CHAR_RESPONSE_UUID  "beb5483e-36e1-4688-b7f5-ea07361b26a2"  // Notify: Response
#define BLE_CHAR_SENSOR_UUID    "beb5483e-36e1-4688-b7f5-ea07361b26a3"  // Notify: Sensor data

// --- Sensor Settings ---
#define DEFAULT_MOVEMENT_THRESHOLD_DEG  70.0f  // Default minimum angle change
#define MOVEMENT_STABILITY_MS   500    // Time to wait for stable position after movement
#define SENSOR_UPDATE_INTERVAL_MS 100  // Sensor update interval
#define DEFAULT_COOLDOWN_TIME_MS 30000     // Default cooldown time (30 seconds)

// --- Time Settings ---
#define TIME_UPDATE_INTERVAL_MS 1000  // Update timestamp every 1 second

// --- Onboard LED state ---
enum LedState {
    LED_STATE_BOOT,              // Startup: Red solid
    LED_STATE_NO_SYNC,           // Not connected + not synced: Red solid
    LED_STATE_SYNCED,            // Not connected + synced: Green solid
    LED_STATE_CONNECTED_NO_SYNC, // Connected + not synced: Yellow solid
    LED_STATE_CONNECTED_SYNCED,  // Connected + synced: Green solid
    LED_STATE_ERROR              // Error: Red rapid blinking (0.2s)
};

// --- Medicine Intake Detection State ---
enum IntakeDetectionState {
    DETECTION_STATE_IDLE,           // No movement detected
    DETECTION_STATE_MOVING,         // Movement detected
    DETECTION_STATE_STABILIZING,    // Waiting for stable position
    DETECTION_STATE_CONFIRMED       // Intake confirmed
};

// --- Global Variables ---
extern volatile uint32_t g_currentTimestamp;  // Unix timestamp (JST)
extern bool g_deviceConnected;               // BLE connection status
extern LedState g_currentLedState;           // Current LED state
extern unsigned long g_currentMillis;        // Current time for this loop iteration
extern unsigned long g_startupMillis;        // Startup time (for log timestamps)
extern bool g_timeSynced;                    // Time synced status

// Last intake timestamp (0 = no pending intake)
extern uint32_t g_lastIntakeTimestamp;

// Sensor
extern LSM6DS3* g_lsm6ds3;                    // 6-axis sensor
extern float g_currentPitch;                  // Current pitch angle
extern float g_currentRoll;                   // Current roll angle
extern float g_stablePitch;                   // Stable pitch angle
extern float g_stableRoll;                    // Stable roll angle
extern IntakeDetectionState g_detectionState; // Detection state
extern unsigned long g_movementStartTime;     // When movement started

// Detection settings (configurable via BLE)
extern float g_movementThreshold;             // Movement detection threshold (degrees)
extern unsigned long g_cooldownTime;          // Cooldown time after detection (ms)

// --- Function Prototypes ---
void setupBLE();
void setupSensor();
void handleTimeSync(const char* command);
void handleDetectionConfig(const char* command);
void handleGetStatus();
void handleGetVersion();
void handleGetIntake();
void handleClearIntake();
void loadSettings();
void saveSettings();
void sendResponse(const char* message);
void sendSensorNotification(const char* data);

// LED Debug functions
void ledDebugShowPattern(int pattern);
void ledDebugShowAngle(float pitch, float roll);

// Time functions
void updateTimestamp();
int getHours();
int getMinutes();
int getSeconds();

// Sensor functions
void updateSensor();
bool detectMedicineIntake();

// LED functions
void setupLed();
void updateLed();
void setLedState(LedState state);
void setLedColor(bool red, bool green, bool blue);
void setLedError();
void updateLedStateBasedOnStatus();

// Logging functions
void setupLog();
void logPrint(const char* tag, const char* format, ...);
void logPrintRaw(const char* format, ...);
#define logPrintln(format, ...) logPrint("", format, ##__VA_ARGS__)

#endif // MEDICINE_CASE_H
