/**
 * BLE Server Implementation for Medicine Case (Adafruit Bluefruit)
 *
 * Provides:
 * - GATT server for time synchronization
 * - Command processing (SET:time:timestamp, SET:schedule:..., SET:notification:...)
 * - Response notification
 * - Sensor data notification
 * - Adafruit OTA DFU support
 */

#include "medicine_case.h"

// --- BLE Custom Service ---
BLEService bleService(BLE_SERVICE_UUID);
BLECharacteristic bleCommandCharacteristic(BLE_CHAR_COMMAND_UUID);
BLECharacteristic bleResponseCharacteristic(BLE_CHAR_RESPONSE_UUID);
BLECharacteristic bleSensorCharacteristic(BLE_CHAR_SENSOR_UUID);

// --- Adafruit OTA DFU ---
BLEDfu bledfu;

// --- Device Information ---
BLEDis bledis;

// --- Callback Handlers ---

void cccd_callback(uint16_t conn_hdl, BLECharacteristic* chr, uint16_t value) {
    (void)conn_hdl;
    (void)chr;
    logPrint("BLE", "CCCD updated: %u", value);
}

void ble_central_connect(uint16_t conn_handle) {
    (void)conn_handle;
    logPrint("BLE", "Device connected");
    g_deviceConnected = true;

    // Update LED state based on time sync status
    updateLedStateBasedOnStatus();
}

void ble_central_disconnect(uint16_t conn_handle, uint8_t reason) {
    (void)conn_handle;
    (void)reason;
    logPrint("BLE", "Device disconnected");
    g_deviceConnected = false;

    // Update LED state based on time sync status
    updateLedStateBasedOnStatus();
}

void onCommandWritten(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
    (void)conn_hdl;
    (void)chr;

    if (len > 0 && len < 513) {
        char command[513];
        memcpy(command, data, len);
        command[len] = '\0';

        logPrint("BLE", "Received command: %s", command);

        // Parse command
        if (strncmp(command, "SET:time:", 9) == 0) {
            logPrint("BLE", "Calling handleTimeSync...");
            handleTimeSync(command);
        } else if (strncmp(command, "SET:schedule:", 13) == 0) {
            logPrint("BLE", "Calling handleScheduleConfig...");
            handleScheduleConfig(command);
        } else if (strncmp(command, "SET:notification:", 17) == 0) {
            logPrint("BLE", "Calling handleNotificationConfig...");
            handleNotificationConfig(command);
        } else if (strncmp(command, "GET:status", 10) == 0) {
            logPrint("BLE", "Calling handleGetStatus...");
            handleGetStatus();
        } else if (strncmp(command, "GET:version", 11) == 0) {
            logPrint("BLE", "Calling handleGetVersion...");
            handleGetVersion();
        } else {
            logPrint("BLE", "Unknown command: %s", command);
            sendResponse("ERROR: Unknown command");
        }
    }
}

