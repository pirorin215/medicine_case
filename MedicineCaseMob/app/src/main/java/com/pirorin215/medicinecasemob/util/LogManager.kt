package com.pirorin215.medicinecasemob.util

import android.util.Log
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Environment
import com.pirorin215.medicinecasemob.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class LogLevel {
    DEBUG,   // Detailed logs for debugging
    INFO,    // Important state changes
    ERROR    // Errors only
}

@Singleton
class LogManager @Inject constructor() {
    companion object {
        private var instance: LogManager? = null

        fun getInstance(): LogManager {
            return instance ?: synchronized(this) {
                instance ?: LogManager().also { instance = it }
            }
        }
    }

    init {
        instance = this
    }

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Use DEBUG for debug builds, INFO for production
    private val currentLogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO

    fun addLog(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        // Only log if the level is at or above the current log level
        if (shouldLog(level)) {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val formattedMessage = "[$timestamp] [$tag] $message"
            
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, message)
                LogLevel.INFO -> Log.i(tag, message)
                LogLevel.ERROR -> Log.e(tag, message)
            }
            
            _logs.value = (_logs.value + formattedMessage).takeLast(100)
        }
    }

    fun d(tag: String, message: String) {
        addLog(tag, message, LogLevel.DEBUG)
    }

    fun i(tag: String, message: String) {
        addLog(tag, message, LogLevel.INFO)
    }

    fun e(tag: String, message: String) {
        addLog(tag, message, LogLevel.ERROR)
    }

    fun addDebugLog(message: String) {
        d("AppLog", message)
    }

    fun addInfoLog(message: String) {
        i("AppLog", message)
    }

    fun addErrorLog(message: String) {
        e("AppLog", message)
    }

    fun saveLogsToFile(context: Context): String? {
        i("LogManager", "Saving app logs to file...")
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "medicine_case_log_$timestamp.txt"
            
            // Get Public Documents directory
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val logDir = File(documentsDir, "MedicineCaseMob/logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val logFile = File(logDir, fileName)
            
            // Prepare log content with system status
            val logContent = StringBuilder()
            logContent.append("=== MedicineCaseMob App Log Snapshot ===\n")
            logContent.append("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            logContent.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})\n")
            
            // Add System BLE Status
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val connectedGattDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            logContent.append("System BLE GATT Connections: ${connectedGattDevices.size}\n")
            connectedGattDevices.forEach { device ->
                logContent.append("  - Device: ${device.name} (${device.address})\n")
            }
            
            logContent.append("\n=== Log Messages ===\n")
            _logs.value.forEach { logContent.append(it).append("\n") }
            
            logFile.writeText(logContent.toString())
            return logFile.absolutePath
        } catch (e: Exception) {
            e("LogManager", "Failed to save logs: ${e.message}")
            return null
        }
    }

    private fun shouldLog(level: LogLevel): Boolean {
        return when (currentLogLevel) {
            LogLevel.DEBUG -> true // Log everything in debug mode
            LogLevel.INFO -> level != LogLevel.DEBUG // Skip debug logs
            LogLevel.ERROR -> level == LogLevel.ERROR // Only errors
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
