package com.pirorin215.medicinecasemob.ui.data

import com.pirorin215.medicinecasemob.util.LogManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

class MedicineRepository(
    private val medicineDao: MedicineDao,
    private val preferenceManager: PreferenceManager
) {

    companion object {
        private const val TAG = "MedicineRepository"
    }

    private val logManager = LogManager.getInstance()

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

    /**
     * BLEから受信した服薬イベント(mcuTimestamp)をDBに記録する。
     * マイコン時刻による重複排除・活動時間外の除外・既服薬のスキップを行う。
     * MedicineBleScanService（push/poll）と NotificationScheduler（安全網）の
     * 両方から呼ばれる、服薬記録の単一経路。
     *
     * @return 新規に服薬記録を書き込んだ場合はその ScheduleType、それ以外は null
     */
    suspend fun recordIntakeEvent(mcuTimestamp: Long): ScheduleType? {
        // DBレベルの重複排除: 同じマイコン時刻が既に記録済みなら無視
        if (isMcuTimestampRecorded(mcuTimestamp)) {
            logManager.d(TAG, "Duplicate intake event ignored (already in DB): $mcuTimestamp")
            return null
        }

        val phoneTimestamp = System.currentTimeMillis() / 1000
        val effectiveTimestamp = if (mcuTimestamp > 0) mcuTimestamp else phoneTimestamp

        val settings = preferenceManager.settingsFlow.first()
        val schedules = getSchedulesFromSettings(settings)
        val scheduleType = determineScheduleTypeForTimestamp(effectiveTimestamp, schedules)

        if (scheduleType == null) {
            logManager.d(TAG, "Ignoring intake: no valid schedule for current time (outside activity hours)")
            return null
        }

        val todayRecord = ensureTodayRecordExists()

        if (todayRecord.isTaken(scheduleType)) {
            logManager.d(TAG, "Ignoring intake: already recorded for $scheduleType")
            return null
        }

        val updatedRecord = todayRecord.withTaken(scheduleType, mcuTimestamp, phoneTimestamp)
        insertIntakeRecord(updatedRecord)
        logManager.d(TAG, "Intake recorded: $scheduleType mcu_time=$mcuTimestamp, phone_received=$phoneTimestamp")
        return scheduleType
    }

    /**
     * 時刻ベースのシンプルな枠判定。
     * 昨日以前の服薬検知は無視し、活動時間内の時刻から枠(朝/昼/夜)を決定する。
     */
    private fun determineScheduleTypeForTimestamp(
        timestamp: Long,
        schedules: List<MedicineSchedule>
    ): ScheduleType? {
        val currentCalendar = Calendar.getInstance()
        val currentDay = currentCalendar.get(Calendar.DAY_OF_YEAR)
        val currentYear = currentCalendar.get(Calendar.YEAR)

        val intakeCalendar = Calendar.getInstance()
        intakeCalendar.timeInMillis = timestamp * 1000
        val intakeDay = intakeCalendar.get(Calendar.DAY_OF_YEAR)
        val intakeYear = intakeCalendar.get(Calendar.YEAR)

        if (intakeYear != currentYear || intakeDay != currentDay) {
            logManager.d(TAG, "Ignoring intake: different date (intake: $intakeYear/$intakeDay, current: $currentYear/$currentDay)")
            return null
        }

        val hour = intakeCalendar.get(Calendar.HOUR_OF_DAY)

        val morningSchedule = schedules.find { it.id == ScheduleType.MORNING.id }
        val afternoonSchedule = schedules.find { it.id == ScheduleType.AFTERNOON.id }
        val eveningSchedule = schedules.find { it.id == ScheduleType.EVENING.id }

        val morningEndHour = morningSchedule?.endHour ?: 11
        val afternoonEndHour = afternoonSchedule?.endHour ?: 17
        val eveningEndHour = eveningSchedule?.endHour ?: 23
        val activityStartHour = morningSchedule?.startHour ?: 7

        return when {
            hour < activityStartHour -> null  // 活動前
            hour < morningEndHour -> ScheduleType.MORNING
            hour < afternoonEndHour -> ScheduleType.AFTERNOON
            hour < eveningEndHour -> ScheduleType.EVENING
            else -> null  // 活動後
        }
    }

    suspend fun clearIntakeRecordsByIds(ids: List<Long>) {
        ids.forEach { recordId ->
            medicineDao.clearAllIntakes(recordId)
        }
    }

    /**
     * 真夜中（0時）に通知フラグをリセットする。
     * MedicineBleScanService と NotificationScheduler の両方で使用。
     */
    suspend fun resetDailyNotificationFlags() {
        updateEndNotificationFlags(morning = false, afternoon = false, evening = false)
        updateInSlotNotificationFlags(morning = false, afternoon = false, evening = false)
    }
}
