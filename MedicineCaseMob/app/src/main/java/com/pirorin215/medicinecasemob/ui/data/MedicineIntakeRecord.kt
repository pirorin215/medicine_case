package com.pirorin215.medicinecasemob.ui.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intake_records")
data class MedicineIntakeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,  // Unix timestamp (day precision)
    val morningTaken: Boolean = false,
    val morningTime: Long = 0L,
    val afternoonTaken: Boolean = false,
    val afternoonTime: Long = 0L,
    val eveningTaken: Boolean = false,
    val eveningTime: Long = 0L
)
