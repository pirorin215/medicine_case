package com.pirorin215.medicinecasemob.ui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

class MedicineRepository(
    private val medicineDao: MedicineDao,
    private val preferenceManager: PreferenceManager
) {

    // Settings (DataStore)
    val settingsFlow: Flow<AppSettingsData> = preferenceManager.settingsFlow

    /**
     * Ensures a record for today exists in the database.
     * If not, creates one with current enabled flags from settings.
     */
    suspend fun ensureTodayRecordExists(): MedicineIntakeRecord {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis / 1000

        val existing = medicineDao.getIntakeRecordByDateSync(todayStart)
        if (existing != null) return existing

        val settings = preferenceManager.settingsFlow.first()
        val newRecord = MedicineIntakeRecord(
            date = todayStart,
            morningEnabled = settings.morningEnabled,
            afternoonEnabled = settings.afternoonEnabled,
            eveningEnabled = settings.eveningEnabled
        )
        medicineDao.insertIntakeRecord(newRecord)
        return newRecord
    }

    suspend fun updateSettings(settings: AppSettingsData) =
        preferenceManager.updateSettings(settings)

    suspend fun updateEndNotificationFlags(morning: Boolean, afternoon: Boolean, evening: Boolean) =
        preferenceManager.updateEndNotificationFlags(morning, afternoon, evening)

    suspend fun updateInSlotNotificationFlags(morning: Boolean, afternoon: Boolean, evening: Boolean) =
        preferenceManager.updateInSlotNotificationFlags(morning, afternoon, evening)

    suspend fun updateLastNotificationTimestamp(timestamp: Long) =
        preferenceManager.updateLastNotificationTimestamp(timestamp)

    suspend fun updateLastDeviceAddress(address: String?) =
        preferenceManager.updateLastDeviceAddress(address)

    /**
     * Helper to get schedules as a list from settings
     */
    fun getSchedulesFromSettings(settings: AppSettingsData): List<MedicineSchedule> {
        return listOf(
            MedicineSchedule(
                id = ScheduleType.MORNING.id,
                enabled = settings.morningEnabled,
                startHour = settings.morningStartHour,
                startMinute = settings.morningStartMinute,
                endHour = settings.morningEndHour,
                endMinute = settings.morningEndMinute,
                reminderHour = settings.morningReminderHour,
                reminderMinute = settings.morningReminderMinute
            ),
            MedicineSchedule(
                id = ScheduleType.AFTERNOON.id,
                enabled = settings.afternoonEnabled,
                startHour = settings.afternoonStartHour,
                startMinute = settings.afternoonStartMinute,
                endHour = settings.afternoonEndHour,
                endMinute = settings.afternoonEndMinute,
                reminderHour = settings.afternoonReminderHour,
                reminderMinute = settings.afternoonReminderMinute
            ),
            MedicineSchedule(
                id = ScheduleType.EVENING.id,
                enabled = settings.eveningEnabled,
                startHour = settings.eveningStartHour,
                startMinute = settings.eveningStartMinute,
                endHour = settings.eveningEndHour,
                endMinute = settings.eveningEndMinute,
                reminderHour = settings.eveningReminderHour,
                reminderMinute = settings.eveningReminderMinute
            )
        )
    }

    // Intake record operations
    fun getAllIntakeRecords(): Flow<List<MedicineIntakeRecord>> = medicineDao.getAllIntakeRecords()

    fun getIntakeRecordByDate(date: Long): Flow<MedicineIntakeRecord?> =
        medicineDao.getIntakeRecordByDate(date)

    suspend fun getIntakeRecordByDateSync(date: Long): MedicineIntakeRecord? =
        medicineDao.getIntakeRecordByDateSync(date)

    suspend fun insertIntakeRecord(record: MedicineIntakeRecord) =
        medicineDao.insertIntakeRecord(record)

    suspend fun updateIntakeRecord(record: MedicineIntakeRecord) =
        medicineDao.updateIntakeRecord(record)

    suspend fun deleteAllIntakeRecords() =
        medicineDao.deleteAllIntakeRecords()

    suspend fun deleteIntakeRecordsByIds(ids: List<Long>) =
        medicineDao.deleteIntakeRecordsByIds(ids)

    suspend fun deleteOldRecords(thresholdDate: Long) =
        medicineDao.deleteOldRecords(thresholdDate)
}
