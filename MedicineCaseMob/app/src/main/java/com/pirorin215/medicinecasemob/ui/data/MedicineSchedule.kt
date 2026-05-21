package com.pirorin215.medicinecasemob.ui.data

data class MedicineSchedule(
    val id: Int,  // 0: Morning, 1: Afternoon, 2: Evening
    val enabled: Boolean,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val reminderHour: Int,
    val reminderMinute: Int
)

enum class ScheduleType(val id: Int, val displayName: String, val defaultStartHour: Int, val defaultStartMinute: Int, val defaultEndHour: Int, val defaultEndMinute: Int) {
    MORNING(0, "朝", 8, 0, 11, 0),
    AFTERNOON(1, "昼", 12, 0, 17, 0),
    EVENING(2, "夜", 19, 0, 22, 0);

    companion object {
        fun fromId(id: Int): ScheduleType? = entries.find { it.id == id }
    }
}
