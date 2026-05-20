/**
 * BLE Server Implementation for Medicine Case (Adafruit Bluefruit)
 *
 * Commands:
 * - SET:time:<timestamp>         - Time synchronization
 * - SET:detection:angle:<value>  - Set detection angle threshold
 * - SET:detection:cooldown:<ms>  - Set cooldown time
 * - GET:status                   - Get device status
 * - GET:version                  - Get firmware version
 * - GET:intake                   - Get last intake timestamp
 * - CLR:intake                   - Clear intake timestamp
 */

#include "medicine_case.h"

// --- BLE Custom Service ---
BLEService bleService(BLE_SERVICE_UUID);
BLECharacteristic bleCommandCharacteristic(BLE_CHAR_COMMAND_UUID);
BLECharacteristic bleResponseCharacteristic(BLE_CHAR_RESPONSE_UUID);
BLECharacteristic bleSensorCharacteristic(BLE_CHAR_SENSOR_UUID);

// Application buffers for BLE characteristics
// Using setBuffer() stores values in application RAM (BLE_GATTS_VLOC_USER)
// instead of the softdevice's attribute table (BLE_GATTS_VLOC_STACK),
// preventing attribute table overflow that causes characteristics to fail silently.
static uint8_t bleCommandBuffer[512];
static uint8_t bleResponseBuffer[512];
static uint8_t bleSensorBuffer[256];

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
        } else if (strncmp(command, "SET:detection:", 14) == 0) {
            logPrint("BLE", "Calling handleDetectionConfig...");
            handleDetectionConfig(command);
        } else if (strncmp(command, "GET:status", 10) == 0) {
            logPrint("BLE", "Calling handleGetStatus...");
            handleGetStatus();
        } else if (strncmp(command, "GET:version", 11) == 0) {
            logPrint("BLE", "Calling handleGetVersion...");
            handleGetVersion();
        } else if (strncmp(command, "GET:intake", 10) == 0) {
            logPrint("BLE", "Calling handleGetIntake...");
            handleGetIntake();
        } else if (strncmp(command, "CLR:intake", 10) == 0) {
            logPrint("BLE", "Calling handleClearIntake...");
            handleClearIntake();
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

    // Service permissions must be set before service.begin()
    bleService.setPermission(SECMODE_OPEN, SECMODE_OPEN);

    // Command characteristic (READ | WRITE | NOTIFY)
    // Using setBuffer() instead of setFixedLen() to avoid attribute table overflow.
    // setFixedLen(512) with VLOC_STACK allocates 512 bytes in the limited attribute table,
    // causing sd_ble_gatts_characteristic_add() to fail with NRF_ERROR_NO_MEM.
    bleCommandCharacteristic.setProperties(CHR_PROPS_READ | CHR_PROPS_WRITE | CHR_PROPS_NOTIFY);
    bleCommandCharacteristic.setPermission(SECMODE_OPEN, SECMODE_OPEN);
    bleCommandCharacteristic.setBuffer(bleCommandBuffer, sizeof(bleCommandBuffer));
    bleCommandCharacteristic.setWriteCallback(onCommandWritten);
    bleCommandCharacteristic.setCccdWriteCallback(cccd_callback);

    // Response characteristic (NOTIFY)
    bleResponseCharacteristic.setProperties(CHR_PROPS_NOTIFY);
    bleResponseCharacteristic.setPermission(SECMODE_OPEN, SECMODE_OPEN);
    bleResponseCharacteristic.setBuffer(bleResponseBuffer, sizeof(bleResponseBuffer));

    // Sensor notification characteristic
    bleSensorCharacteristic.setProperties(CHR_PROPS_NOTIFY);
    bleSensorCharacteristic.setPermission(SECMODE_OPEN, SECMODE_OPEN);
    bleSensorCharacteristic.setBuffer(bleSensorBuffer, sizeof(bleSensorBuffer));

    // CRITICAL: Service MUST be begun BEFORE characteristics
    // BLECharacteristic::begin() uses BLEService::lastService
    bleService.begin();

    // Begin characteristics with error checking
    // If sd_ble_gatts_characteristic_add() fails (e.g., NRF_ERROR_NO_MEM),
    // the characteristic won't be discoverable by clients
    err_t err;

    err = bleCommandCharacteristic.begin();
    if (err != ERROR_NONE) {
        logPrint("BLE", "ERROR: Command characteristic begin() failed: 0x%02X", err);
    } else {
        logPrint("BLE", "Command characteristic added successfully");
    }

    err = bleResponseCharacteristic.begin();
    if (err != ERROR_NONE) {
        logPrint("BLE", "ERROR: Response characteristic begin() failed: 0x%02X", err);
    } else {
        logPrint("BLE", "Response characteristic added successfully");
    }

    err = bleSensorCharacteristic.begin();
    if (err != ERROR_NONE) {
        logPrint("BLE", "ERROR: Sensor characteristic begin() failed: 0x%02X", err);
    } else {
        logPrint("BLE", "Sensor characteristic added successfully");
    }

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
    if (g_deviceConnected) {
        bleSensorCharacteristic.notify((uint8_t*)data, strlen(data));
        logPrint("SENSOR", "Sensor notification sent: %s", data);
    }
}
