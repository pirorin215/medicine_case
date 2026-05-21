package com.pirorin215.medicinecasemob.ui.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore initialization
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "medicine_settings")

/**
 * Settings keys and utilities for DataStore-based persistence.
 * Unlike Room DB, DataStore is not affected by DB version changes.
 */
object MedicineSettings {
    // Detection Settings
    val MOVEMENT_THRESHOLD = floatPreferencesKey("movement_threshold")
    val COOLDOWN_TIME = intPreferencesKey("cooldown_time")

    // Schedule Settings
    val MORNING_ENABLED = booleanPreferencesKey("morning_enabled")
    val MORNING_START_HOUR = intPreferencesKey("morning_start_hour")
    val MORNING_START_MINUTE = intPreferencesKey("morning_start_minute")
    val MORNING_END_HOUR = intPreferencesKey("morning_end_hour")
    val MORNING_END_MINUTE = intPreferencesKey("morning_end_minute")

    val AFTERNOON_ENABLED = booleanPreferencesKey("afternoon_enabled")
    val AFTERNOON_START_HOUR = intPreferencesKey("afternoon_start_hour")
    val AFTERNOON_START_MINUTE = intPreferencesKey("afternoon_start_minute")
    val AFTERNOON_END_HOUR = intPreferencesKey("afternoon_end_hour")
    val AFTERNOON_END_MINUTE = intPreferencesKey("afternoon_end_minute")

    val EVENING_ENABLED = booleanPreferencesKey("evening_enabled")
    val EVENING_START_HOUR = intPreferencesKey("evening_start_hour")
    val EVENING_START_MINUTE = intPreferencesKey("evening_start_minute")
    val EVENING_END_HOUR = intPreferencesKey("evening_end_hour")
    val EVENING_END_MINUTE = intPreferencesKey("evening_end_minute")

    // Default values
    const val DEFAULT_MOVEMENT_THRESHOLD = 70.0f
    const val DEFAULT_COOLDOWN_TIME = 30000  // 30 seconds

    const val DEFAULT_MORNING_START_HOUR = 8
    const val DEFAULT_MORNING_START_MINUTE = 0
    const val DEFAULT_MORNING_END_HOUR = 11
    const val DEFAULT_MORNING_END_MINUTE = 0

    const val DEFAULT_AFTERNOON_START_HOUR = 12
    const val DEFAULT_AFTERNOON_START_MINUTE = 0
    const val DEFAULT_AFTERNOON_END_HOUR = 17
    const val DEFAULT_AFTERNOON_END_MINUTE = 0

    const val DEFAULT_EVENING_START_HOUR = 19
    const val DEFAULT_EVENING_START_MINUTE = 0
    const val DEFAULT_EVENING_END_HOUR = 22
    const val DEFAULT_EVENING_END_MINUTE = 0
}

/**
 * Repository for medicine app settings using DataStore.
 * Settings persist independently of Room DB version changes.
 */
class MedicineSettingsRepository(private val context: Context) {

    // Detection Settings
    val movementThreshold: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.MOVEMENT_THRESHOLD] ?: MedicineSettings.DEFAULT_MOVEMENT_THRESHOLD
    }

    val cooldownTime: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.COOLDOWN_TIME] ?: MedicineSettings.DEFAULT_COOLDOWN_TIME
    }

    // Schedule Settings
    val morningEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.MORNING_ENABLED] ?: true
    }

    val morningStartHour: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.MORNING_START_HOUR] ?: MedicineSettings.DEFAULT_MORNING_START_HOUR
    }

    val morningStartMinute: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.MORNING_START_MINUTE] ?: MedicineSettings.DEFAULT_MORNING_START_MINUTE
    }

    val morningEndHour: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.MORNING_END_HOUR] ?: MedicineSettings.DEFAULT_MORNING_END_HOUR
    }

    val morningEndMinute: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.MORNING_END_MINUTE] ?: MedicineSettings.DEFAULT_MORNING_END_MINUTE
    }

    val afternoonEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.AFTERNOON_ENABLED] ?: true
    }

    val afternoonStartHour: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.AFTERNOON_START_HOUR] ?: MedicineSettings.DEFAULT_AFTERNOON_START_HOUR
    }

    val afternoonStartMinute: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.AFTERNOON_START_MINUTE] ?: MedicineSettings.DEFAULT_AFTERNOON_START_MINUTE
    }

    val afternoonEndHour: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.AFTERNOON_END_HOUR] ?: MedicineSettings.DEFAULT_AFTERNOON_END_HOUR
    }

    val afternoonEndMinute: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.AFTERNOON_END_MINUTE] ?: MedicineSettings.DEFAULT_AFTERNOON_END_MINUTE
    }

    val eveningEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.EVENING_ENABLED] ?: true
    }

    val eveningStartHour: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.EVENING_START_HOUR] ?: MedicineSettings.DEFAULT_EVENING_START_HOUR
    }

    val eveningStartMinute: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.EVENING_START_MINUTE] ?: MedicineSettings.DEFAULT_EVENING_START_MINUTE
    }

    val eveningEndHour: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.EVENING_END_HOUR] ?: MedicineSettings.DEFAULT_EVENING_END_HOUR
    }

    val eveningEndMinute: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MedicineSettings.EVENING_END_MINUTE] ?: MedicineSettings.DEFAULT_EVENING_END_MINUTE
    }

    // Save methods
    suspend fun saveMovementThreshold(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[MedicineSettings.MOVEMENT_THRESHOLD] = value
        }
    }

    suspend fun saveCooldownTime(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[MedicineSettings.COOLDOWN_TIME] = value
        }
    }

    suspend fun saveMorningEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MedicineSettings.MORNING_ENABLED] = enabled
        }
    }

    suspend fun saveMorningTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        context.dataStore.edit { preferences ->
            preferences[MedicineSettings.MORNING_START_HOUR] = startHour
            preferences[MedicineSettings.MORNING_START_MINUTE] = startMinute
            preferences[MedicineSettings.MORNING_END_HOUR] = endHour
            preferences[MedicineSettings.MORNING_END_MINUTE] = endMinute
        }
    }

    suspend fun saveAfternoonEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MedicineSettings.AFTERNOON_ENABLED] = enabled
        }
    }

    suspend fun saveAfternoonTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        context.dataStore.edit { preferences ->
            preferences[MedicineSettings.AFTERNOON_START_HOUR] = startHour
            preferences[MedicineSettings.AFTERNOON_START_MINUTE] = startMinute
            preferences[MedicineSettings.AFTERNOON_END_HOUR] = endHour
            preferences[MedicineSettings.AFTERNOON_END_MINUTE] = endMinute
        }
    }

    suspend fun saveEveningEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MedicineSettings.EVENING_ENABLED] = enabled
        }
    }

    suspend fun saveEveningTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        context.dataStore.edit { preferences ->
            preferences[MedicineSettings.EVENING_START_HOUR] = startHour
            preferences[MedicineSettings.EVENING_START_MINUTE] = startMinute
            preferences[MedicineSettings.EVENING_END_HOUR] = endHour
            preferences[MedicineSettings.EVENING_END_MINUTE] = endMinute
        }
    }
}
