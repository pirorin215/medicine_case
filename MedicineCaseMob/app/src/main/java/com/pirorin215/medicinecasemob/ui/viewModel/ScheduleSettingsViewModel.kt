package com.pirorin215.medicinecasemob.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pirorin215.medicinecasemob.ui.data.AppSettingsData
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
class ScheduleSettingsViewModel @Inject constructor(
    private val repository: com.pirorin215.medicinecasemob.ui.data.MedicineRepository,
    private val logManager: LogManager
) : ViewModel() {

    companion object {
        private const val TAG = "ScheduleSettingsViewModel"
    }

    // Settings flow from repository
    val settings = repository.settingsFlow

    // UI State for simple settings
    private val _onlyNotifyWhenBleConnected = MutableStateFlow(false)
    val onlyNotifyWhenBleConnected: StateFlow<Boolean> = _onlyNotifyWhenBleConnected.asStateFlow()

    private val _notificationIntervalMinutes = MutableStateFlow(60)
    val notificationIntervalMinutes: StateFlow<Int> = _notificationIntervalMinutes.asStateFlow()

    private val _uiFontSizeScale = MutableStateFlow(1.2f)
    val uiFontSizeScale: StateFlow<Float> = _uiFontSizeScale.asStateFlow()

    // Computed schedules for UI
    val schedules: StateFlow<List<MedicineSchedule>> = settings.map { settings ->
        repository.getSchedulesFromSettings(settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settings.collect { settings ->
                _onlyNotifyWhenBleConnected.value = settings.onlyNotifyWhenBleConnected
                _notificationIntervalMinutes.value = settings.notificationIntervalMinutes
                _uiFontSizeScale.value = settings.uiFontSizeScale
            }
        }
    }

    fun updateScheduleEnabled(type: ScheduleType, enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.settingsFlow.first()
            val updated = when (type) {
                ScheduleType.MORNING -> current.copy(morningEnabled = enabled)
                ScheduleType.AFTERNOON -> current.copy(afternoonEnabled = enabled)
                ScheduleType.EVENING -> current.copy(eveningEnabled = enabled)
            }
            saveAndLogSettings(updated)
        }
    }

    fun updateScheduleTimeRange(type: ScheduleType, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            val current = repository.settingsFlow.first()
            val updated = when (type) {
                ScheduleType.MORNING -> current.copy(
                    morningStartHour = startHour, morningStartMinute = startMinute,
                    morningEndHour = endHour, morningEndMinute = endMinute,
                    notifiedAtEndOfMorning = if (current.morningEndHour != endHour || current.morningEndMinute != endMinute) false else current.notifiedAtEndOfMorning
                )
                ScheduleType.AFTERNOON -> current.copy(
                    afternoonStartHour = startHour, afternoonStartMinute = startMinute,
                    afternoonEndHour = endHour, afternoonEndMinute = endMinute,
                    notifiedAtEndOfAfternoon = if (current.afternoonEndHour != endHour || current.afternoonEndMinute != endMinute) false else current.notifiedAtEndOfAfternoon
                )
                ScheduleType.EVENING -> current.copy(
                    eveningStartHour = startHour, eveningStartMinute = startMinute,
                    eveningEndHour = endHour, eveningEndMinute = endMinute,
                    notifiedAtEndOfEvening = if (current.eveningEndHour != endHour || current.eveningEndMinute != endMinute) false else current.notifiedAtEndOfEvening
                )
            }
            saveAndLogSettings(updated)
        }
    }

    fun updateScheduleReminderTime(type: ScheduleType, hour: Int, minute: Int) {
        viewModelScope.launch {
            val current = repository.settingsFlow.first()
            val updated = when (type) {
                ScheduleType.MORNING -> current.copy(morningReminderHour = hour, morningReminderMinute = minute, notifiedInSlotMorning = false)
                ScheduleType.AFTERNOON -> current.copy(afternoonReminderHour = hour, afternoonReminderMinute = minute, notifiedInSlotAfternoon = false)
                ScheduleType.EVENING -> current.copy(eveningReminderHour = hour, eveningReminderMinute = minute, notifiedInSlotEvening = false)
            }
            saveAndLogSettings(updated)
        }
    }

    fun updateOnlyNotifyWhenBleConnected(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.settingsFlow.first()
            val updated = current.copy(onlyNotifyWhenBleConnected = enabled)
            saveAndLogSettings(updated)
        }
    }

    fun updateNotificationIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            val current = repository.settingsFlow.first()
            val updated = current.copy(notificationIntervalMinutes = minutes)
            saveAndLogSettings(updated)
        }
    }

    fun updateUiFontSizeScale(scale: Float) {
        viewModelScope.launch {
            val current = repository.settingsFlow.first()
            val updated = current.copy(uiFontSizeScale = scale)
            saveAndLogSettings(updated)
        }
    }

    private suspend fun saveAndLogSettings(updated: AppSettingsData) {
        try {
            repository.updateSettings(updated)
            Log.d(TAG, "Settings saved to DataStore successfully")

            // スケジュール変更時は当日の服薬記録をリセットする
            resetTodayIntakeRecord()

            // 設定内容をダンプ
            logManager.addInfoLog("=== 設定変更・保存 ===")
            logManager.addInfoLog("■スケジュール")
            logManager.addInfoLog("  朝: ${if (updated.morningEnabled) "有効" else "無効"} ${String.format("%02d:%02d-%02d:%02d", updated.morningStartHour, updated.morningStartMinute, updated.morningEndHour, updated.morningEndMinute)} (推奨: ${String.format("%02d:%02d", updated.morningReminderHour, updated.morningReminderMinute)})")
            logManager.addInfoLog("  昼: ${if (updated.afternoonEnabled) "有効" else "無効"} ${String.format("%02d:%02d-%02d:%02d", updated.afternoonStartHour, updated.afternoonStartMinute, updated.afternoonEndHour, updated.afternoonEndMinute)} (推奨: ${String.format("%02d:%02d", updated.afternoonReminderHour, updated.afternoonReminderMinute)})")
            logManager.addInfoLog("  夜: ${if (updated.eveningEnabled) "有効" else "無効"} ${String.format("%02d:%02d-%02d:%02d", updated.eveningStartHour, updated.eveningStartMinute, updated.eveningEndHour, updated.eveningEndMinute)} (推奨: ${String.format("%02d:%02d", updated.eveningReminderHour, updated.eveningReminderMinute)})")
            logManager.addInfoLog("■通知")
            logManager.addInfoLog("  BLE接続時のみ通知: ${if (updated.onlyNotifyWhenBleConnected) "ON" else "OFF"}")
            logManager.addInfoLog("  リマインダー間隔: ${updated.notificationIntervalMinutes}分")
            logManager.addInfoLog("=====================")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save settings", e)
        }
    }

    private fun resetTodayIntakeRecord() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis / 1000

            val existingRecord = repository.getIntakeRecordByDateSync(todayStart)
            if (existingRecord != null) {
                val currentSettings = repository.settingsFlow.first()
                // 当日の記録が存在する場合はリセット（削除ではなく未服用状態に更新）
                // 同時に、その時点での有効フラグも同期する
                val resetRecord = existingRecord.copy(
                    morningTaken = false, morningTime = 0L,
                    morningEnabled = currentSettings.morningEnabled,
                    afternoonTaken = false, afternoonTime = 0L,
                    afternoonEnabled = currentSettings.afternoonEnabled,
                    eveningTaken = false, eveningTime = 0L,
                    eveningEnabled = currentSettings.eveningEnabled
                )
                repository.insertIntakeRecord(resetRecord)
                logManager.addInfoLog("スケジュール変更に伴い、当日の服薬記録をリセットしました")
                Log.d(TAG, "Today's intake record reset due to schedule change")
            }
        }
    }
}
