#ifndef MEDICINE_CASE_H
#define MEDICINE_CASE_H

#include <Arduino.h>
#include <bluefruit.h>
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>
#include <LSM6DS3.h>

using namespace Adafruit_LittleFS_Namespace;

// --- Firmware Version Information ---
#define FIRMWARE_VERSION_MAJOR 1
#define FIRMWARE_VERSION_MINOR 0
#define FIRMWARE_VERSION_PATCH 14

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
#define MOVEMENT_THRESHOLD_DEG  70.0f  // Minimum angle change to detect as medicine intake (lowered from 90)
#define MOVEMENT_STABILITY_MS   500    // Time to wait for stable position after movement
#define SENSOR_UPDATE_INTERVAL_MS 100  // Sensor update interval

// --- Notification Settings ---
#define NOTIFICATION_CHECK_INTERVAL_MS 60000  // Check notifications every 60 seconds
#define MAX_NOTIFICATIONS_PER_DAY  24         // Max 24 notifications per day (1 per hour)

// --- Medicine Schedule Settings ---
#define MAX_SCHEDULES 3  // Morning, Afternoon, Evening

// --- Time Settings ---
#define TIME_UPDATE_INTERVAL_MS 1000  // Update timestamp every 1 second

// --- Onboard LED state ---
enum LedState {
    LED_STATE_BOOT,              // Startup: Red solid
    LED_STATE_NO_SYNC,           // Not connected + not synced: Red blinking (1s)
    LED_STATE_SYNCED,            // Not connected + synced: Green solid
    LED_STATE_CONNECTED_NO_SYNC, // Connected + not synced: Blue blinking (1s)
    LED_STATE_CONNECTED_SYNCED,  // Connected + synced: Blue solid
    LED_STATE_ERROR              // Error: Red rapid blinking (0.2s)
};

// --- Medicine Schedule Structure ---
struct MedicineSchedule {
    bool enabled;      // Whether this schedule is enabled
    int8_t hour;       // Hour (0-23)
    int8_t minute;     // Minute (0-59)
    uint8_t taken;     // Whether medicine was taken (0=not taken, 1=taken)
    uint32_t takenTimestamp;  // Timestamp when medicine was taken
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
extern bool g_bleNotificationEnabled;        // BLE notification enabled

// Sensor
extern LSM6DS3* g_lsm6ds3;                    // 6-axis sensor
extern float g_currentPitch;                  // Current pitch angle
extern float g_currentRoll;                   // Current roll angle
extern float g_stablePitch;                   // Stable pitch angle
extern float g_stableRoll;                    // Stable roll angle
extern IntakeDetectionState g_detectionState; // Detection state
extern unsigned long g_movementStartTime;     // When movement started

// Medicine schedules
extern MedicineSchedule g_schedules[MAX_SCHEDULES];  // Morning, Afternoon, Evening

// Notification tracking
extern uint32_t g_lastNotificationCheck;      // Last notification check timestamp
extern uint8_t g_dailyNotificationCount;      // Notifications sent today

// --- Function Prototypes ---
void setupBLE();
void setupSensor();
void handleTimeSync(const char* command);
void handleScheduleConfig(const char* command);
void handleNotificationConfig(const char* command);
void handleGetStatus();
void handleGetVersion();
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
void checkStablePosition();
void recordMedicineIntake(int scheduleIndex);

// Notification functions
void checkNotifications();
bool shouldNotify(int scheduleIndex);
void sendIntakeNotification(int scheduleIndex);

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
