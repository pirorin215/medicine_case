package com.pirorin215.medicinecasemob.ui.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class MedicineSchedule(
    @PrimaryKey
    val id: Int,  // 0: Morning, 1: Afternoon, 2: Evening
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val taken: Boolean = false,
    val takenTimestamp: Long = 0L
)

data class MedicineScheduleConfig(
    val id: Int,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int
)

enum class ScheduleType(val id: Int, val displayName: String, val defaultHour: Int) {
    MORNING(0, "朝", 7),
    AFTERNOON(1, "昼", 12),
    EVENING(2, "夜", 19)
}
