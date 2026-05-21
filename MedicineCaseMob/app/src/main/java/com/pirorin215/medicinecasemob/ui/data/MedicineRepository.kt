package com.pirorin215.medicinecasemob.ui.data

import kotlinx.coroutines.flow.Flow

class MedicineRepository(private val medicineDao: MedicineDao) {

    // Schedule operations
    fun getAllSchedules(): Flow<List<MedicineSchedule>> = medicineDao.getAllSchedules()

    suspend fun getAllSchedulesSync(): List<MedicineSchedule> = medicineDao.getAllSchedulesSync()

    fun getScheduleById(id: Int): Flow<MedicineSchedule?> = medicineDao.getScheduleById(id)

    suspend fun insertSchedule(schedule: MedicineSchedule) = medicineDao.insertSchedule(schedule)

    suspend fun updateSchedule(schedule: MedicineSchedule) = medicineDao.updateSchedule(schedule)

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

    // Detection settings operations
    fun getDetectionSettings(): Flow<DetectionSettings?> =
        medicineDao.getDetectionSettings()

    suspend fun insertDetectionSettings(settings: DetectionSettings) =
        medicineDao.insertDetectionSettings(settings)

    suspend fun updateDetectionSettings(settings: DetectionSettings) =
        medicineDao.updateDetectionSettings(settings)
}
