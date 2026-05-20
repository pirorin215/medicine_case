package com.pirorin215.medicinecasemob.ble

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
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
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

    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false

    // Write queue: Android BLE allows only one pending write at a time.
    // Commands are queued and processed sequentially via onCharacteristicWrite callback.
    private val writeQueue = mutableListOf<String>()
    private var isWriting = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name?.startsWith(DEVICE_NAME_PREFIX) == true) {
                Log.d(TAG, "Found device: ${device.name} (${device.address})")
                val updatedResults = _scanResults.value.toMutableList()
                updatedResults.add(result)
                _scanResults.value = updatedResults
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                val device = result.device
                if (device.name?.startsWith(DEVICE_NAME_PREFIX) == true) {
                    Log.d(TAG, "Found device in batch: ${device.name} (${device.address})")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            stopScan()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to ${device.name} (${device.address})")
                    _connectionState.value = ConnectionState.Connected(device)

                    // Add delay before service discovery (from bikeclock)
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "Starting service discovery...")
                        val initiated = gatt.discoverServices()
                        if (!initiated) {
                            Log.e(TAG, "Failed to initiate service discovery")
                            disconnect()
                        }
                    }, 1000L) // 1 second delay before service discovery
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from ${device.name} (${device.address})")
                    _connectionState.value = ConnectionState.Disconnected
                    _serviceReady.value = false
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    Log.d(TAG, "Medicine Case service found")

                    // Add a small delay before marking service as ready
                    Handler(Looper.getMainLooper()).postDelayed({
                        _serviceReady.value = true
                        Log.d(TAG, "Service is now ready for commands")
                    }, 500L)

                    // Enable notifications on response and sensor characteristics
                    enableNotifications(gatt)
                } else {
                    Log.e(TAG, "Medicine Case service not found")
                }
            } else {
                Log.e(TAG, "Service discovery failed: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val data = String(value)
            Log.d(TAG, "Characteristic changed: ${characteristic.uuid} -> $data")

            when (characteristic.uuid) {
                CHAR_RESPONSE_UUID -> {
                    Log.d(TAG, "Response: $data")
                    // Handle GET:intake response
                    if (data.startsWith("INTAKE:")) {
                        Log.d(TAG, "Intake response received: $data")
                        _intakeEvent.value = data
                    }
                }
                CHAR_SENSOR_UUID -> {
                    Log.d(TAG, "Sensor data: $data")
                    // Handle INTAKE notification from sensor characteristic
                    if (data.startsWith("INTAKE:")) {
                        Log.d(TAG, "Intake event received: $data")
                        _intakeEvent.value = data
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Write success: ${characteristic.uuid}")
            } else {
                Log.e(TAG, "Write failed: ${characteristic.uuid}, status: $status")
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
    fun startScan() {
        if (!isBluetoothAvailable()) {
            Log.e(TAG, "Bluetooth LE not available")
            return
        }

        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }

        if (isScanning) {
            Log.w(TAG, "Already scanning")
            return
        }

        Log.d(TAG, "Starting BLE scan...")

        // First, try to find bonded devices
        val bondedDevices = bluetoothAdapter?.bondedDevices
        val bondedMedicineCase = bondedDevices?.find { it.name?.startsWith(DEVICE_NAME_PREFIX) == true }

        if (bondedMedicineCase != null) {
            Log.d(TAG, "Found bonded device: ${bondedMedicineCase.name} (${bondedMedicineCase.address})")
            // Connect directly to bonded device
            connectToDevice(bondedMedicineCase)
            return
        }

        Log.d(TAG, "No bonded device found, starting scan...")
        isScanning = true
        _scanResults.value = emptyList()

        bluetoothLeScanner?.startScan(scanCallback)

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

        Log.d(TAG, "Stopping BLE scan...")
        isScanning = false
        bluetoothLeScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to ${device.name} (${device.address})...")
        _connectionState.value = ConnectionState.Connecting

        // Disconnect from current device if connected
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
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
        val service = gatt.getService(SERVICE_UUID) ?: return

        // Enable notifications for response characteristic
        val responseChar = service.getCharacteristic(CHAR_RESPONSE_UUID)
        if (responseChar != null) {
            Log.d(TAG, "Enabling notifications for response characteristic")
            gatt.setCharacteristicNotification(responseChar, true)
            val descriptor = responseChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        // Enable notifications for sensor characteristic
        val sensorChar = service.getCharacteristic(CHAR_SENSOR_UUID)
        if (sensorChar != null) {
            Log.d(TAG, "Enabling notifications for sensor characteristic")
            gatt.setCharacteristicNotification(sensorChar, true)
            val descriptor = sensorChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String): Boolean {
        Log.d(TAG, "sendCommand() called: $command")

        val gatt = bluetoothGatt ?: run {
            Log.e(TAG, "sendCommand failed: bluetoothGatt is null")
            return false
        }

        // Validate service and characteristic exist
        val service = gatt.getService(SERVICE_UUID) ?: run {
            Log.e(TAG, "sendCommand failed: Service not found")
            return false
        }

        service.getCharacteristic(CHAR_COMMAND_UUID) ?: run {
            Log.e(TAG, "sendCommand failed: Command characteristic not found")
            return false
        }

        synchronized(writeQueue) {
            writeQueue.add(command)
            Log.d(TAG, "Command queued (size: ${writeQueue.size}): $command")

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
            Log.d(TAG, "Write initiated: $command")
        } else {
            Log.e(TAG, "writeCharacteristic failed, removing from queue: $command")
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
        Log.d(TAG, "setDetectionAngle: $command")
        return sendCommand(command)
    }

    fun setDetectionCooldown(cooldownMs: Long): Boolean {
        val command = "SET:detection:cooldown:$cooldownMs"
        Log.d(TAG, "setDetectionCooldown: $command")
        return sendCommand(command)
    }

    fun getIntake(): Boolean {
        Log.d(TAG, "getIntake: requesting intake timestamp")
        return sendCommand("GET:intake")
    }

    fun clearIntake(): Boolean {
        Log.d(TAG, "clearIntake: clearing intake timestamp")
        return sendCommand("CLR:intake")
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

    fun getStatus() {
        sendCommand("GET:status")
    }

    fun getVersion() {
        sendCommand("GET:version")
    }

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Scanning : ConnectionState()
        data object Connecting : ConnectionState()
        data class Connected(val device: BluetoothDevice) : ConnectionState()
    }
}
