package com.pirorin215.medicinecasemob.ui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    // Intake record operations
    @Query("SELECT * FROM intake_records ORDER BY date DESC")
    fun getAllIntakeRecords(): Flow<List<MedicineIntakeRecord>>

    @Query("SELECT * FROM intake_records WHERE date = :date LIMIT 1")
    fun getIntakeRecordByDate(date: Long): Flow<MedicineIntakeRecord?>

    @Query("SELECT * FROM intake_records WHERE date = :date LIMIT 1")
    suspend fun getIntakeRecordByDateSync(date: Long): MedicineIntakeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntakeRecord(record: MedicineIntakeRecord)

    @Update
    suspend fun updateIntakeRecord(record: MedicineIntakeRecord)

    @Query("DELETE FROM intake_records")
    suspend fun deleteAllIntakeRecords()

    @Query("DELETE FROM intake_records WHERE id IN (:ids)")
    suspend fun deleteIntakeRecordsByIds(ids: List<Long>)

    @Query("DELETE FROM intake_records WHERE date < :thresholdDate")
    suspend fun deleteOldRecords(thresholdDate: Long)

    // Check if an intake with the given mcu timestamp already exists
    @Query("SELECT * FROM intake_records WHERE morningTime = :mcuTimestamp OR afternoonTime = :mcuTimestamp OR eveningTime = :mcuTimestamp LIMIT 1")
    suspend fun findRecordByMcuTime(mcuTimestamp: Long): MedicineIntakeRecord?

    @Query("UPDATE intake_records SET morningTaken = 0, morningTime = 0, morningReceivedTime = 0, afternoonTaken = 0, afternoonTime = 0, afternoonReceivedTime = 0, eveningTaken = 0, eveningTime = 0, eveningReceivedTime = 0 WHERE id = :recordId")
    suspend fun clearAllIntakes(recordId: Long)
}
