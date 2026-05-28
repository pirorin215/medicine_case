package com.pirorin215.medicinecasemob.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.notification.NotificationService
import com.pirorin215.medicinecasemob.ui.data.MedicineIntakeRecord
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import com.pirorin215.medicinecasemob.ui.data.MedicineSchedule
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import com.pirorin215.medicinecasemob.util.LogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MedicineRepository,
    private val bleManager: BleManager,
    private val notificationService: NotificationService,
    private val logManager: LogManager
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // Settings and Schedules from repository
    val settings = repository.settingsFlow
    val schedules = settings.map { settings ->
        repository.getSchedulesFromSettings(settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        ensureTodayRecordExists()
        loadIntakeRecords()
        observeBleConnectionState()
    }

    private fun ensureTodayRecordExists() {
        viewModelScope.launch {
            repository.ensureTodayRecordExists()
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
            }
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
            repository.clearIntakeRecordsByIds(ids)
            Log.d(TAG, "Cleared ${ids.size} intake records")

            // Re-fetch intake from BLE device to restore cleared records
            bleManager.clearAndGetIntake()

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
