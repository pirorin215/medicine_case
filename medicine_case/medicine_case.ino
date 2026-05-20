/**
 * Medicine Case - XIAO BLE Sense based medicine case reminder
 *
 * Features:
 * - BLE time synchronization with smartphone
 * - 6-axis sensor (LSM6DS3TRC) for medicine intake detection
 * - Medicine intake notification (morning, afternoon, evening)
 * - Schedule-based notification system
 * - Always powered via USB
 */

#include <Adafruit_TinyUSB.h>
#include "medicine_case.h"

// --- Global Variables ---
volatile uint32_t g_currentTimestamp = 0;  // Unix timestamp (JST)
bool g_deviceConnected = false;
LedState g_currentLedState = LED_STATE_BOOT;
unsigned long g_currentMillis = 0;
unsigned long g_startupMillis = 0;
bool g_timeSynced = false;
bool g_bleNotificationEnabled = true;  // Default: enabled

// Sensor
LSM6DS3* g_lsm6ds3 = nullptr;
float g_currentPitch = 0.0f;
float g_currentRoll = 0.0f;
float g_stablePitch = 0.0f;
float g_stableRoll = 0.0f;
IntakeDetectionState g_detectionState = DETECTION_STATE_IDLE;
unsigned long g_movementStartTime = 0;

// Medicine schedules (Morning, Afternoon, Evening)
MedicineSchedule g_schedules[MAX_SCHEDULES] = {
    {true, 7, 0, 0, 0},    // Morning: 7:00 AM
    {true, 12, 0, 0, 0},   // Afternoon: 12:00 PM
    {true, 19, 0, 0, 0}    // Evening: 7:00 PM
};

// Notification tracking
uint32_t g_lastNotificationCheck = 0;
uint8_t g_dailyNotificationCount = 0;
uint32_t g_lastNotificationDay = 0;

// Time management
unsigned long g_lastCounterMillis = 0;

// --- Time Helper Functions ---
int getHours() {
    return (g_currentTimestamp % 86400) / 3600;
}

int getMinutes() {
    return (g_currentTimestamp % 3600) / 60;
}

int getSeconds() {
    return g_currentTimestamp % 60;
}

// --- System Utilities ---

// Update timestamp (simple tick counter)
void updateTimestamp() {
    if (g_currentMillis - g_lastCounterMillis >= TIME_UPDATE_INTERVAL_MS) {
        g_currentTimestamp++;
        g_lastCounterMillis = g_currentMillis;
    }
}

// Get current day (for daily notification reset)
uint32_t getCurrentDay() {
    return g_currentTimestamp / 86400;
}

// --- Settings Management ---

void loadSettings() {
    logPrint("MEDICINE_CASE", "Loading settings from InternalFS...");
    InternalFS.begin();

    File file(InternalFS);

    if (file.open("/settings.dat", FILE_O_READ)) {
        // Load schedules
        if (file.read((uint8_t*)g_schedules, sizeof(g_schedules)) == sizeof(g_schedules)) {
            for (int i = 0; i < MAX_SCHEDULES; i++) {
                const char* scheduleName = (i == 0) ? "Morning" : (i == 1) ? "Afternoon" : "Evening";
                logPrint("MEDICINE_CASE", "  %s: %s %02d:%02d (taken=%d)",
                         scheduleName,
                         g_schedules[i].enabled ? "enabled" : "disabled",
                         g_schedules[i].hour,
                         g_schedules[i].minute,
                         g_schedules[i].taken);
            }
            logPrint("MEDICINE_CASE", "Settings loaded successfully.");
        }
        file.close();
    } else {
        logPrint("MEDICINE_CASE", "No settings file found. Using defaults.");
    }
    Serial.flush();
}

void saveSettings() {
    logPrint("MEDICINE_CASE", "Saving settings to InternalFS...");
    InternalFS.remove("/settings.dat");
    File file(InternalFS);

    if (file.open("/settings.dat", FILE_O_WRITE)) {
        file.write((uint8_t*)g_schedules, sizeof(g_schedules));
        file.close();
        logPrint("MEDICINE_CASE", "Settings saved successfully.");
    } else {
        logPrint("MEDICINE_CASE", "Failed to open settings file for writing.");
    }
    Serial.flush();
}

// --- BLE Command Handlers ---

void handleScheduleConfig(const char* command) {
    // Format: SET:schedule:index:enabled:hour:minute
    // Example: SET:schedule:0:1:7:0 (Morning schedule, enabled, 7:00 AM)
    const char* p = command + strlen("SET:schedule:");

    int index = atoi(p);
    p = strchr(p, ':') + 1;

    int enabled = atoi(p);
    p = strchr(p, ':') + 1;

    int hour = atoi(p);
    p = strchr(p, ':') + 1;

    int minute = atoi(p);

    if (index >= 0 && index < MAX_SCHEDULES) {
        g_schedules[index].enabled = (enabled != 0);
        g_schedules[index].hour = (int8_t)hour;
        g_schedules[index].minute = (int8_t)minute;

        const char* scheduleName = (index == 0) ? "Morning" : (index == 1) ? "Afternoon" : "Evening";
        logPrint("MEDICINE_CASE", "%s schedule updated: %s %02d:%02d",
                 scheduleName,
                 g_schedules[index].enabled ? "enabled" : "disabled",
                 g_schedules[index].hour,
                 g_schedules[index].minute);

        saveSettings();
        sendResponse("OK: Schedule updated");
    } else {
        logPrint("MEDICINE_CASE", "Invalid schedule index: %d", index);
        sendResponse("ERROR: Invalid schedule index");
    }
}

