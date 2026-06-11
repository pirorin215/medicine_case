package com.pirorin215.medicinecasemob.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.data.AppSettingsData
import com.pirorin215.medicinecasemob.ui.data.MedicineSchedule
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import com.pirorin215.medicinecasemob.ui.data.formatTimeOfDay
import com.pirorin215.medicinecasemob.ui.data.formatTimeRange
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
    private val logManager: LogManager,
    private val bleManager: BleManager
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
                ScheduleType.MORNING -> {
                    // 朝の時間範囲を変更
                    // - 朝の開始時刻: morningStartMinute
                    // - 昼の開始時刻: afternoonStartMinute（朝の終了時刻として使用）
                    val newMorningStart = startHour * 60 + startMinute
                    val newAfternoonStart = endHour * 60 + endMinute

                    current.copy(
                        morningStartMinute = newMorningStart,
                        afternoonStartMinute = newAfternoonStart,
                        notifiedAtEndOfMorning = false
                    )
                }
                ScheduleType.AFTERNOON -> {
                    // 昼の時間範囲を変更
                    // - 昼の開始時刻: afternoonStartMinute（朝の終了時刻としても使用）
                    // - 夜の開始時刻: eveningStartMinute（昼の終了時刻として使用）
                    val newAfternoonStart = startHour * 60 + startMinute
                    val newEveningStart = endHour * 60 + endMinute

                    current.copy(
                        afternoonStartMinute = newAfternoonStart,
                        eveningStartMinute = newEveningStart,
                        notifiedAtEndOfAfternoon = false
                    )
                }
                ScheduleType.EVENING -> {
                    // 夜の時間範囲を変更
                    // - 夜の開始時刻: eveningStartMinute（昼の終了時刻としても使用）
                    // - 1日の終了時刻: dayEndMinute
                    val newEveningStart = startHour * 60 + startMinute
                    val newDayEnd = endHour * 60 + endMinute

                    current.copy(
                        eveningStartMinute = newEveningStart,
                        dayEndMinute = newDayEnd,
                        notifiedAtEndOfEvening = false
                    )
                }
            }
            saveAndLogSettings(updated)
        }
    }

    fun updateScheduleReminderTime(type: ScheduleType, hour: Int, minute: Int) {
        viewModelScope.launch {
            val current = repository.settingsFlow.first()
            val reminderMinute = hour * 60 + minute
            val updated = when (type) {
                ScheduleType.MORNING -> current.copy(
                    morningReminderMinute = reminderMinute,
                    morningNotifiedInSlot = false
                )
                ScheduleType.AFTERNOON -> current.copy(
                    afternoonReminderMinute = reminderMinute,
                    afternoonNotifiedInSlot = false
                )
                ScheduleType.EVENING -> current.copy(
                    eveningReminderMinute = reminderMinute,
                    eveningNotifiedInSlot = false
                )
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
            logManager.addInfoLog("  朝: ${if (updated.morningEnabled) "有効" else "無効"} ${formatTimeRange(updated.morningStartMinute, updated.afternoonStartMinute)} (推奨: ${updated.morningReminderMinute.formatTimeOfDay()})")
            logManager.addInfoLog("  昼: ${if (updated.afternoonEnabled) "有効" else "無効"} ${formatTimeRange(updated.afternoonStartMinute, updated.eveningStartMinute)} (推奨: ${updated.afternoonReminderMinute.formatTimeOfDay()})")
            logManager.addInfoLog("  夜: ${if (updated.eveningEnabled) "有効" else "無効"} ${formatTimeRange(updated.eveningStartMinute, updated.dayEndMinute)} (推奨: ${updated.eveningReminderMinute.formatTimeOfDay()})")
            logManager.addInfoLog("■通知")
            logManager.addInfoLog("  BLE接続時のみ通知: ${if (updated.onlyNotifyWhenBleConnected) "ON" else "OFF"}")
            logManager.addInfoLog("  リマインダー間隔: ${updated.notificationIntervalMinutes}分")
            logManager.addInfoLog("=====================")

            // BLE接続中なら、マイコン側のINTAKEデータを再取得（リセット後のDBに反映）
            if (bleManager.connectionState.value is BleManager.ConnectionState.Connected) {
                logManager.addInfoLog("BLE接続中のため、INTAKE再取得を実行")
                bleManager.getIntake()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save settings", e)
        }
    }

    private suspend fun resetTodayIntakeRecord() {
        val todayStart = repository.getTodayStartTimestamp()

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
