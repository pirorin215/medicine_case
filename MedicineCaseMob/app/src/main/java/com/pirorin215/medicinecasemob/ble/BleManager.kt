package com.pirorin215.medicinecasemob.ble

import android.util.Log
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.pirorin215.medicinecasemob.util.LogManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class IntakeEventItem(
    val receivedAt: Long,        // スマホで受信した日時 (Unix timestamp in ms)
    val mcuTimestamp: Long,      // マイコン側のタイムスタンプ (Unix timestamp in seconds)
    val rawEvent: String         // 生データ "INTAKE:<timestamp>"
)

@Singleton
class BleManager @Inject constructor(
    private val logManager: LogManager,
    private val context: Context
) {
    companion object {
        private const val TAG = "BleManager"

        // Medicine Case BLE UUIDs
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914d")
        val CHAR_COMMAND_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a0")
        val CHAR_RESPONSE_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a2")
        val CHAR_SENSOR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a3")

        const val DEVICE_NAME_PREFIX = "MedicineCase"
        private const val SCAN_PERIOD: Long = 10000 // 10 seconds

        val CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _serviceReady = MutableStateFlow(false)
    val serviceReady: StateFlow<Boolean> = _serviceReady.asStateFlow()

    // Intake event from firmware (INTAKE:<timestamp> or NONE)
    private val _intakeEvent = MutableStateFlow<String?>(null)
    val intakeEvent: StateFlow<String?> = _intakeEvent.asStateFlow()

    // Latest intake timestamp for debug
    private val _lastIntakeTimestamp = MutableStateFlow<Long?>(null)
    val lastIntakeTimestamp: StateFlow<Long?> = _lastIntakeTimestamp.asStateFlow()

    // Latest firmware response (for debug)
    private val _lastFirmwareResponse = MutableStateFlow<String>("待機中...")
    val lastFirmwareResponse: StateFlow<String> = _lastFirmwareResponse.asStateFlow()

    // Firmware version
    private val _firmwareVersion = MutableStateFlow<String?>(null)
    val firmwareVersion: StateFlow<String?> = _firmwareVersion.asStateFlow()

    // Intake event history (max 100 items)
    private val _intakeEventHistory = MutableStateFlow<List<IntakeEventItem>>(emptyList())
    val intakeEventHistory: StateFlow<List<IntakeEventItem>> = _intakeEventHistory.asStateFlow()

    private val MAX_HISTORY_SIZE = 100

    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false

    // Write queue: Android BLE allows only one pending write at a time.
    // Commands are queued and processed sequentially via onCharacteristicWrite callback.
    // Descriptor write queue: must serialize like command writes
    private val descriptorQueue = mutableListOf<BluetoothGattDescriptor>()
    private var isWritingDescriptor = false

    private val writeQueue = mutableListOf<String>()
    private var isWriting = false

    // Channel for synchronous intake query responses
    // Used by queryIntake() to wait for GET:intake response
    private val _intakeQueryChannel = Channel<String>(Channel.BUFFERED)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name?.startsWith(DEVICE_NAME_PREFIX) == true) {
                logManager.d(TAG, "Found device: ${device.name} (${device.address})")
                addScanResult(result)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                val device = result.device
                if (device.name?.startsWith(DEVICE_NAME_PREFIX) == true) {
                    logManager.d(TAG, "Found device in batch: ${device.name} (${device.address})")
                    addScanResult(result)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            logManager.e(TAG, "Scan failed: $errorCode")
            stopScan()
        }
    }

    /**
     * スキャン結果を_scanResultsに追加する共通メソッド。
     * onScanResult/onBatchScanResultsの両方から呼ばれる。
     */
    private fun addScanResult(result: ScanResult) {
        val updatedResults = _scanResults.value.toMutableList()
        updatedResults.add(result)
        _scanResults.value = updatedResults
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    logManager.d(TAG, "Connected to ${device.name} (${device.address})")
                    _connectionState.value = ConnectionState.Connected(device)
                    _serviceReady.value = false  // Reset service ready for new connection

                    // Add delay before service discovery (from bikeclock)
                    Handler(Looper.getMainLooper()).postDelayed({
                        logManager.d(TAG, "Starting service discovery...")
                        val initiated = gatt.discoverServices()
                        if (!initiated) {
                            logManager.e(TAG, "Failed to initiate service discovery")
                            disconnect()
                        }
                    }, 1000L) // 1 second delay before service discovery
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    logManager.d(TAG, "Disconnected from ${device.name} (${device.address})")
                    _connectionState.value = ConnectionState.Disconnected
                    _serviceReady.value = false
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logManager.d(TAG, "Services discovered")
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    logManager.d(TAG, "Medicine Case service found")

                    // Add a small delay before marking service as ready
                    Handler(Looper.getMainLooper()).postDelayed({
                        _serviceReady.value = true
                        logManager.d(TAG, "Service is now ready for commands")
                    }, 500L)

                    // Enable notifications on response and sensor characteristics
                    enableNotifications(gatt)
                } else {
                    logManager.e(TAG, "Medicine Case service not found")
                }
            } else {
                logManager.e(TAG, "Service discovery failed: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val data = String(value)
            logManager.d(TAG, "Characteristic changed: ${characteristic.uuid} -> $data")

            when (characteristic.uuid) {
                CHAR_RESPONSE_UUID -> {
                    logManager.d(TAG, "Response: $data")
                    // Update latest firmware response for debug
                    _lastFirmwareResponse.value = data

                    // Handle Version response
                    if (data.startsWith("OK:version:")) {
                        val version = data.removePrefix("OK:version:")
                        _firmwareVersion.value = version
                        logManager.i(TAG, "Firmware version: $version")
                    }

                    // Add all responses to history for debug
                    val timestamp = if (data.startsWith("INTAKE:")) {
                        val timestampStr = data.removePrefix("INTAKE:")
                        timestampStr.toLongOrNull() ?: 0L
                    } else {
                        0L
                    }
                    addToIntakeHistory(data, timestamp)

                    // Update intake event for MedicineBleScanService
                    if (data.startsWith("INTAKE:") || data == "NONE") {
                        _intakeEvent.value = data
                        _intakeQueryChannel.trySend(data)
                        if (data.startsWith("INTAKE:")) {
                            _lastIntakeTimestamp.value = timestamp
                        }
                    }
                }
                CHAR_SENSOR_UUID -> {
                    logManager.d(TAG, "Sensor data: $data")
                    // Handle INTAKE notification from sensor characteristic
                    if (data.startsWith("INTAKE:")) {
                        logManager.d(TAG, "Intake event received: $data")
                        _intakeEvent.value = data
                        _intakeQueryChannel.trySend(data)
                        // Update last intake timestamp for debug
                        val timestampStr = data.removePrefix("INTAKE:")
                        val timestamp = timestampStr.toLongOrNull() ?: 0L
                        _lastIntakeTimestamp.value = timestamp
                        addToIntakeHistory(data, timestamp)
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logManager.i(TAG, "✅ Descriptor write SUCCESS: ${descriptor.characteristic.uuid}")
            } else {
                logManager.e(TAG, "❌ Descriptor write FAILED: ${descriptor.characteristic.uuid}, status: $status")
            }

            // Process next descriptor in queue
            synchronized(descriptorQueue) {
                isWritingDescriptor = false
            }
            logManager.d(TAG, "Processing next descriptor, remaining: ${descriptorQueue.size}")
            processDescriptorQueue()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logManager.d(TAG, "Write success: ${characteristic.uuid}")
            } else {
                logManager.e(TAG, "Write failed: ${characteristic.uuid}, status: $status")
            }

            // Process next command in queue
            synchronized(writeQueue) {
                if (writeQueue.isNotEmpty()) {
                    writeQueue.removeAt(0)
                }
                isWriting = false
                processWriteQueue()
            }
        }
    }

    fun isBluetoothAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun startScan(targetAddress: String? = null) {
        if (!isBluetoothAvailable()) {
            logManager.e(TAG, "Bluetooth LE not available")
            return
        }

        if (!isBluetoothEnabled()) {
            logManager.e(TAG, "Bluetooth not enabled")
            return
        }

        if (isScanning) {
            Log.w(TAG, "Already scanning")
            return
        }

        // 1. Try to connect to target address if provided
        if (targetAddress != null) {
            try {
                val device = bluetoothAdapter?.getRemoteDevice(targetAddress)
                if (device != null) {
                    logManager.d(TAG, "Found target device address: $targetAddress")
                    connectToDevice(device)
                    return
                }
            } catch (e: Exception) {
                logManager.e(TAG, "Error getting remote device for $targetAddress: ${e.message}")
            }
        }

        // 2. Try to find bonded devices
        val bondedDevices = bluetoothAdapter?.bondedDevices
        val bondedMedicineCase = bondedDevices?.find { it.name?.startsWith(DEVICE_NAME_PREFIX) == true }

        if (bondedMedicineCase != null) {
            logManager.d(TAG, "Found bonded device: ${bondedMedicineCase.name} (${bondedMedicineCase.address})")
            // Connect directly to bonded device
            connectToDevice(bondedMedicineCase)
            return
        }

        logManager.d(TAG, "No specific device to connect, starting scan...")
        isScanning = true
        _scanResults.value = emptyList()

        // Scan filter for our service UUID
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        )
        
        // Use Balanced or Low Power mode for background friendly scanning
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .setReportDelay(1000L)
            .build()

        bluetoothLeScanner?.startScan(filters, settings, scanCallback)

        // Stop scan after SCAN_PERIOD
        Handler(Looper.getMainLooper()).postDelayed({
            stopScan()
        }, SCAN_PERIOD)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) {
            return
        }

        logManager.d(TAG, "Stopping BLE scan...")
        isScanning = false
        bluetoothLeScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        logManager.d(TAG, "Connecting to ${device.name} (${device.address})...")
        _connectionState.value = ConnectionState.Connecting

        // Disconnect from current device if connected
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        logManager.d(TAG, "Disconnecting...")
        synchronized(writeQueue) {
            writeQueue.clear()
            isWriting = false
        }
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID) ?: run {
            logManager.e(TAG, "enableNotifications: Service not found")
            return
        }

        // Queue descriptors for both characteristics (must write one at a time)
        val responseChar = service.getCharacteristic(CHAR_RESPONSE_UUID)
        if (responseChar != null) {
            logManager.d(TAG, "Queueing notification enable for response characteristic")
            gatt.setCharacteristicNotification(responseChar, true)
            val descriptor = responseChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                synchronized(descriptorQueue) {
                    descriptorQueue.add(descriptor)
                }
            } else {
                logManager.e(TAG, "Response characteristic descriptor not found")
            }
        } else {
            logManager.e(TAG, "Response characteristic not found")
        }

        val sensorChar = service.getCharacteristic(CHAR_SENSOR_UUID)
        if (sensorChar != null) {
            logManager.d(TAG, "Queueing notification enable for sensor characteristic")
            gatt.setCharacteristicNotification(sensorChar, true)
            val descriptor = sensorChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                synchronized(descriptorQueue) {
                    descriptorQueue.add(descriptor)
                }
            } else {
                logManager.e(TAG, "Sensor characteristic descriptor not found")
            }
        } else {
            logManager.e(TAG, "Sensor characteristic not found")
        }

        // Start processing the queue
        logManager.d(TAG, "Starting descriptor queue processing, queue size: ${descriptorQueue.size}")
        processDescriptorQueue()
    }

    @SuppressLint("MissingPermission")
    private fun processDescriptorQueue() {
        val gatt = bluetoothGatt
        if (gatt == null) {
            logManager.e(TAG, "processDescriptorQueue: bluetoothGatt is null")
            return
        }

        synchronized(descriptorQueue) {
            if (isWritingDescriptor) {
                logManager.d(TAG, "processDescriptorQueue: Already writing descriptor, waiting...")
                return
            }
            if (descriptorQueue.isEmpty()) {
                logManager.d(TAG, "processDescriptorQueue: Descriptor queue is empty")
                return
            }

            isWritingDescriptor = true
            val descriptor = descriptorQueue.removeAt(0)
            val result = gatt.writeDescriptor(descriptor)
            if (result) {
                logManager.d(TAG, "Descriptor write initiated for ${descriptor.characteristic.uuid}")
            } else {
                logManager.e(TAG, "Descriptor write FAILED for ${descriptor.characteristic.uuid}")
                isWritingDescriptor = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String): Boolean {
        logManager.d(TAG, "sendCommand() called: $command")

        val gatt = bluetoothGatt ?: run {
            logManager.e(TAG, "sendCommand failed: bluetoothGatt is null")
            return false
        }

        // Validate service and characteristic exist
        val service = gatt.getService(SERVICE_UUID) ?: run {
            logManager.e(TAG, "sendCommand failed: Service not found")
            return false
        }

        service.getCharacteristic(CHAR_COMMAND_UUID) ?: run {
            logManager.e(TAG, "sendCommand failed: Command characteristic not found")
            return false
        }

        synchronized(writeQueue) {
            writeQueue.add(command)
            logManager.d(TAG, "Command queued (size: ${writeQueue.size}): $command")

            if (!isWriting) {
                processWriteQueue()
            }
        }
        return true
    }

    /**
     * Process the next command in the write queue.
     * Must be called while holding the writeQueue lock.
     */
    @SuppressLint("MissingPermission")
    private fun processWriteQueue() {
        if (isWriting || writeQueue.isEmpty()) return

        val gatt = bluetoothGatt ?: run {
            Log.w(TAG, "processWriteQueue: gatt is null, clearing queue")
            writeQueue.clear()
            return
        }

        val service = gatt.getService(SERVICE_UUID) ?: run {
            Log.w(TAG, "processWriteQueue: service not found, clearing queue")
            writeQueue.clear()
            return
        }

        val commandChar = service.getCharacteristic(CHAR_COMMAND_UUID) ?: run {
            Log.w(TAG, "processWriteQueue: characteristic not found, clearing queue")
            writeQueue.clear()
            return
        }

        val command = writeQueue.first()
        commandChar.value = command.toByteArray()
        commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        val result = gatt.writeCharacteristic(commandChar)
        if (result) {
            isWriting = true
            logManager.d(TAG, "Write initiated: $command")
        } else {
            logManager.e(TAG, "writeCharacteristic failed, removing from queue: $command")
            writeQueue.removeAt(0)
            // Retry next command
            processWriteQueue()
        }
    }

    fun syncTime() {
        val timestamp = System.currentTimeMillis() / 1000
        val command = "SET:time:$timestamp"
        sendCommand(command)
    }

    fun setDetectionAngle(angle: Float): Boolean {
        val command = "SET:detection:angle:$angle"
        logManager.d(TAG, "setDetectionAngle: $command")
        return sendCommand(command)
    }

    fun setDetectionCooldown(cooldownMs: Long): Boolean {
        val command = "SET:detection:cooldown:$cooldownMs"
        logManager.d(TAG, "setDetectionCooldown: $command")
        return sendCommand(command)
    }

    fun getIntake(): Boolean {
        logManager.d(TAG, "getIntake: requesting intake timestamp")
        return sendCommand("GET:intake")
    }

    /**
     * Send GET:intake command and synchronously wait for the BLE response.
     * Used for periodic polling to keep DB in sync with the MCU.
     *
     * @return "INTAKE:<timestamp>", "NONE", or null on timeout/failure
     */
    suspend fun queryIntake(timeoutMs: Long = 3000L): String? {
        // Clear stale data from channel
        while (_intakeQueryChannel.tryReceive().isSuccess) { }

        val sent = sendCommand("GET:intake")
        if (!sent) {
            logManager.e(TAG, "queryIntake: failed to send command")
            return null
        }

        return try {
            withTimeout(timeoutMs) {
                _intakeQueryChannel.receive()
            }
        } catch (e: TimeoutCancellationException) {
            logManager.e(TAG, "queryIntake timed out after ${timeoutMs}ms")
            null
        }
    }

    /**
     * Clear the internal intake event and request intake from BLE device.
     * This is used after clearing intake records to re-fetch from the device.
     */
    fun clearAndGetIntake() {
        logManager.d(TAG, "clearAndGetIntake: clearing internal event and requesting from BLE")
        _intakeEvent.value = null  // Clear internal event
        _lastIntakeTimestamp.value = null  // Clear last timestamp
        getIntake()  // Request from BLE device
    }

    /**
     * Consume (read and reset) the current intake event.
     * Returns the event string (e.g. "INTAKE:12345") or null.
     */
    fun consumeIntakeEvent(): String? {
        val event = _intakeEvent.value
        _intakeEvent.value = null
        return event
    }

    fun getVersion() {
        sendCommand("GET:version")
    }

    /**
     * Clear the last intake timestamp (for debug purposes).
     * Called when intake event is consumed and processed.
     */
    fun clearLastIntakeTimestamp() {
        _lastIntakeTimestamp.value = null
    }

    /**
     * Add an intake event to history.
     * @param rawEvent Raw event string (e.g. "INTAKE:1234567890")
     * @param mcuTimestamp Parsed timestamp from the event
     */
    private fun addToIntakeHistory(rawEvent: String, mcuTimestamp: Long) {
        val now = System.currentTimeMillis()
        val event = IntakeEventItem(
            receivedAt = now,
            mcuTimestamp = mcuTimestamp,
            rawEvent = rawEvent
        )

        val currentHistory = _intakeEventHistory.value.toMutableList()
        currentHistory.add(0, event) // Add to beginning (newest first)

        // Keep only the most recent MAX_HISTORY_SIZE items
        if (currentHistory.size > MAX_HISTORY_SIZE) {
            currentHistory.removeAt(currentHistory.size - 1)
        }

        _intakeEventHistory.value = currentHistory
        logManager.d(TAG, "Added intake event to history: total=${currentHistory.size}")
    }

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Scanning : ConnectionState()
        data object Connecting : ConnectionState()
        data class Connected(val device: BluetoothDevice) : ConnectionState()
    }
}
