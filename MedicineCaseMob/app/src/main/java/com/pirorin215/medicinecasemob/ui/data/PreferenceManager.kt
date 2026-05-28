package com.pirorin215.medicinecasemob.ui.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferenceManager @Inject constructor(private val context: Context) {

    private object PreferencesKeys {
        // New Minute-based Storage
        val MORNING_START_MINUTE = intPreferencesKey("morning_start_minute_v2")
        val AFTERNOON_START_MINUTE = intPreferencesKey("afternoon_start_minute_v2")
        val EVENING_START_MINUTE = intPreferencesKey("evening_start_minute_v2")
        val DAY_END_MINUTE = intPreferencesKey("day_end_minute_v2")

        // Reminder Times (minute-based)
        val MORNING_REMINDER_MINUTE = intPreferencesKey("morning_reminder_minute_v2")
        val AFTERNOON_REMINDER_MINUTE = intPreferencesKey("afternoon_reminder_minute_v2")
        val EVENING_REMINDER_MINUTE = intPreferencesKey("evening_reminder_minute_v2")

        // Schedule Enabled Flags
        val MORNING_ENABLED = booleanPreferencesKey("morning_enabled")
        val AFTERNOON_ENABLED = booleanPreferencesKey("afternoon_enabled")
        val EVENING_ENABLED = booleanPreferencesKey("evening_enabled")

        // Legacy Keys (for migration)
        val LEGACY_MORNING_START_HOUR = intPreferencesKey("morning_start_hour")
        val LEGACY_MORNING_START_MINUTE = intPreferencesKey("legacy_morning_start_minute")
        val LEGACY_MORNING_END_HOUR = intPreferencesKey("morning_end_hour")
        val LEGACY_MORNING_END_MINUTE = intPreferencesKey("legacy_morning_end_minute")
        val LEGACY_AFTERNOON_START_HOUR = intPreferencesKey("afternoon_start_hour")
        val LEGACY_AFTERNOON_START_MINUTE = intPreferencesKey("legacy_afternoon_start_minute")
        val LEGACY_AFTERNOON_END_HOUR = intPreferencesKey("afternoon_end_hour")
        val LEGACY_AFTERNOON_END_MINUTE = intPreferencesKey("legacy_afternoon_end_minute")
        val LEGACY_EVENING_START_HOUR = intPreferencesKey("evening_start_hour")
        val LEGACY_EVENING_START_MINUTE = intPreferencesKey("legacy_evening_start_minute")
        val LEGACY_EVENING_END_HOUR = intPreferencesKey("evening_end_hour")
        val LEGACY_EVENING_END_MINUTE = intPreferencesKey("legacy_evening_end_minute")
        val LEGACY_MORNING_REMINDER_HOUR = intPreferencesKey("morning_reminder_hour")
        val LEGACY_MORNING_REMINDER_MINUTE = intPreferencesKey("legacy_morning_reminder_minute")
        val LEGACY_AFTERNOON_REMINDER_HOUR = intPreferencesKey("afternoon_reminder_hour")
        val LEGACY_AFTERNOON_REMINDER_MINUTE = intPreferencesKey("legacy_afternoon_reminder_minute")
        val LEGACY_EVENING_REMINDER_HOUR = intPreferencesKey("evening_reminder_hour")
        val LEGACY_EVENING_REMINDER_MINUTE = intPreferencesKey("legacy_evening_reminder_minute")

        // Notification Settings
        val ONLY_NOTIFY_WHEN_BLE_CONNECTED = booleanPreferencesKey("only_notify_when_ble_connected")
        val NOTIFICATION_INTERVAL_MINUTES = intPreferencesKey("notification_interval_minutes")
        val LAST_NOTIFICATION_TIMESTAMP = longPreferencesKey("last_notification_timestamp")

        // Notification Flags (End of slot)
        val NOTIFIED_AT_END_OF_MORNING = booleanPreferencesKey("notified_at_end_of_morning")
        val NOTIFIED_AT_END_OF_AFTERNOON = booleanPreferencesKey("notified_at_end_of_afternoon")
        val NOTIFIED_AT_END_OF_EVENING = booleanPreferencesKey("notified_at_end_of_evening")

        // Notification Flags (In-slot)
        val NOTIFIED_IN_SLOT_MORNING = booleanPreferencesKey("notified_in_slot_morning")
        val NOTIFIED_IN_SLOT_AFTERNOON = booleanPreferencesKey("notified_in_slot_afternoon")
        val NOTIFIED_IN_SLOT_EVENING = booleanPreferencesKey("notified_in_slot_evening")

        // Detection Settings
        val MOVEMENT_THRESHOLD = floatPreferencesKey("movement_threshold")
        val COOLDOWN_TIME = longPreferencesKey("cooldown_time")

        // UI Settings
        val UI_FONT_SIZE_SCALE = floatPreferencesKey("ui_font_size_scale")

        // Connection Settings
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
    }

    val settingsFlow: Flow<AppSettingsData> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            // Try to get new minute-based values
            val morningStart = preferences[PreferencesKeys.MORNING_START_MINUTE]
            val afternoonStart = preferences[PreferencesKeys.AFTERNOON_START_MINUTE]
            val eveningStart = preferences[PreferencesKeys.EVENING_START_MINUTE]
            val dayEnd = preferences[PreferencesKeys.DAY_END_MINUTE]

            // Check if migration is needed (old data exists but new data doesn't)
            val needsMigration = morningStart == null || afternoonStart == null ||
                                eveningStart == null || dayEnd == null

            if (needsMigration) {
                // Migrate from legacy hour/minute format to minute-based format
                val legacyMorningStartHour = preferences[PreferencesKeys.LEGACY_MORNING_START_HOUR] ?: 8
                val legacyMorningStartMin = preferences[PreferencesKeys.LEGACY_MORNING_START_MINUTE] ?: 0
                val legacyMorningEndHour = preferences[PreferencesKeys.LEGACY_MORNING_END_HOUR] ?: 11
                val legacyMorningEndMin = preferences[PreferencesKeys.LEGACY_MORNING_END_MINUTE] ?: 0

                val legacyAfternoonStartHour = preferences[PreferencesKeys.LEGACY_AFTERNOON_START_HOUR] ?: 11
                val legacyAfternoonStartMin = preferences[PreferencesKeys.LEGACY_AFTERNOON_START_MINUTE] ?: 0
                val legacyAfternoonEndHour = preferences[PreferencesKeys.LEGACY_AFTERNOON_END_HOUR] ?: 17
                val legacyAfternoonEndMin = preferences[PreferencesKeys.LEGACY_AFTERNOON_END_MINUTE] ?: 0

                val legacyEveningStartHour = preferences[PreferencesKeys.LEGACY_EVENING_START_HOUR] ?: 17
                val legacyEveningStartMin = preferences[PreferencesKeys.LEGACY_EVENING_START_MINUTE] ?: 0
                val legacyEveningEndHour = preferences[PreferencesKeys.LEGACY_EVENING_END_HOUR] ?: 23
                val legacyEveningEndMin = preferences[PreferencesKeys.LEGACY_EVENING_END_MINUTE] ?: 0

                val migratedMorningStart = legacyMorningStartHour * 60 + legacyMorningStartMin
                val migratedAfternoonStart = legacyAfternoonStartHour * 60 + legacyAfternoonStartMin
                val migratedEveningStart = legacyEveningStartHour * 60 + legacyEveningStartMin
                val migratedDayEnd = legacyEveningEndHour * 60 + legacyEveningEndMin

                AppSettingsData(
                    morningEnabled = preferences[PreferencesKeys.MORNING_ENABLED] ?: true,
                    morningReminderMinute = preferences[PreferencesKeys.MORNING_REMINDER_MINUTE] ?:
                        (preferences[PreferencesKeys.LEGACY_MORNING_REMINDER_HOUR] ?: 9) * 60 +
                        (preferences[PreferencesKeys.LEGACY_MORNING_REMINDER_MINUTE] ?: 0),

                    afternoonEnabled = preferences[PreferencesKeys.AFTERNOON_ENABLED] ?: true,
                    afternoonReminderMinute = preferences[PreferencesKeys.AFTERNOON_REMINDER_MINUTE] ?:
                        (preferences[PreferencesKeys.LEGACY_AFTERNOON_REMINDER_HOUR] ?: 13) * 60 +
                        (preferences[PreferencesKeys.LEGACY_AFTERNOON_REMINDER_MINUTE] ?: 0),

                    eveningEnabled = preferences[PreferencesKeys.EVENING_ENABLED] ?: true,
                    eveningReminderMinute = preferences[PreferencesKeys.EVENING_REMINDER_MINUTE] ?:
                        (preferences[PreferencesKeys.LEGACY_EVENING_REMINDER_HOUR] ?: 20) * 60 +
                        (preferences[PreferencesKeys.LEGACY_EVENING_REMINDER_MINUTE] ?: 0),

                    morningStartMinute = migratedMorningStart,
                    afternoonStartMinute = migratedAfternoonStart,
                    eveningStartMinute = migratedEveningStart,
                    dayEndMinute = migratedDayEnd,

                    onlyNotifyWhenBleConnected = preferences[PreferencesKeys.ONLY_NOTIFY_WHEN_BLE_CONNECTED] ?: false,
                    notificationIntervalMinutes = preferences[PreferencesKeys.NOTIFICATION_INTERVAL_MINUTES] ?: 60,
                    lastNotificationTimestamp = preferences[PreferencesKeys.LAST_NOTIFICATION_TIMESTAMP]?.toInt() ?: 0,

                    movementThreshold = preferences[PreferencesKeys.MOVEMENT_THRESHOLD] ?: 70.0f,
                    cooldownTime = preferences[PreferencesKeys.COOLDOWN_TIME] ?: 30000L,

                    uiFontSizeScale = preferences[PreferencesKeys.UI_FONT_SIZE_SCALE] ?: 1.2f,

                    notifiedAtEndOfMorning = preferences[PreferencesKeys.NOTIFIED_AT_END_OF_MORNING] ?: false,
                    notifiedAtEndOfAfternoon = preferences[PreferencesKeys.NOTIFIED_AT_END_OF_AFTERNOON] ?: false,
                    notifiedAtEndOfEvening = preferences[PreferencesKeys.NOTIFIED_AT_END_OF_EVENING] ?: false,

                    morningNotifiedInSlot = preferences[PreferencesKeys.NOTIFIED_IN_SLOT_MORNING] ?: false,
                    afternoonNotifiedInSlot = preferences[PreferencesKeys.NOTIFIED_IN_SLOT_AFTERNOON] ?: false,
                    eveningNotifiedInSlot = preferences[PreferencesKeys.NOTIFIED_IN_SLOT_EVENING] ?: false,

                    lastDeviceAddress = preferences[PreferencesKeys.LAST_DEVICE_ADDRESS]
                )
            } else {
                // Use new minute-based values
                AppSettingsData(
                    morningEnabled = preferences[PreferencesKeys.MORNING_ENABLED] ?: true,
                    morningReminderMinute = preferences[PreferencesKeys.MORNING_REMINDER_MINUTE] ?: 9 * 60,
                    morningStartMinute = morningStart,

                    afternoonEnabled = preferences[PreferencesKeys.AFTERNOON_ENABLED] ?: true,
                    afternoonReminderMinute = preferences[PreferencesKeys.AFTERNOON_REMINDER_MINUTE] ?: 13 * 60,
                    afternoonStartMinute = afternoonStart,

                    eveningEnabled = preferences[PreferencesKeys.EVENING_ENABLED] ?: true,
                    eveningReminderMinute = preferences[PreferencesKeys.EVENING_REMINDER_MINUTE] ?: 20 * 60,
                    eveningStartMinute = eveningStart,

                    dayEndMinute = dayEnd,

                    onlyNotifyWhenBleConnected = preferences[PreferencesKeys.ONLY_NOTIFY_WHEN_BLE_CONNECTED] ?: false,
                    notificationIntervalMinutes = preferences[PreferencesKeys.NOTIFICATION_INTERVAL_MINUTES] ?: 60,
                    lastNotificationTimestamp = preferences[PreferencesKeys.LAST_NOTIFICATION_TIMESTAMP]?.toInt() ?: 0,

                    movementThreshold = preferences[PreferencesKeys.MOVEMENT_THRESHOLD] ?: 70.0f,
                    cooldownTime = preferences[PreferencesKeys.COOLDOWN_TIME] ?: 30000L,

                    uiFontSizeScale = preferences[PreferencesKeys.UI_FONT_SIZE_SCALE] ?: 1.2f,

                    notifiedAtEndOfMorning = preferences[PreferencesKeys.NOTIFIED_AT_END_OF_MORNING] ?: false,
                    notifiedAtEndOfAfternoon = preferences[PreferencesKeys.NOTIFIED_AT_END_OF_AFTERNOON] ?: false,
                    notifiedAtEndOfEvening = preferences[PreferencesKeys.NOTIFIED_AT_END_OF_EVENING] ?: false,

                    morningNotifiedInSlot = preferences[PreferencesKeys.NOTIFIED_IN_SLOT_MORNING] ?: false,
                    afternoonNotifiedInSlot = preferences[PreferencesKeys.NOTIFIED_IN_SLOT_AFTERNOON] ?: false,
                    eveningNotifiedInSlot = preferences[PreferencesKeys.NOTIFIED_IN_SLOT_EVENING] ?: false,

                    lastDeviceAddress = preferences[PreferencesKeys.LAST_DEVICE_ADDRESS]
                )
            }
        }

    suspend fun updateSettings(settings: AppSettingsData) {
        context.dataStore.edit { preferences ->
            // Save new minute-based values
            preferences[PreferencesKeys.MORNING_ENABLED] = settings.morningEnabled
            preferences[PreferencesKeys.MORNING_START_MINUTE] = settings.morningStartMinute
            preferences[PreferencesKeys.MORNING_REMINDER_MINUTE] = settings.morningReminderMinute

            preferences[PreferencesKeys.AFTERNOON_ENABLED] = settings.afternoonEnabled
            preferences[PreferencesKeys.AFTERNOON_START_MINUTE] = settings.afternoonStartMinute
            preferences[PreferencesKeys.AFTERNOON_REMINDER_MINUTE] = settings.afternoonReminderMinute

            preferences[PreferencesKeys.EVENING_ENABLED] = settings.eveningEnabled
            preferences[PreferencesKeys.EVENING_START_MINUTE] = settings.eveningStartMinute
            preferences[PreferencesKeys.EVENING_REMINDER_MINUTE] = settings.eveningReminderMinute

            preferences[PreferencesKeys.DAY_END_MINUTE] = settings.dayEndMinute

            preferences[PreferencesKeys.ONLY_NOTIFY_WHEN_BLE_CONNECTED] = settings.onlyNotifyWhenBleConnected
            preferences[PreferencesKeys.NOTIFICATION_INTERVAL_MINUTES] = settings.notificationIntervalMinutes
            preferences[PreferencesKeys.LAST_NOTIFICATION_TIMESTAMP] = settings.lastNotificationTimestamp.toLong()

            preferences[PreferencesKeys.MOVEMENT_THRESHOLD] = settings.movementThreshold
            preferences[PreferencesKeys.COOLDOWN_TIME] = settings.cooldownTime

            preferences[PreferencesKeys.UI_FONT_SIZE_SCALE] = settings.uiFontSizeScale

            preferences[PreferencesKeys.NOTIFIED_AT_END_OF_MORNING] = settings.notifiedAtEndOfMorning
            preferences[PreferencesKeys.NOTIFIED_AT_END_OF_AFTERNOON] = settings.notifiedAtEndOfAfternoon
            preferences[PreferencesKeys.NOTIFIED_AT_END_OF_EVENING] = settings.notifiedAtEndOfEvening

            preferences[PreferencesKeys.NOTIFIED_IN_SLOT_MORNING] = settings.morningNotifiedInSlot
            preferences[PreferencesKeys.NOTIFIED_IN_SLOT_AFTERNOON] = settings.afternoonNotifiedInSlot
            preferences[PreferencesKeys.NOTIFIED_IN_SLOT_EVENING] = settings.eveningNotifiedInSlot

            if (settings.lastDeviceAddress != null) {
                preferences[PreferencesKeys.LAST_DEVICE_ADDRESS] = settings.lastDeviceAddress
            } else {
                preferences.remove(PreferencesKeys.LAST_DEVICE_ADDRESS)
            }
        }
    }

    suspend fun updateLastDeviceAddress(address: String?) {
        context.dataStore.edit { preferences ->
            if (address != null) {
                preferences[PreferencesKeys.LAST_DEVICE_ADDRESS] = address
            } else {
                preferences.remove(PreferencesKeys.LAST_DEVICE_ADDRESS)
            }
        }
    }

    suspend fun updateEndNotificationFlags(
        morning: Boolean,
        afternoon: Boolean,
        evening: Boolean
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFIED_AT_END_OF_MORNING] = morning
            preferences[PreferencesKeys.NOTIFIED_AT_END_OF_AFTERNOON] = afternoon
            preferences[PreferencesKeys.NOTIFIED_AT_END_OF_EVENING] = evening
        }
    }

    suspend fun updateInSlotNotificationFlags(
        morning: Boolean,
        afternoon: Boolean,
        evening: Boolean
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFIED_IN_SLOT_MORNING] = morning
            preferences[PreferencesKeys.NOTIFIED_IN_SLOT_AFTERNOON] = afternoon
            preferences[PreferencesKeys.NOTIFIED_IN_SLOT_EVENING] = evening
        }
    }

    suspend fun updateLastNotificationTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_NOTIFICATION_TIMESTAMP] = timestamp
        }
    }
}
