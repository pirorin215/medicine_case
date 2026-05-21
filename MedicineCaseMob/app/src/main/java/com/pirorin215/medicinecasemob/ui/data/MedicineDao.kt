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
}
