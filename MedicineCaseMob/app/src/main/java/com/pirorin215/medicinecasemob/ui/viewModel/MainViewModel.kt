package com.pirorin215.medicinecasemob.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.notification.NotificationService
import com.pirorin215.medicinecasemob.ui.data.MedicineIntakeRecord
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import com.pirorin215.medicinecasemob.ui.data.MedicineSchedule
import com.pirorin215.medicinecasemob.ui.data.MedicineSettingsRepository
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import com.pirorin215.medicinecasemob.util.LogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MedicineRepository,
    private val settingsRepository: MedicineSettingsRepository,
    private val bleManager: BleManager,
    private val notificationService: NotificationService,
    private val logManager: LogManager
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _schedules = MutableStateFlow<List<MedicineSchedule>>(emptyList())
    val schedules: StateFlow<List<MedicineSchedule>> = _schedules.asStateFlow()

    private val _intakeRecords = MutableStateFlow<List<MedicineIntakeRecord>>(emptyList())
    val intakeRecords: StateFlow<List<MedicineIntakeRecord>> = _intakeRecords.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _selectedRecordIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRecordIds: StateFlow<Set<Long>> = _selectedRecordIds.asStateFlow()

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()

    val bleConnectionState = bleManager.connectionState
    val scanResults = bleManager.scanResults
    val appLogs: StateFlow<List<String>> = logManager.logs

    fun clearLogs() {
        logManager.clearLogs()
    }

    fun saveLogs(context: android.content.Context): String? {
        return logManager.saveLogsToFile(context)
    }

    init {
        loadSchedules()
        loadIntakeRecords()
        observeBleConnectionState()
        observeIntakeEvents()
    }

    private fun loadSchedules() {
        // Load schedules from Room database
        viewModelScope.launch {
            repository.getAllSchedules().collect { schedules ->
                _schedules.value = schedules
                Log.d(TAG, "Schedules loaded from Room DB: ${schedules.size} items")
            }
        }
    }

    private fun loadIntakeRecords() {
        viewModelScope.launch {
            repository.getAllIntakeRecords().collect { records ->
                _intakeRecords.value = records
            }
        }
    }

    private fun observeBleConnectionState() {
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                _isConnected.value = state is BleManager.ConnectionState.Connected

                // On connect: sync time, check for missed intake events, and notify if needed
                if (state is BleManager.ConnectionState.Connected) {
                    bleManager.syncTime()
                    bleManager.getIntake()

                    // Check for missed intakes and notify immediately
                    val todayRecord = getTodayRecord()
                    val schedules = _schedules.value
                    notificationService.checkAndNotifyMissedIntakes(
                        schedules = schedules,
                        todayRecord = todayRecord,
                        isConnectedToBle = true,
                        forceNotification = true
                    )
                }
            }
        }
    }

    /**
     * Observe intake events from firmware (INTAKE:<timestamp>).
     * On receiving:
     * 1. Determine which schedule period (morning/afternoon/evening) the intake belongs to
     * 2. Update the intake record in local DB
     * 3. Clear the intake timestamp on the firmware
     */
    private fun observeIntakeEvents() {
        viewModelScope.launch {
            bleManager.intakeEvent.collect { event ->
                if (event == null) return@collect

                Log.d(TAG, "Processing intake event: $event")

                if (event.startsWith("INTAKE:")) {
                    val timestampStr = event.removePrefix("INTAKE:")
                    val timestamp = timestampStr.toLongOrNull()

                    if (timestamp != null && timestamp > 0) {
                        recordIntakeLocally(timestamp)

                        // Clear intake timestamp on firmware
                        bleManager.clearIntake()
                    }
                }

                // Consume the event
                bleManager.consumeIntakeEvent()

                // Clear the last intake timestamp for debug
                bleManager.clearLastIntakeTimestamp()
            }
        }
    }

    /**
     * Record an intake event in the local database.
     * Uses the phone's current time for date and period determination,
     * since the MCU timestamp may be unsynced (seconds since boot).
     */
    private fun recordIntakeLocally(mcuTimestamp: Long) {
        viewModelScope.launch {
            val now = Calendar.getInstance()
            val phoneTimestamp = System.currentTimeMillis() / 1000

            // Get today's record
            val todayCal = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayStart = todayCal.timeInMillis / 1000

            val existingRecord = repository.getIntakeRecordByDateSync(todayStart)

            // Check 30-minute rule: ignore if less than 30 minutes since last intake
            val lastIntakeTime = getLastIntakeTime(existingRecord)
            val minutesSince = if (lastIntakeTime > 0) {
                (phoneTimestamp - lastIntakeTime) / 60
            } else {
                Long.MAX_VALUE
            }

            if (minutesSince < 30) {
                Log.d(TAG, "Ignoring intake: only ${minutesSince}m since last intake")
                bleManager.clearIntake()
                return@launch
            }

            // Determine which period this intake belongs to based on time ranges
            val schedules = _schedules.value
            val scheduleType = determineScheduleTypeForTimestamp(phoneTimestamp, schedules)

            if (scheduleType == null) {
                Log.d(TAG, "Ignoring intake: no valid schedule for current time")
                bleManager.clearIntake()
                return@launch
            }

            // Check if already taken for this period
            val alreadyTaken = when (scheduleType) {
                ScheduleType.MORNING -> existingRecord?.morningTaken == true
                ScheduleType.AFTERNOON -> existingRecord?.afternoonTaken == true
                ScheduleType.EVENING -> existingRecord?.eveningTaken == true
            }

            if (alreadyTaken) {
                Log.d(TAG, "Ignoring intake: already recorded for $scheduleType")
                bleManager.clearIntake()
                return@launch
            }

            // Record intake
            val record = existingRecord ?: MedicineIntakeRecord(date = todayStart)
            val updatedRecord = when (scheduleType) {
                ScheduleType.MORNING -> record.copy(
                    morningTaken = true,
                    morningTime = phoneTimestamp
                )
                ScheduleType.AFTERNOON -> record.copy(
                    afternoonTaken = true,
                    afternoonTime = phoneTimestamp
                )
                ScheduleType.EVENING -> record.copy(
                    eveningTaken = true,
                    eveningTime = phoneTimestamp
                )
            }

            repository.insertIntakeRecord(updatedRecord)
            Log.d(TAG, "Intake recorded: $scheduleType at phoneTime=$phoneTimestamp (mcuTime=$mcuTimestamp)")

            // Clear intake on firmware
            bleManager.clearIntake()
        }
    }

    /**
     * Get the last intake timestamp from today's record
     */
    private fun getLastIntakeTime(record: MedicineIntakeRecord?): Long {
        return record?.let {
            maxOf(
                if (it.morningTaken) it.morningTime else 0L,
                if (it.afternoonTaken) it.afternoonTime else 0L,
                if (it.eveningTaken) it.eveningTime else 0L
            )
        } ?: 0L
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

        Log.d(TAG, "determineScheduleTypeForTimestamp: timestamp=$timestamp, hour=$hour, minute=$minute, currentMinutes=$currentMinutes")

        for (schedule in schedules) {
            if (!schedule.enabled) continue

            val startMinutes = schedule.startHour * 60 + schedule.startMinute
            val endMinutes = schedule.endHour * 60 + schedule.endMinute

            Log.d(TAG, "Checking schedule ${schedule.id}: enabled=${schedule.enabled}, range=$startMinutes-$endMinutes, current=$currentMinutes")

            // Check if current time is within schedule range
            if (currentMinutes in startMinutes..endMinutes) {
                Log.d(TAG, "Matched schedule: ${schedule.id}")
                return when (schedule.id) {
                    0 -> ScheduleType.MORNING
                    1 -> ScheduleType.AFTERNOON
                    2 -> ScheduleType.EVENING
                    else -> null
                }
            }
        }

        Log.d(TAG, "No matching schedule found")
        return null  // No matching schedule
    }

    /**
     * Determine schedule type based on hour of day.
     * Morning: 4:00 - 11:59
     * Afternoon: 12:00 - 17:59
     * Evening: 18:00 - 3:59 (next day)
     * NOTE: This is fallback for UI display, not for intake recording
     */
    private fun determineScheduleType(hour: Int): ScheduleType {
        return when (hour) {
            in 4..11 -> ScheduleType.MORNING
            in 12..17 -> ScheduleType.AFTERNOON
            else -> ScheduleType.EVENING  // 18-23 and 0-3
        }
    }

    fun startBleScan() {
        bleManager.startScan()
    }

    fun stopBleScan() {
        bleManager.stopScan()
    }

    fun connectToDevice(device: android.bluetooth.BluetoothDevice) {
        bleManager.connectToDevice(device)
    }

    fun disconnectBle() {
        bleManager.disconnect()
    }

    fun syncTime() {
        bleManager.syncTime()
    }

    /**
     * Update schedule in local DB only (not sent to firmware).
     * Schedule management is app-side only in the new architecture.
     */
    fun updateSchedule(schedule: MedicineSchedule) {
        viewModelScope.launch {
            repository.updateSchedule(schedule)
        }
    }

    fun getTodayRecord(): MedicineIntakeRecord? {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis / 1000

        return _intakeRecords.value.find { it.date == todayStart }
    }

    // --- History selection ---

    fun enterSelectMode() {
        _isSelectMode.value = true
        _selectedRecordIds.value = emptySet()
    }

    fun exitSelectMode() {
        _isSelectMode.value = false
        _selectedRecordIds.value = emptySet()
    }

    fun toggleRecordSelection(recordId: Long) {
        val current = _selectedRecordIds.value
        _selectedRecordIds.value = if (recordId in current) {
            current - recordId
        } else {
            current + recordId
        }
    }

    fun selectAllRecords() {
        _selectedRecordIds.value = _intakeRecords.value.map { it.id }.toSet()
    }

    fun deleteSelectedRecords() {
        val ids = _selectedRecordIds.value.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            repository.deleteIntakeRecordsByIds(ids)
            Log.d(TAG, "Deleted ${ids.size} intake records")
            exitSelectMode()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.deleteAllIntakeRecords()
            Log.d(TAG, "All intake records cleared")
        }
    }
}
