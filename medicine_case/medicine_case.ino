/**
 * Medicine Case - XIAO BLE Sense based medicine case reminder
 *
 * Architecture (v1.1.0):
 * - Firmware: intake detection only (no schedule management)
 * - App: schedule management, notifications, history
 *
 * Firmware role:
 * - Detect medicine intake via 6-axis sensor
 * - Store single intake timestamp for app to retrieve
 * - BLE communication for time sync and detection settings
 */

#include "medicine_case.h"

// --- Global Variables ---
volatile uint32_t g_currentTimestamp = 0;  // Unix timestamp (JST)
bool g_deviceConnected = false;
LedState g_currentLedState = LED_STATE_BOOT;
unsigned long g_currentMillis = 0;
unsigned long g_startupMillis = 0;
bool g_timeSynced = false;

// Last intake timestamp (0 = no pending intake)
uint32_t g_lastIntakeTimestamp = 0;

// Sensor
LSM6DS3* g_lsm6ds3 = nullptr;
float g_currentPitch = 0.0f;
float g_currentRoll = 0.0f;
float g_stablePitch = 0.0f;
float g_stableRoll = 0.0f;
IntakeDetectionState g_detectionState = DETECTION_STATE_IDLE;
unsigned long g_movementStartTime = 0;

// Detection settings (configurable via BLE)
float g_movementThreshold = DEFAULT_MOVEMENT_THRESHOLD_DEG;
unsigned long g_cooldownTime = DEFAULT_COOLDOWN_TIME_MS;

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

// --- Settings Management ---

void loadSettings() {
    logPrint("MEDICINE_CASE", "Loading settings from InternalFS...");
    InternalFS.begin();

    File file(InternalFS);

    // Load detection settings only
    if (file.open("/detection.dat", FILE_O_READ)) {
        float threshold;
        unsigned long cooldown;
        if (file.read((uint8_t*)&threshold, sizeof(threshold)) == sizeof(threshold)) {
            g_movementThreshold = threshold;
            logPrint("MEDICINE_CASE", "  Movement threshold: %.1f degrees", g_movementThreshold);
        }
        if (file.read((uint8_t*)&cooldown, sizeof(cooldown)) == sizeof(cooldown)) {
            g_cooldownTime = cooldown;
            logPrint("MEDICINE_CASE", "  Cooldown time: %lu ms", g_cooldownTime);
        }
        file.close();
        logPrint("MEDICINE_CASE", "Settings loaded successfully.");
    } else {
        logPrint("MEDICINE_CASE", "No settings file found. Using defaults.");
    }
    Serial.flush();
}

void saveSettings() {
    logPrint("MEDICINE_CASE", "Saving settings to InternalFS...");

    // Save detection settings only
    InternalFS.remove("/detection.dat");
    File file(InternalFS);

    if (file.open("/detection.dat", FILE_O_WRITE)) {
        file.write((uint8_t*)&g_movementThreshold, sizeof(g_movementThreshold));
        file.write((uint8_t*)&g_cooldownTime, sizeof(g_cooldownTime));
        file.close();
        logPrint("MEDICINE_CASE", "Detection settings saved: threshold=%.1f deg, cooldown=%lu ms",
                 g_movementThreshold, g_cooldownTime);
    } else {
        logPrint("MEDICINE_CASE", "Failed to open detection settings file for writing.");
    }

    Serial.flush();
}

// --- BLE Command Handlers ---

void handleDetectionConfig(const char* command) {
    // Format: SET:detection:type:value
    // Examples:
    //   SET:detection:angle:70.0  (Set movement threshold to 70 degrees)
    //   SET:detection:cooldown:30000  (Set cooldown to 30000ms)

    const char* typeStart = command + strlen("SET:detection:");

    // Check detection type
    if (strncmp(typeStart, "angle:", 6) == 0) {
        // Set movement threshold
        const char* valueStr = typeStart + 6;
        float value = atof(valueStr);

        if (value >= 10.0f && value <= 180.0f) {
            g_movementThreshold = value;
            logPrint("MEDICINE_CASE", "Movement threshold set to %.1f degrees", g_movementThreshold);
            saveSettings();
            sendResponse("OK: Detection angle updated");
        } else {
            logPrint("MEDICINE_CASE", "Invalid angle value: %.1f (valid range: 10-180)", value);
            sendResponse("ERROR: Invalid angle value (valid range: 10-180)");
        }

    } else if (strncmp(typeStart, "cooldown:", 8) == 0) {
        // Set cooldown time
        const char* valueStr = typeStart + 8;
        unsigned long value = (unsigned long)atol(valueStr);

        if (value >= 1000 && value <= 300000) {  // 1 second to 5 minutes
            g_cooldownTime = value;
            logPrint("MEDICINE_CASE", "Cooldown time set to %lu ms (%.1f seconds)",
                     g_cooldownTime, g_cooldownTime / 1000.0f);
            saveSettings();
            sendResponse("OK: Detection cooldown updated");
        } else {
            logPrint("MEDICINE_CASE", "Invalid cooldown value: %lu (valid range: 1000-300000)", value);
            sendResponse("ERROR: Invalid cooldown value (valid range: 1000-300000)");
        }

    } else {
        logPrint("MEDICINE_CASE", "Unknown detection config type: %s", typeStart);
        sendResponse("ERROR: Unknown detection config type");
    }
}

