package com.pirorin215.medicinecasemob.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.data.MedicineIntakeRecord
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import com.pirorin215.medicinecasemob.ui.data.MedicineSchedule
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import com.pirorin215.medicinecasemob.ble.IntakeEventItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class IntakeEventHistoryItem(
    val receivedAt: Long,           // スマホで受信した日時 (Unix timestamp in ms)
    val mcuTimestamp: Long,         // マイコン側のタイムスタンプ (Unix timestamp in seconds)
    val rawEvent: String,           // 生データ "INTAKE:<timestamp>" or "NONE"
    val scheduleType: ScheduleType?, // nullなら範囲外
    val wasRecorded: Boolean         // 記録されたかどうか
)

enum class HistoryFilter {
    ALL,           // 全て表示
    TIME,          // 時刻同期関連のみ
    DETECTION,     // 検知設定関連のみ
    INTAKE         // 服薬関連のみ
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val repository: MedicineRepository,
    private val bleManager: BleManager
) : ViewModel() {

    companion object {
        private const val TAG = "DebugViewModel"
    }

    // BLE connection state
    val bleConnectionState = bleManager.connectionState

    // Service ready
    val serviceReady = bleManager.serviceReady

    // Last intake timestamp from firmware
    val lastIntakeTimestamp = bleManager.lastIntakeTimestamp

    // Firmware version
    val firmwareVersion = bleManager.firmwareVersion

    // Scan results
    val scanResults = bleManager.scanResults

    // Today's record
    private val _todayRecord = MutableStateFlow<MedicineIntakeRecord?>(null)
    val todayRecord: StateFlow<MedicineIntakeRecord?> = _todayRecord.asStateFlow()

    // Latest firmware response (for debug)
    private val _latestFirmwareResponse = MutableStateFlow<String>("待機中...")
    val latestFirmwareResponse: StateFlow<String> = _latestFirmwareResponse.asStateFlow()

    // Latest intake info
    private val _latestIntakeInfo = MutableStateFlow<String>("未受信")
    val latestIntakeInfo: StateFlow<String> = _latestIntakeInfo.asStateFlow()

    // Intake event history (with schedule determination)
    private val _intakeHistory = MutableStateFlow<List<IntakeEventHistoryItem>>(emptyList())
    val intakeHistory: StateFlow<List<IntakeEventHistoryItem>> = _intakeHistory.asStateFlow()

    // History filter
    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    // Filtered history (for UI)
    val filteredHistory: StateFlow<List<IntakeEventHistoryItem>> = _intakeHistory.combine(_historyFilter) { history, filter ->
        when (filter) {
            HistoryFilter.ALL -> history
            HistoryFilter.TIME -> history.filter { isTimeRelated(it.rawEvent) }
            HistoryFilter.DETECTION -> history.filter { isDetectionRelated(it.rawEvent) }
            HistoryFilter.INTAKE -> history.filter { isIntakeRelated(it.rawEvent) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Schedules for determining intake periods
    private val _schedules = MutableStateFlow<List<MedicineSchedule>>(emptyList())
    val schedules: StateFlow<List<MedicineSchedule>> = _schedules.asStateFlow()

    // Predefined commands
    val predefinedCommands = listOf(
        "GET:version" to "バージョン取得",
        "GET:intake" to "INTAKE取得",
        "CLR:intake" to "INTAKEクリア",
        "SET:time" to "時刻同期"
    )

    private val _selectedPredefinedCommand = MutableStateFlow(predefinedCommands[0].first)
    val selectedPredefinedCommand: StateFlow<String> = _selectedPredefinedCommand.asStateFlow()

    // Manual command input
    private val _manualCommandInput = MutableStateFlow("")
    val manualCommandInput: StateFlow<String> = _manualCommandInput.asStateFlow()

    init {
        loadTodayRecord()
        loadSchedules()
        observeIntakeTimestamp()
        observeIntakeHistory()
        observeFirmwareResponse()
    }

    private fun loadTodayRecord() {
        viewModelScope.launch {
            val todayStart = repository.getTodayStartTimestamp()

            repository.getAllIntakeRecords().collect { records ->
                _todayRecord.value = records.find { it.date == todayStart }
            }
        }
    }

    private fun loadSchedules() {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _schedules.value = repository.getSchedulesFromSettings(settings)
            }
        }
    }

    private fun observeIntakeHistory() {
        viewModelScope.launch {
            // Combine intake history with schedules to ensure schedules are loaded
            combine(
                bleManager.intakeEventHistory,
                _schedules
            ) { rawHistory, schedules ->
                Log.d(TAG, "🔄 Processing history: ${rawHistory.size} events, ${schedules.size} schedules")
                val enrichedHistory = rawHistory.map { event ->
                    val scheduleType = determineScheduleTypeForTimestamp(event.mcuTimestamp, schedules)
                    IntakeEventHistoryItem(
                        receivedAt = event.receivedAt,
                        mcuTimestamp = event.mcuTimestamp,
                        rawEvent = event.rawEvent,
                        scheduleType = scheduleType,
                        wasRecorded = scheduleType != null
                    )
                }
                enrichedHistory
            }.collect { history ->
                _intakeHistory.value = history
            }
        }
    }

    private fun observeIntakeTimestamp() {
        viewModelScope.launch {
            bleManager.lastIntakeTimestamp.collect { timestamp ->
                if (timestamp != null) {
                    val date = Date(timestamp * 1000)
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)
                    _latestIntakeInfo.value = format.format(date)
                } else {
                    _latestIntakeInfo.value = "未受信"
                }
            }
        }
    }

    private fun observeFirmwareResponse() {
        viewModelScope.launch {
            bleManager.lastFirmwareResponse.collect { response ->
                _latestFirmwareResponse.value = response
            }
        }
    }

    fun getIntakeFromFirmware() {
        Log.d(TAG, "Getting intake from firmware")
        bleManager.getIntake()
    }

    fun syncTimeToFirmware() {
        Log.d(TAG, "Syncing time to firmware")
        bleManager.syncTime()
    }

    fun onPredefinedCommandSelected(command: String) {
        _selectedPredefinedCommand.value = command
    }

    fun onManualCommandInputChange(input: String) {
        _manualCommandInput.value = input
    }

    fun executePredefinedCommand() {
        val command = _selectedPredefinedCommand.value
        Log.d(TAG, "Executing predefined command: $command")
        if (command == "SET:time") {
            bleManager.syncTime()
        } else {
            bleManager.sendCommand(command)
        }
    }

    fun sendManualCommand() {
        val command = _manualCommandInput.value.trim()
        if (command.isNotEmpty()) {
            Log.d(TAG, "Sending manual command: $command")
            bleManager.sendCommand(command)
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "⚠️ 時刻未同期（受信時刻を使用）"
        val date = Date(timestamp * 1000)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)
        return "${format.format(date)}"
    }

    fun formatTimeOnly(timestamp: Long): String {
        if (timestamp == 0L) return "--:--:--"
        val date = Date(timestamp * 1000)
        val format = SimpleDateFormat("HH:mm:ss", Locale.JAPAN)
        return format.format(date)
    }

    fun formatDateOnly(timestamp: Long): String {
        if (timestamp == 0L) return "未設定"
        val date = Date(timestamp * 1000)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)
        return format.format(date)
    }

    fun getDeviceInfo(): String {
        val state = bleConnectionState.value
        return when (state) {
            is BleManager.ConnectionState.Connected -> {
                val device = (state as BleManager.ConnectionState.Connected).device
                "接続中\nデバイス: ${device.name}\nアドレス: ${device.address}"
            }
            BleManager.ConnectionState.Disconnected -> "未接続"
            BleManager.ConnectionState.Scanning -> "スキャン中"
            BleManager.ConnectionState.Connecting -> "接続中"
        }
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    private fun isTimeRelated(rawEvent: String): Boolean {
        return rawEvent.contains("time", ignoreCase = true) ||
               rawEvent.contains("synced", ignoreCase = true)
    }

    private fun isDetectionRelated(rawEvent: String): Boolean {
        return rawEvent.contains("detection", ignoreCase = true) ||
               rawEvent.contains("angle", ignoreCase = true) ||
               rawEvent.contains("cooldown", ignoreCase = true)
    }

    private fun isIntakeRelated(rawEvent: String): Boolean {
        return rawEvent.startsWith("INTAKE:", ignoreCase = true) ||
               rawEvent.equals("NONE", ignoreCase = true) ||
               rawEvent.contains("intake", ignoreCase = true)
    }

    /**
     * Determine schedule type based on timestamp and configured time ranges.
     * Returns null if timestamp doesn't fall within any schedule range.
     */
    private fun determineScheduleTypeForTimestamp(
        timestamp: Long,
        schedules: List<MedicineSchedule>
    ): ScheduleType? {
        // Convert Unix timestamp to hour/minute using Calendar
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp * 1000  // Convert seconds to milliseconds
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute

        Log.d(TAG, "Schedule determination: timestamp=$timestamp, hour=$hour, minute=$minute, schedules=${schedules.size}")

        for (schedule in schedules) {
            if (!schedule.enabled) continue

            val startMinutes = schedule.startMinuteOfDay
            val endMinutes = schedule.endMinuteOfDay

            // Check if current time is within schedule range
            if (currentMinutes in startMinutes..endMinutes) {
                val result = when (schedule.id) {
                    0 -> ScheduleType.MORNING
                    1 -> ScheduleType.AFTERNOON
                    2 -> ScheduleType.EVENING
                    else -> null
                }
                Log.d(TAG, "Schedule match found: $result (schedule.id=${schedule.id})")
                return result
            }
        }

        Log.d(TAG, "No matching schedule found for currentMinutes=$currentMinutes")
        return null  // No matching schedule
    }

    fun formatReceivedTime(timestamp: Long): String {
        val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.JAPAN)
        return format.format(Date(timestamp))
    }
}
