/**
 * Utility Functions for Medicine Case
 *
 * - LED status indication
 * - Logging utilities
 */

#include "medicine_case.h"

// --- LED Functions ---

void setupLed() {
    logPrint("LED", "Initializing onboard LED...");

    // Initialize XIAO BLE Sense RGB LED pins (CORRECT PINOUT)
    // Red = D11, Green = D13, Blue = D12 (active low)
    pinMode(11, OUTPUT);  // LED_RED
    pinMode(13, OUTPUT);  // LED_GREEN
    pinMode(12, OUTPUT);  // LED_BLUE

    // Turn off all LEDs initially
    digitalWrite(11, HIGH);  // Red OFF
    digitalWrite(13, HIGH);  // Green OFF
    digitalWrite(12, HIGH);  // Blue OFF

    logPrint("LED", "✅ RGB LED initialized (R=11, G=13, B=12)");
}

void updateLed() {
    // 点滅をやめて、点灯のみにする
    switch (g_currentLedState) {
        case LED_STATE_BOOT:
            // Solid purple during boot (Red + Blue)
            setLedColor(true, false, true);
            break;

        case LED_STATE_NO_SYNC:
            // Solid red - Not connected (changed from blinking)
            setLedColor(true, false, false);
            break;

        case LED_STATE_SYNCED:
            // Solid green - Synced but not connected
            setLedColor(false, true, false);
            break;

        case LED_STATE_CONNECTED_NO_SYNC:
            // Solid yellow - Connected but not synced (changed from blinking)
            setLedColor(true, true, false);  // Red + Green = Yellow
            break;

        case LED_STATE_CONNECTED_SYNCED:
            // Solid green - Connected and synced (ready to detect)
            setLedColor(false, true, false);
            break;

        case LED_STATE_ERROR:
            // Red rapid blinking (only error state keeps blinking)
            static unsigned long lastLedUpdate = 0;
            static bool ledState = false;
            if (g_currentMillis - lastLedUpdate >= 200) {
                ledState = !ledState;
                setLedColor(ledState, false, false);
                lastLedUpdate = g_currentMillis;
            }
            break;
    }
}

void setLedState(LedState state) {
    g_currentLedState = state;
}

void setLedColor(bool red, bool green, bool blue) {
    // XIAO BLE Sense onboard LED is active low
    // Red = D11, Green = D13, Blue = D12 (DISABLED - INPUT mode)
    pinMode(11, OUTPUT);  // LED_RED
    pinMode(13, OUTPUT);  // LED_GREEN
    // pinMode(12, INPUT);  // LED_BLUE disabled (INPUT mode)

    digitalWrite(11, red ? LOW : HIGH);   // Red
    digitalWrite(13, green ? LOW : HIGH); // Green
    // digitalWrite(12, blue ? LOW : HIGH); // Blue disabled
}

void setLedError() {
    setLedState(LED_STATE_ERROR);
    logPrint("LED", "Error state set");
}

void updateLedStateBasedOnStatus() {
    if (g_deviceConnected) {
        if (g_timeSynced) {
            setLedState(LED_STATE_CONNECTED_SYNCED);
        } else {
            setLedState(LED_STATE_CONNECTED_NO_SYNC);
        }
    } else {
        if (g_timeSynced) {
            setLedState(LED_STATE_SYNCED);
        } else {
            setLedState(LED_STATE_NO_SYNC);
        }
    }
}

// --- Logging Functions ---

void setupLog() {
    // Serial buffer configuration not available on this platform
    // Serial.setTxBufferSize(2048);
    // Serial.setRxBufferSize(2048);

    // シリアルポートが確実に初期化されるのを待つ
    delay(500);

    // テストメッセージを送信
    Serial.println();
    Serial.println("========================================");
    Serial.printf("Medicine Case v%d.%d.%d\n", FIRMWARE_VERSION_MAJOR, FIRMWARE_VERSION_MINOR, FIRMWARE_VERSION_PATCH);
    Serial.println("✅ Serial port ready (115200 baud)");
    Serial.println("✅ Logging system initialized");
    Serial.println("========================================");
    Serial.println();

    logPrint("LOG", "📡 Serial communication started");
}

void logPrint(const char* tag, const char* format, ...) {
    char buffer[256];
    va_list args;

    // Calculate timestamp since boot
    unsigned long seconds = (g_currentMillis - g_startupMillis) / 1000;
    unsigned long milliseconds = (g_currentMillis - g_startupMillis) % 1000;

    // Format header
    int headerLen = snprintf(buffer, sizeof(buffer), "[%06lu.%03lu]", seconds, milliseconds);

    // Add tag if provided
    if (tag[0] != '\0') {
        headerLen += snprintf(buffer + headerLen, sizeof(buffer) - headerLen, "[%s] ", tag);
    }

    // Add formatted message
    va_start(args, format);
    vsnprintf(buffer + headerLen, sizeof(buffer) - headerLen, format, args);
    va_end(args);

    // Print to serial and flush immediately
    Serial.println(buffer);
    Serial.flush();
}

void logPrintRaw(const char* format, ...) {
    va_list args;
    char buffer[256];

    va_start(args, format);
    vsnprintf(buffer, sizeof(buffer), format, args);
    va_end(args);

    Serial.println(buffer);
    Serial.flush();
}

/**
 * Checks if the string 'remainingCommand' starts with 'prefix'.
 * If it matches, advances 'remainingCommand' to the end of the prefix and returns true.
 * Otherwise, returns false and does not modify 'remainingCommand'.
 */
bool startsWith(const char* &remainingCommand, const char* prefix) {
    size_t len = strlen(prefix);
    if (strncmp(remainingCommand, prefix, len) == 0) {
        remainingCommand += len;
        return true;
    }
    return false;
}
