package com.pirorin215.medicinecasemob.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.data.DetectionSettings
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import com.pirorin215.medicinecasemob.ui.data.MedicineSchedule
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MedicineRepository,
    private val bleManager: BleManager
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _detectionSettings = MutableStateFlow<DetectionSettings?>(
        DetectionSettings(movementThreshold = 70.0f, cooldownTime = 30000L)
    )
    val detectionSettings: StateFlow<DetectionSettings?> = _detectionSettings.asStateFlow()

    private val _schedules = MutableStateFlow<List<MedicineSchedule>>(emptyList())
    val schedules: StateFlow<List<MedicineSchedule>> = _schedules.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow<String?>(null)
    val saveSuccess: StateFlow<String?> = _saveSuccess.asStateFlow()

    init {
        loadDetectionSettings()
        loadSchedules()
        observeServiceReady()
    }

    private fun loadDetectionSettings() {
        viewModelScope.launch {
            repository.getDetectionSettings().collect { settings ->
                if (settings != null) {
                    _detectionSettings.value = settings
                    Log.d(TAG, "DetectionSettings loaded: angle=${settings.movementThreshold}, cooldown=${settings.cooldownTime}")
                }
            }
        }
    }

    private fun loadSchedules() {
        viewModelScope.launch {
            repository.getAllSchedules().collect { schedules ->
                _schedules.value = schedules
                Log.d(TAG, "Schedules loaded: ${schedules.size} items")
            }
        }
    }

    private fun observeServiceReady() {
        viewModelScope.launch {
            bleManager.serviceReady.collect { ready ->
                Log.d(TAG, "Service ready status: $ready")
            }
        }
    }

    fun updateMovementThreshold(angle: Float) {
        _detectionSettings.value?.let { current ->
            _detectionSettings.value = current.copy(movementThreshold = angle)
            Log.d(TAG, "Movement threshold updated: $angle")
        }
    }

    fun updateCooldownTime(cooldownMs: Long) {
        _detectionSettings.value?.let { current ->
            _detectionSettings.value = current.copy(cooldownTime = cooldownMs)
            Log.d(TAG, "Cooldown time updated: ${cooldownMs}ms")
        }
    }

    fun updateScheduleEnabled(type: ScheduleType, enabled: Boolean) {
        val existing = _schedules.value.find { it.id == type.id }
        if (existing != null) {
            val updated = existing.copy(enabled = enabled)
            _schedules.value = _schedules.value.map {
                if (it.id == type.id) updated else it
            }
        } else {
            // Create new schedule if not in list (DB not yet initialized)
            val newSchedule = MedicineSchedule(
                id = type.id, enabled = enabled,
                hour = type.defaultHour, minute = 0
            )
            _schedules.value = _schedules.value + newSchedule
        }
        Log.d(TAG, "Schedule ${type.displayName} enabled: $enabled")
    }

    fun updateScheduleTime(type: ScheduleType, hour: Int, minute: Int) {
        val existing = _schedules.value.find { it.id == type.id }
        if (existing != null) {
            val updated = existing.copy(hour = hour, minute = minute)
            _schedules.value = _schedules.value.map {
                if (it.id == type.id) updated else it
            }
        } else {
            // Create new schedule if not in list
            val newSchedule = MedicineSchedule(
                id = type.id, enabled = true,
                hour = hour, minute = minute
            )
            _schedules.value = _schedules.value + newSchedule
        }
        Log.d(TAG, "Schedule ${type.displayName} time updated: $hour:$minute")
    }

    fun saveSettings() {
        val settings = _detectionSettings.value ?: run {
            Log.e(TAG, "Cannot save: detectionSettings is null")
            return
        }

        Log.d(TAG, "Saving settings: angle=${settings.movementThreshold}, cooldown=${settings.cooldownTime}")

        viewModelScope.launch {
            _isSaving.value = true

            try {
                // Save detection settings to local database
                Log.d(TAG, "Saving detection settings to database...")
                repository.updateDetectionSettings(settings)
                Log.d(TAG, "Detection settings save successful")

                // Save schedules to local database (use insert with REPLACE for new records)
                Log.d(TAG, "Saving schedules to database...")
                for (schedule in _schedules.value) {
                    repository.insertSchedule(schedule)
                }
                Log.d(TAG, "Schedules save successful")

                // Send detection settings to device via BLE
                val isConnected = bleManager.connectionState.value is BleManager.ConnectionState.Connected
                val serviceReady = bleManager.serviceReady.value

                if (isConnected && serviceReady) {
                    Log.d(TAG, "Sending detection settings to device...")
                    val angleSuccess = bleManager.setDetectionAngle(settings.movementThreshold)
                    Log.d(TAG, "Angle send result: $angleSuccess")

                    val cooldownSuccess = bleManager.setDetectionCooldown(settings.cooldownTime)
                    Log.d(TAG, "Cooldown send result: $cooldownSuccess")

                    if (angleSuccess && cooldownSuccess) {
                        _saveSuccess.value = "設定を保存してマイコンに送信しました"
                    } else {
                        _saveSuccess.value = "設定を保存しましたが、マイコンへの送信に失敗しました"
                        Log.w(TAG, "BLE send failed: angle=$angleSuccess, cooldown=$cooldownSuccess")
                    }
                } else {
                    _saveSuccess.value = "設定を保存しました（BLE未接続のためマイコンには送信されません）"
                    Log.d(TAG, "BLE not connected or not ready, skipping device send")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save settings", e)
                _saveSuccess.value = "設定の保存に失敗しました: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = null
        Log.d(TAG, "Save success message cleared")
    }
}
