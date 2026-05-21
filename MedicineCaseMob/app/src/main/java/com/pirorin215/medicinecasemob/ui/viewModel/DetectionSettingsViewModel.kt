package com.pirorin215.medicinecasemob.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.data.MedicineSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetectionSettingsViewModel @Inject constructor(
    private val settingsRepository: MedicineSettingsRepository,
    private val bleManager: BleManager
) : ViewModel() {

    companion object {
        private const val TAG = "DetectionSettingsViewModel"
    }

    // Detection settings
    private val _movementThreshold = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_MOVEMENT_THRESHOLD)
    val movementThreshold: StateFlow<Float> = _movementThreshold.asStateFlow()

    private val _cooldownTime = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_COOLDOWN_TIME)
    val cooldownTime: StateFlow<Int> = _cooldownTime.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow<String?>(null)
    val saveSuccess: StateFlow<String?> = _saveSuccess.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        // Load detection settings from DataStore
        viewModelScope.launch {
            settingsRepository.movementThreshold.collect { value ->
                _movementThreshold.value = value
            }
        }

        viewModelScope.launch {
            settingsRepository.cooldownTime.collect { value ->
                _cooldownTime.value = value
            }
        }
    }

    fun updateMovementThreshold(angle: Float) {
        _movementThreshold.value = angle
    }

    fun updateCooldownTime(cooldownMs: Long) {
        _cooldownTime.value = cooldownMs.toInt()
    }

    fun saveSettings() {
        viewModelScope.launch {
            _isSaving.value = true

            try {
                // Save detection settings to DataStore
                settingsRepository.saveMovementThreshold(_movementThreshold.value)
                settingsRepository.saveCooldownTime(_cooldownTime.value)

                Log.d(TAG, "Detection settings saved to DataStore successfully")

                // Send detection settings to device via BLE
                val isConnected = bleManager.connectionState.value is BleManager.ConnectionState.Connected
                val serviceReady = bleManager.serviceReady.value

                if (isConnected && serviceReady) {
                    val angleSuccess = bleManager.setDetectionAngle(_movementThreshold.value)
                    val cooldownSuccess = bleManager.setDetectionCooldown(_cooldownTime.value.toLong())

                    if (angleSuccess && cooldownSuccess) {
                        _saveSuccess.value = "設定を保存してマイコンに送信しました"
                    } else {
                        _saveSuccess.value = "設定を保存しましたが、マイコンへの送信に失敗しました"
                    }
                } else {
                    _saveSuccess.value = "設定を保存しました"
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
    }
}