void handleNotificationConfig(const char* command) {
    // Format: SET:notification:enabled
    // Example: SET:notification:1 (Enable notifications)
    const char* enabledStr = command + strlen("SET:notification:");
    int enabled = atoi(enabledStr);

    g_bleNotificationEnabled = (enabled != 0);

    logPrint("MEDICINE_CASE", "BLE notification %s", g_bleNotificationEnabled ? "enabled" : "disabled");
    saveSettings();
    sendResponse("OK: Notification config updated");
}

void handleGetStatus() {
    logPrint("MEDICINE_CASE", "Processing GET:status command");

    // Build status response
    char statusResponse[256];
    snprintf(statusResponse, sizeof(statusResponse),
             "OK:status:%d,%d,%d,%d,%d,%d,%d,%d,%d",
             g_schedules[0].enabled, g_schedules[0].hour, g_schedules[0].minute, g_schedules[0].taken,
             g_schedules[1].enabled, g_schedules[1].hour, g_schedules[1].minute, g_schedules[1].taken,
             g_schedules[2].enabled, g_schedules[2].hour, g_schedules[2].minute, g_schedules[2].taken);

    sendResponse(statusResponse);
    logPrint("MEDICINE_CASE", "Status response sent");
}

void handleGetVersion() {
    logPrint("MEDICINE_CASE", "Processing GET:version command");

    char versionResponse[64];
    snprintf(versionResponse, sizeof(versionResponse), "OK:version:%d.%d.%d",
             FIRMWARE_VERSION_MAJOR, FIRMWARE_VERSION_MINOR, FIRMWARE_VERSION_PATCH);
    sendResponse(versionResponse);

    logPrint("MEDICINE_CASE", "Version response sent: %d.%d.%d",
             FIRMWARE_VERSION_MAJOR, FIRMWARE_VERSION_MINOR, FIRMWARE_VERSION_PATCH);
}

// --- Main Functions ---

void setup() {
    // TinyUSB Serial初期化（Blinkプロジェクト成功パターン）
    Serial.begin(115200);
    while (!Serial) {
        delay(10);  // Serial接続を待つ
    }

    Serial.println("========================================");
    Serial.println("Medicine Case v1.0.6 - TinyUSB Serial");
    Serial.println("========================================");
    Serial.flush();

    g_startupMillis = millis();

    // LED初期化
    setupLed();

    Serial.println("LED: INITIALIZED");
    Serial.flush();

    // センサー初期化
    Serial.println("SENSOR: INITIALIZING...");
    setupSensor();
    Serial.println("SENSOR: OK");
    Serial.flush();

    // BLE初期化
    Serial.println("BLE: INITIALIZING...");
    setupBLE();
    Serial.println("BLE: OK");
    Serial.flush();

    // BLEライブラリによるLED制御を上書きする
    // BLE初期化後に再度LEDを設定する
    Serial.println("LED: Re-initializing after BLE...");
    Serial.flush();

    // まず青色LED（D12）を消す
    digitalWrite(12, HIGH);  // Blue OFF

    // 青色LED（D12）を入力モードにして、BLEライブラリからの制御を無効化
    pinMode(12, INPUT);

    // 赤（D11）と緑（D13）は出力モードのまま（制御可能）
    pinMode(11, OUTPUT);  // LED_RED
    pinMode(13, OUTPUT);  // LED_GREEN

    // 赤と緑を消す
    digitalWrite(11, HIGH);  // Red OFF
    digitalWrite(13, HIGH);  // Green OFF

    Serial.println("LED: Blue LED (D12) disabled (INPUT mode)");
    Serial.println("LED: Red (D11) and Green (D13) ready for control");
    Serial.flush();

    delay(100);

    Serial.println("LED: Re-initialization complete");
    Serial.flush();

    // 設定読み込み
    loadSettings();

    g_lastCounterMillis = millis();
    g_lastNotificationCheck = g_currentTimestamp;
    g_lastNotificationDay = getCurrentDay();

    Serial.println("========================================");
    Serial.println("SYSTEM: READY");
    Serial.println("========================================");
    Serial.flush();

    // 未接続状態のLED設定
    setLedState(LED_STATE_NO_SYNC);

    Serial.println("LOOP: STARTING");
    Serial.flush();
}

void loop() {
    // Get current time once for this loop iteration
    g_currentMillis = millis();

    // Update timestamp
    updateTimestamp();

    // Update sensor
    updateSensor();

    // Check for medicine intake
    detectMedicineIntake();

    // Check notifications (every minute)
    if (g_currentTimestamp - g_lastNotificationCheck >= 60) {
        checkNotifications();
        g_lastNotificationCheck = g_currentTimestamp;

        // Reset daily notification count if day changed
        uint32_t currentDay = getCurrentDay();
        if (currentDay != g_lastNotificationDay) {
            g_dailyNotificationCount = 0;
            g_lastNotificationDay = currentDay;

            // Reset "taken" status for new day
            for (int i = 0; i < MAX_SCHEDULES; i++) {
                g_schedules[i].taken = 0;
                g_schedules[i].takenTimestamp = 0;
            }
            saveSettings();

            Serial.println("MEDICINE_CASE: New day - Reset status");
            Serial.flush();
        }
    }

    // Update LED
    updateLed();

    // 定期的なシステム状態ログ出力（5秒ごと）
    static unsigned long lastSystemLogTime = 0;
    if (g_currentMillis - lastSystemLogTime >= 5000) {
        Serial.print("SYSTEM: Time=");
        Serial.print(g_currentTimestamp);
        Serial.print(", Connected=");
        Serial.print(g_deviceConnected);
        Serial.print(", Synced=");
        Serial.println(g_timeSynced);
        Serial.flush();
        lastSystemLogTime = g_currentMillis;
    }

    delay(10);
}
