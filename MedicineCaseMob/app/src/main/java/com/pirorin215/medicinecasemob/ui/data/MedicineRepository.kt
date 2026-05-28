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
     * 今日の0時0分のUnix秒タイムスタンプを取得するヘルパー。
     * 複数箇所で重複していたCalendar初期化パターンを統一。
     */
    fun getTodayStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / 1000
    }

    /**
     * Ensures a record for today exists in the database.
     * If not, creates one with current enabled flags from settings.
     * Returns today's record (existing or newly created).
     */
    suspend fun ensureTodayRecordExists(): MedicineIntakeRecord {
        val todayStart = getTodayStartTimestamp()

        val existing = medicineDao.getIntakeRecordByDateSync(todayStart)
        if (existing != null) return existing

        // Create new record with current settings
        val settings = preferenceManager.settingsFlow.first()
        val newRecord = MedicineIntakeRecord(
            date = todayStart,
            morningEnabled = settings.morningEnabled,
            afternoonEnabled = settings.afternoonEnabled,
            eveningEnabled = settings.eveningEnabled
        )
        medicineDao.insertIntakeRecord(newRecord)
        return medicineDao.getIntakeRecordByDateSync(todayStart) ?: newRecord
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
                startHour = settings.morningStartMinute / 60,
                startMinute = settings.morningStartMinute % 60,
                endHour = settings.afternoonStartMinute / 60,
                endMinute = settings.afternoonStartMinute % 60,
                reminderHour = settings.morningReminderMinute / 60,
                reminderMinute = settings.morningReminderMinute % 60
            ),
            MedicineSchedule(
                id = ScheduleType.AFTERNOON.id,
                enabled = settings.afternoonEnabled,
                startHour = settings.afternoonStartMinute / 60,
                startMinute = settings.afternoonStartMinute % 60,
                endHour = settings.eveningStartMinute / 60,
                endMinute = settings.eveningStartMinute % 60,
                reminderHour = settings.afternoonReminderMinute / 60,
                reminderMinute = settings.afternoonReminderMinute % 60
            ),
            MedicineSchedule(
                id = ScheduleType.EVENING.id,
                enabled = settings.eveningEnabled,
                startHour = settings.eveningStartMinute / 60,
                startMinute = settings.eveningStartMinute % 60,
                endHour = settings.dayEndMinute / 60,
                endMinute = settings.dayEndMinute % 60,
                reminderHour = settings.eveningReminderMinute / 60,
                reminderMinute = settings.eveningReminderMinute % 60
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

    /**
     * Check if an intake with the given mcu timestamp already exists in any record.
     * Used for duplicate detection instead of in-memory set.
     */
    suspend fun isMcuTimestampRecorded(mcuTimestamp: Long): Boolean {
        if (mcuTimestamp == 0L) return false
        return medicineDao.findRecordByMcuTime(mcuTimestamp) != null
    }

    suspend fun clearIntakeRecordsByIds(ids: List<Long>) {
        ids.forEach { recordId ->
            medicineDao.clearAllIntakes(recordId)
        }
    }
}
