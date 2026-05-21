package com.pirorin215.medicinecasemob.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pirorin215.medicinecasemob.ui.data.MedicineSchedule
import com.pirorin215.medicinecasemob.ui.data.MedicineSettingsRepository
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import com.pirorin215.medicinecasemob.util.LogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleSettingsViewModel @Inject constructor(
    private val repository: com.pirorin215.medicinecasemob.ui.data.MedicineRepository,
    private val settingsRepository: MedicineSettingsRepository,
    private val logManager: LogManager
) : ViewModel() {

    companion object {
        private const val TAG = "ScheduleSettingsViewModel"
    }

    // Morning schedule
    private val _morningEnabled = MutableStateFlow(true)
    val morningEnabled: StateFlow<Boolean> = _morningEnabled.asStateFlow()

    private val _morningStartHour = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_MORNING_START_HOUR)
    val morningStartHour: StateFlow<Int> = _morningStartHour.asStateFlow()

    private val _morningStartMinute = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_MORNING_START_MINUTE)
    val morningStartMinute: StateFlow<Int> = _morningStartMinute.asStateFlow()

    private val _morningEndHour = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_MORNING_END_HOUR)
    val morningEndHour: StateFlow<Int> = _morningEndHour.asStateFlow()

    private val _morningEndMinute = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_MORNING_END_MINUTE)
    val morningEndMinute: StateFlow<Int> = _morningEndMinute.asStateFlow()

    // Afternoon schedule
    private val _afternoonEnabled = MutableStateFlow(true)
    val afternoonEnabled: StateFlow<Boolean> = _afternoonEnabled.asStateFlow()

    private val _afternoonStartHour = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_AFTERNOON_START_HOUR)
    val afternoonStartHour: StateFlow<Int> = _afternoonStartHour.asStateFlow()

    private val _afternoonStartMinute = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_AFTERNOON_START_MINUTE)
    val afternoonStartMinute: StateFlow<Int> = _afternoonStartMinute.asStateFlow()

    private val _afternoonEndHour = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_AFTERNOON_END_HOUR)
    val afternoonEndHour: StateFlow<Int> = _afternoonEndHour.asStateFlow()

    private val _afternoonEndMinute = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_AFTERNOON_END_MINUTE)
    val afternoonEndMinute: StateFlow<Int> = _afternoonEndMinute.asStateFlow()

    // Evening schedule
    private val _eveningEnabled = MutableStateFlow(true)
    val eveningEnabled: StateFlow<Boolean> = _eveningEnabled.asStateFlow()

    private val _eveningStartHour = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_EVENING_START_HOUR)
    val eveningStartHour: StateFlow<Int> = _eveningStartHour.asStateFlow()

    private val _eveningStartMinute = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_EVENING_START_MINUTE)
    val eveningStartMinute: StateFlow<Int> = _eveningStartMinute.asStateFlow()

    private val _eveningEndHour = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_EVENING_END_HOUR)
    val eveningEndHour: StateFlow<Int> = _eveningEndHour.asStateFlow()

    private val _eveningEndMinute = MutableStateFlow(com.pirorin215.medicinecasemob.ui.data.MedicineSettings.DEFAULT_EVENING_END_MINUTE)
    val eveningEndMinute: StateFlow<Int> = _eveningEndMinute.asStateFlow()

    // Computed schedules for UI
    val schedules: StateFlow<List<MedicineSchedule>> = combine(
        _morningEnabled, _morningStartHour, _morningStartMinute, _morningEndHour, _morningEndMinute,
        _afternoonEnabled, _afternoonStartHour, _afternoonStartMinute, _afternoonEndHour, _afternoonEndMinute,
        _eveningEnabled, _eveningStartHour, _eveningStartMinute, _eveningEndHour, _eveningEndMinute
    ) { array ->
        val mEn = array[0] as Boolean
        val mSH = array[1] as Int
        val mSM = array[2] as Int
        val mEH = array[3] as Int
        val mEM = array[4] as Int
        val aEn = array[5] as Boolean
        val aSH = array[6] as Int
        val aSM = array[7] as Int
        val aEH = array[8] as Int
        val aEM = array[9] as Int
        val eEn = array[10] as Boolean
        val eSH = array[11] as Int
        val eSM = array[12] as Int
        val eEH = array[13] as Int
        val eEM = array[14] as Int
        listOf(
            MedicineSchedule(id = ScheduleType.MORNING.id, enabled = mEn, startHour = mSH, startMinute = mSM, endHour = mEH, endMinute = mEM),
            MedicineSchedule(id = ScheduleType.AFTERNOON.id, enabled = aEn, startHour = aSH, startMinute = aSM, endHour = aEH, endMinute = aEM),
            MedicineSchedule(id = ScheduleType.EVENING.id, enabled = eEn, startHour = eSH, startMinute = eSM, endHour = eEH, endMinute = eEM)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf(
            MedicineSchedule(id = ScheduleType.MORNING.id, enabled = true, startHour = 8, startMinute = 0, endHour = 11, endMinute = 0),
            MedicineSchedule(id = ScheduleType.AFTERNOON.id, enabled = true, startHour = 12, startMinute = 0, endHour = 17, endMinute = 0),
            MedicineSchedule(id = ScheduleType.EVENING.id, enabled = true, startHour = 19, startMinute = 0, endHour = 22, endMinute = 0)
        )
    )

    init {
        // First, load from Room DB to initialize with correct values
        loadSchedulesFromDb()

        // Then, observe DataStore for changes
        loadSettings()
    }

    private fun loadSchedulesFromDb() {
        viewModelScope.launch {
            repository.getAllSchedules().collect { schedules ->
                schedules.forEach { schedule ->
                    when (schedule.id) {
                        ScheduleType.MORNING.id -> {
                            _morningEnabled.value = schedule.enabled
                            _morningStartHour.value = schedule.startHour
                            _morningStartMinute.value = schedule.startMinute
                            _morningEndHour.value = schedule.endHour
                            _morningEndMinute.value = schedule.endMinute
                        }
                        ScheduleType.AFTERNOON.id -> {
                            _afternoonEnabled.value = schedule.enabled
                            _afternoonStartHour.value = schedule.startHour
                            _afternoonStartMinute.value = schedule.startMinute
                            _afternoonEndHour.value = schedule.endHour
                            _afternoonEndMinute.value = schedule.endMinute
                        }
                        ScheduleType.EVENING.id -> {
                            _eveningEnabled.value = schedule.enabled
                            _eveningStartHour.value = schedule.startHour
                            _eveningStartMinute.value = schedule.startMinute
                            _eveningEndHour.value = schedule.endHour
                            _eveningEndMinute.value = schedule.endMinute
                        }
                    }
                }
                Log.d(TAG, "Schedules loaded from Room DB: ${schedules.size} items")
            }
        }
    }

    private fun loadSettings() {
        // Load morning schedule from DataStore
        viewModelScope.launch {
            settingsRepository.morningEnabled.collect { value ->
                _morningEnabled.value = value
            }
            settingsRepository.morningStartHour.collect { value ->
                _morningStartHour.value = value
            }
            settingsRepository.morningStartMinute.collect { value ->
                _morningStartMinute.value = value
            }
            settingsRepository.morningEndHour.collect { value ->
                _morningEndHour.value = value
            }
            settingsRepository.morningEndMinute.collect { value ->
                _morningEndMinute.value = value
            }
        }

        // Load afternoon schedule from DataStore
        viewModelScope.launch {
            settingsRepository.afternoonEnabled.collect { value ->
                _afternoonEnabled.value = value
            }
            settingsRepository.afternoonStartHour.collect { value ->
                _afternoonStartHour.value = value
            }
            settingsRepository.afternoonStartMinute.collect { value ->
                _afternoonStartMinute.value = value
            }
            settingsRepository.afternoonEndHour.collect { value ->
                _afternoonEndHour.value = value
            }
            settingsRepository.afternoonEndMinute.collect { value ->
                _afternoonEndMinute.value = value
            }
        }

        // Load evening schedule from DataStore
        viewModelScope.launch {
            settingsRepository.eveningEnabled.collect { value ->
                _eveningEnabled.value = value
            }
            settingsRepository.eveningStartHour.collect { value ->
                _eveningStartHour.value = value
            }
            settingsRepository.eveningStartMinute.collect { value ->
                _eveningStartMinute.value = value
            }
            settingsRepository.eveningEndHour.collect { value ->
                _eveningEndHour.value = value
            }
            settingsRepository.eveningEndMinute.collect { value ->
                _eveningEndMinute.value = value
            }
        }
    }

    fun updateScheduleEnabled(type: ScheduleType, enabled: Boolean) {
        when (type) {
            ScheduleType.MORNING -> _morningEnabled.value = enabled
            ScheduleType.AFTERNOON -> _afternoonEnabled.value = enabled
            ScheduleType.EVENING -> _eveningEnabled.value = enabled
        }
        // Auto-save
        saveSettings()
    }

    fun updateScheduleTimeRange(type: ScheduleType, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        when (type) {
            ScheduleType.MORNING -> {
                _morningStartHour.value = startHour
                _morningStartMinute.value = startMinute
                _morningEndHour.value = endHour
                _morningEndMinute.value = endMinute
            }
            ScheduleType.AFTERNOON -> {
                _afternoonStartHour.value = startHour
                _afternoonStartMinute.value = startMinute
                _afternoonEndHour.value = endHour
                _afternoonEndMinute.value = endMinute
            }
            ScheduleType.EVENING -> {
                _eveningStartHour.value = startHour
                _eveningStartMinute.value = startMinute
                _eveningEndHour.value = endHour
                _eveningEndMinute.value = endMinute
            }
        }
        // Auto-save
        saveSettings()
    }

    fun saveSettings() {
        viewModelScope.launch {
            try {
                // Save all settings to DataStore
                settingsRepository.saveMorningEnabled(_morningEnabled.value)
                settingsRepository.saveMorningTimeRange(
                    _morningStartHour.value, _morningStartMinute.value,
                    _morningEndHour.value, _morningEndMinute.value
                )

                settingsRepository.saveAfternoonEnabled(_afternoonEnabled.value)
                settingsRepository.saveAfternoonTimeRange(
                    _afternoonStartHour.value, _afternoonStartMinute.value,
                    _afternoonEndHour.value, _afternoonEndMinute.value
                )

                settingsRepository.saveEveningEnabled(_eveningEnabled.value)
                settingsRepository.saveEveningTimeRange(
                    _eveningStartHour.value, _eveningStartMinute.value,
                    _eveningEndHour.value, _eveningEndMinute.value
                )

                Log.d(TAG, "Schedule settings saved to DataStore successfully")

                // Also save to Room database for MainViewModel
                val morningSchedule = com.pirorin215.medicinecasemob.ui.data.MedicineSchedule(
                    id = ScheduleType.MORNING.id,
                    enabled = _morningEnabled.value,
                    startHour = _morningStartHour.value,
                    startMinute = _morningStartMinute.value,
                    endHour = _morningEndHour.value,
                    endMinute = _morningEndMinute.value
                )

                val afternoonSchedule = com.pirorin215.medicinecasemob.ui.data.MedicineSchedule(
                    id = ScheduleType.AFTERNOON.id,
                    enabled = _afternoonEnabled.value,
                    startHour = _afternoonStartHour.value,
                    startMinute = _afternoonStartMinute.value,
                    endHour = _afternoonEndHour.value,
                    endMinute = _afternoonEndMinute.value
                )

                val eveningSchedule = com.pirorin215.medicinecasemob.ui.data.MedicineSchedule(
                    id = ScheduleType.EVENING.id,
                    enabled = _eveningEnabled.value,
                    startHour = _eveningStartHour.value,
                    startMinute = _eveningStartMinute.value,
                    endHour = _eveningEndHour.value,
                    endMinute = _eveningEndMinute.value
                )

                // Save all to Room database
                repository.insertSchedule(morningSchedule)
                repository.insertSchedule(afternoonSchedule)
                repository.insertSchedule(eveningSchedule)

                Log.d(TAG, "Schedule settings saved to Room DB successfully")

                // Log current schedule settings for debugging
                logManager.addInfoLog("=== スケジュール設定保存 ===")
                logManager.addInfoLog("朝: ${_morningEnabled.value} %02d:%02d-%02d:%02d".format(
                    _morningStartHour.value, _morningStartMinute.value,
                    _morningEndHour.value, _morningEndMinute.value
                ))
                logManager.addInfoLog("昼: ${_afternoonEnabled.value} %02d:%02d-%02d:%02d".format(
                    _afternoonStartHour.value, _afternoonStartMinute.value,
                    _afternoonEndHour.value, _afternoonEndMinute.value
                ))
                logManager.addInfoLog("夜: ${_eveningEnabled.value} %02d:%02d-%02d:%02d".format(
                    _eveningStartHour.value, _eveningStartMinute.value,
                    _eveningEndHour.value, _eveningEndMinute.value
                ))
                logManager.addInfoLog("===========================")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save settings", e)
            }
        }
    }
}
