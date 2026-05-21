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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class IntakeEventHistoryItem(
    val receivedAt: Long,           // スマホで受信した日時 (Unix timestamp in ms)
    val mcuTimestamp: Long,         // マイコン側のタイムスタンプ (Unix timestamp in seconds)
    val rawEvent: String,           // 生データ "INTAKE:<timestamp>"
    val scheduleType: ScheduleType?, // nullなら範囲外
    val wasRecorded: Boolean         // 記録されたかどうか
)

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

    // Scan results
    val scanResults = bleManager.scanResults

    // Today's record
    private val _todayRecord = MutableStateFlow<MedicineIntakeRecord?>(null)
    val todayRecord: StateFlow<MedicineIntakeRecord?> = _todayRecord.asStateFlow()

    // Latest intake info
    private val _latestIntakeInfo = MutableStateFlow<String>("未受信")
    val latestIntakeInfo: StateFlow<String> = _latestIntakeInfo.asStateFlow()

    // Intake event history (with schedule determination)
    private val _intakeHistory = MutableStateFlow<List<IntakeEventHistoryItem>>(emptyList())
    val intakeHistory: StateFlow<List<IntakeEventHistoryItem>> = _intakeHistory.asStateFlow()

    // Schedules for determining intake periods
    private val _schedules = MutableStateFlow<List<MedicineSchedule>>(emptyList())
    val schedules: StateFlow<List<MedicineSchedule>> = _schedules.asStateFlow()

    init {
        loadTodayRecord()
        loadSchedules()
        observeIntakeTimestamp()
        observeIntakeHistory()
    }

    private fun loadTodayRecord() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis / 1000

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
            bleManager.intakeEventHistory.collect { rawHistory ->
                val schedules = _schedules.value
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
                _intakeHistory.value = enrichedHistory
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

    fun refreshIntakeFromFirmware() {
        Log.d(TAG, "Manually refreshing intake from firmware")
        bleManager.getIntake()
    }

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "0 (未設定)"
        val date = Date(timestamp * 1000)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)
        return "${format.format(date)} ($timestamp)"
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

        for (schedule in schedules) {
            if (!schedule.enabled) continue

            val startMinutes = schedule.startHour * 60 + schedule.startMinute
            val endMinutes = schedule.endHour * 60 + schedule.endMinute

            // Check if current time is within schedule range
            if (currentMinutes in startMinutes..endMinutes) {
                return when (schedule.id) {
                    0 -> ScheduleType.MORNING
                    1 -> ScheduleType.AFTERNOON
                    2 -> ScheduleType.EVENING
                    else -> null
                }
            }
        }

        return null  // No matching schedule
    }

    fun formatReceivedTime(timestamp: Long): String {
        val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.JAPAN)
        return format.format(Date(timestamp))
    }
}
