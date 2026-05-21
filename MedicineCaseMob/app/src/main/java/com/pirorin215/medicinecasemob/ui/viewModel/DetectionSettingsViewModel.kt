package com.pirorin215.medicinecasemob.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.data.AppSettingsData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetectionSettingsViewModel @Inject constructor(
    private val repository: com.pirorin215.medicinecasemob.ui.data.MedicineRepository,
    private val bleManager: BleManager
) : ViewModel() {

    companion object {
        private const val TAG = "DetectionSettingsViewModel"
    }

    // Settings flow from repository
    val settings = repository.settingsFlow

    // UI State
    private val _movementThreshold = MutableStateFlow(70.0f)
    val movementThreshold: StateFlow<Float> = _movementThreshold.asStateFlow()

    private val _cooldownTime = MutableStateFlow(30000)
    val cooldownTime: StateFlow<Int> = _cooldownTime.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow<String?>(null)
    val saveSuccess: StateFlow<String?> = _saveSuccess.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collect { settings ->
                _movementThreshold.value = settings.movementThreshold
                _cooldownTime.value = settings.cooldownTime.toInt()
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
                val currentSettings = repository.settingsFlow.first()
                val updated = currentSettings.copy(
                    movementThreshold = _movementThreshold.value,
                    cooldownTime = _cooldownTime.value.toLong()
                )
                repository.updateSettings(updated)

                // Log and BLE update
                val logManager = com.pirorin215.medicinecasemob.util.LogManager.getInstance()
                logManager.addInfoLog("=== 検知設定変更・保存 ===")
                logManager.addInfoLog("検出角度: ${updated.movementThreshold}°")
                logManager.addInfoLog("クールダウン: ${updated.cooldownTime}ms")
                logManager.addInfoLog("=====================")

                val isConnected = bleManager.connectionState.value is BleManager.ConnectionState.Connected
                val serviceReady = bleManager.serviceReady.value

                if (isConnected && serviceReady) {
                    bleManager.setDetectionAngle(_movementThreshold.value)
                    bleManager.setDetectionCooldown(_cooldownTime.value.toLong())
                    _saveSuccess.value = "設定を保存してマイコンに送信しました"
                } else {
                    _saveSuccess.value = "設定を保存しました（マイコン未接続）"
                }

                kotlinx.coroutines.delay(3000)
                _saveSuccess.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save detection settings", e)
                _saveSuccess.value = "設定の保存に失敗しました"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = null
    }
}