// --- BLE Setup ---
void setupBLE() {
    logPrint("BLE", "========================================");
    logPrint("BLE", "BLE Initialization");
    logPrint("BLE", "Firmware Version: %d.%d.%d (%s)",
             FIRMWARE_VERSION_MAJOR, FIRMWARE_VERSION_MINOR, FIRMWARE_VERSION_PATCH, __DATE__);
    logPrint("BLE", "BLE Service UUID: " BLE_SERVICE_UUID);
    logPrint("BLE", "Command UUID: " BLE_CHAR_COMMAND_UUID);
    logPrint("BLE", "Response UUID: " BLE_CHAR_RESPONSE_UUID);
    logPrint("BLE", "Sensor UUID: " BLE_CHAR_SENSOR_UUID);
    logPrint("BLE", "========================================");

    // Initialize Bluefruit with max connections
    Bluefruit.begin(1, 0);

    // Set device name
    Bluefruit.setName(BLE_DEVICE_NAME);

    // Set the connection interval
    Bluefruit.Periph.setConnInterval(12, 24);

    // Set up callbacks
    Bluefruit.Periph.setConnectCallback(ble_central_connect);
    Bluefruit.Periph.setDisconnectCallback(ble_central_disconnect);

    // --- Initialize Adafruit OTA DFU Service ---
    logPrint("BLE", "Initializing Adafruit OTA DFU Service...");
    bledfu.begin();

    // --- Initialize Device Information Service ---
    logPrint("BLE", "Initializing Device Information Service...");
    bledis.setManufacturer("pirorin215");
    bledis.setModel("Medicine Case");
    bledis.begin();

    // --- Initialize Custom Service ---
    logPrint("BLE", "Initializing Custom Service...");

    bleService.setPermission(SECMODE_OPEN, SECMODE_OPEN);

    // Command characteristic (READ | WRITE | NOTIFY)
    bleCommandCharacteristic.setProperties(CHR_PROPS_READ | CHR_PROPS_WRITE | CHR_PROPS_NOTIFY);
    bleCommandCharacteristic.setPermission(SECMODE_OPEN, SECMODE_OPEN);
    bleCommandCharacteristic.setFixedLen(512);
    bleCommandCharacteristic.setWriteCallback(onCommandWritten);
    bleCommandCharacteristic.setCccdWriteCallback(cccd_callback);

    // Sensor notification characteristic
    bleSensorCharacteristic.setProperties(CHR_PROPS_NOTIFY);
    bleSensorCharacteristic.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
    bleSensorCharacteristic.setFixedLen(256);

    bleService.begin();
    bleCommandCharacteristic.begin();
    bleSensorCharacteristic.begin();

    Serial.flush();
    delay(100);

    // --- Set up advertising ---
    Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
    Bluefruit.Advertising.addService(bleService);
    Bluefruit.ScanResponse.addName();
    Bluefruit.ScanResponse.addTxPower();

    // Start advertising
    Bluefruit.Advertising.restartOnDisconnect(true);
    Bluefruit.Advertising.setInterval(32, 244);
    Bluefruit.Advertising.setFastTimeout(30);
    Bluefruit.Advertising.start(0);

    logPrint("BLE", "========================================");
    logPrint("BLE", "✅ BLE Initialization Complete!");
    logPrint("BLE", "Device Name: %s", BLE_DEVICE_NAME);
    logPrint("BLE", "Custom Service: ENABLED");
    logPrint("BLE", "Adafruit OTA DFU: ENABLED");
    logPrint("BLE", "Advertising started successfully!");
    logPrint("BLE", "Waiting for connections...");
    logPrint("BLE", "========================================");
}

// --- Time Sync Handler ---
void handleTimeSync(const char* command) {
    logPrint("BLE", "Processing time sync command: %s", command);

    // Parse timestamp: SET:time:<timestamp>
    const char* timestampStr = command + strlen("SET:time:");
    uint32_t timestamp = (uint32_t)atol(timestampStr);

    if (timestamp > 0) {
        g_currentTimestamp = timestamp;
        g_timeSynced = true;

        // Update LED state based on connection status
        updateLedStateBasedOnStatus();

        int hours = getHours();
        int minutes = getMinutes();
        int seconds = getSeconds();

        logPrint("BLE", "Time synced successfully: %02d:%02d:%02d",
                 hours, minutes, seconds);

        // Send success response
        sendResponse("OK: Time synced");
    } else {
        logPrint("BLE", "Invalid timestamp: %s", timestampStr);
        sendResponse("ERROR: Invalid timestamp format");
        setLedError();
    }
}

// --- Response Helper ---
void sendResponse(const char* message) {
    bleCommandCharacteristic.notify((uint8_t*)message, strlen(message));
    logPrint("BLE", "Response sent: %s", message);
}

// --- Sensor Notification Helper ---
void sendSensorNotification(const char* data) {
    if (g_deviceConnected && g_bleNotificationEnabled) {
        bleSensorCharacteristic.notify((uint8_t*)data, strlen(data));
        logPrint("SENSOR", "Sensor notification sent: %s", data);
    }
}