void handleGetStatus() {
    logPrint("MEDICINE_CASE", "Processing GET:status command");

    char statusResponse[128];
    snprintf(statusResponse, sizeof(statusResponse),
             "OK:status:connected=%d,synced=%d,pending_intake=%lu",
             g_deviceConnected ? 1 : 0,
             g_timeSynced ? 1 : 0,
             g_lastIntakeTimestamp);

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

void handleGetIntake() {
    logPrint("MEDICINE_CASE", "Processing GET:intake command");

    if (g_lastIntakeTimestamp > 0) {
        char response[64];
        snprintf(response, sizeof(response), "INTAKE:%lu", g_lastIntakeTimestamp);
        sendResponse(response);
        logPrint("MEDICINE_CASE", "Intake timestamp sent: %lu", g_lastIntakeTimestamp);
    } else {
        sendResponse("NONE");
        logPrint("MEDICINE_CASE", "No pending intake");
    }
}

void handleClearIntake() {
    logPrint("MEDICINE_CASE", "Processing CLR:intake command");
    g_lastIntakeTimestamp = 0;
    sendResponse("OK: Intake cleared");
    logPrint("MEDICINE_CASE", "Intake timestamp cleared");
}

// --- Main Functions ---

void setup() {
    Serial.begin(115200);
    unsigned long startTime = millis();
    while (!Serial && (millis() - startTime < 5000)) {
       delay(10);
    }

    Serial.println("========================================");
    Serial.println("Medicine Case v1.1.0");
    Serial.println("========================================");
    Serial.flush();

    g_startupMillis = millis();

    setupLed();
    Serial.println("LED: INITIALIZED");
    Serial.flush();

    setupSensor();
    Serial.println("SENSOR: OK");
    Serial.flush();

    setupBLE();
    Serial.println("BLE: OK");
    Serial.flush();

    // Disable BLE library's LED control
    digitalWrite(12, HIGH);  // Blue OFF
    pinMode(12, INPUT);      // Blue LED disabled
    pinMode(11, OUTPUT);     // LED_RED
    pinMode(13, OUTPUT);     // LED_GREEN
    digitalWrite(11, HIGH);  // Red OFF
    digitalWrite(13, HIGH);  // Green OFF
    Serial.println("LED: Blue LED disabled, Red/Green ready");
    Serial.flush();

    delay(100);

    loadSettings();
    g_lastCounterMillis = millis();

    Serial.println("========================================");
    Serial.println("SYSTEM: READY");
    Serial.println("========================================");
    Serial.flush();

    setLedState(LED_STATE_NO_SYNC);

    Serial.println("LOOP: STARTING");
    Serial.flush();
}

void loop() {
    g_currentMillis = millis();

    // Update timestamp
    updateTimestamp();

    // Update sensor
    updateSensor();

    // Detect medicine intake
    detectMedicineIntake();

    // Update LED
    updateLed();

    // Periodic system log (every 5 seconds)
    static unsigned long lastSystemLogTime = 0;
    if (g_currentMillis - lastSystemLogTime >= 5000) {
        Serial.print("SYSTEM: Time=");
        Serial.print(g_currentTimestamp);
        Serial.print(", Connected=");
        Serial.print(g_deviceConnected);
        Serial.print(", Synced=");
        Serial.print(g_timeSynced);
        Serial.print(", PendingIntake=");
        Serial.println(g_lastIntakeTimestamp);
        Serial.flush();
        lastSystemLogTime = g_currentMillis;
    }

    delay(10);
}
