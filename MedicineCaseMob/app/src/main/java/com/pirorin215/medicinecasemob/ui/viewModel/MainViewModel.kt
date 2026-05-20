package com.pirorin215.medicinecasemob.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.data.MedicineIntakeRecord
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import com.pirorin215.medicinecasemob.ui.data.MedicineSchedule
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
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
    private val bleManager: BleManager
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

    val bleConnectionState = bleManager.connectionState
    val scanResults = bleManager.scanResults

    init {
        loadSchedules()
        loadIntakeRecords()
        observeBleConnectionState()
        observeIntakeEvents()
    }

    private fun loadSchedules() {
        viewModelScope.launch {
            repository.getAllSchedules().collect { schedules ->
                _schedules.value = schedules
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

                // On reconnect, check for missed intake events
                if (state is BleManager.ConnectionState.Connected) {
                    bleManager.getIntake()
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
            }
        }
    }

    /**
     * Record an intake event in the local database.
     * Determines morning/afternoon/evening based on the hour of the intake timestamp.
     */
    private fun recordIntakeLocally(timestamp: Long) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = timestamp * 1000
            }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)

            // Determine which period this intake belongs to
            val scheduleType = determineScheduleType(hour)

            // Get or create today's record
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis / 1000

            val existingRecord = repository.getIntakeRecordByDateSync(todayStart)
            val record = existingRecord ?: MedicineIntakeRecord(date = todayStart)

            val updatedRecord = when (scheduleType) {
                ScheduleType.MORNING -> record.copy(
                    morningTaken = true,
                    morningTime = timestamp
                )
                ScheduleType.AFTERNOON -> record.copy(
                    afternoonTaken = true,
                    afternoonTime = timestamp
                )
                ScheduleType.EVENING -> record.copy(
                    eveningTaken = true,
                    eveningTime = timestamp
                )
            }

            repository.insertIntakeRecord(updatedRecord)
            Log.d(TAG, "Intake recorded: $scheduleType at timestamp=$timestamp")
        }
    }

    /**
     * Determine schedule type based on hour of day.
     * Morning: 4:00 - 11:59
     * Afternoon: 12:00 - 17:59
     * Evening: 18:00 - 3:59 (next day)
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

    fun getScheduleName(type: ScheduleType): String {
        return type.displayName
    }
}
