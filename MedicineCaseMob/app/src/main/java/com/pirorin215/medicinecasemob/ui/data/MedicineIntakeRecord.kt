package com.pirorin215.medicinecasemob.ui.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pirorin215.medicinecasemob.ui.data.ScheduleType

@Entity(tableName = "intake_records")
data class MedicineIntakeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,  // Unix timestamp (day precision)
    val morningTaken: Boolean = false,
    val morningTime: Long = 0L,           // マイコンからの時刻 (Unix timestamp in seconds)
    val morningReceivedTime: Long = 0L,   // スマホ受信時刻 (Unix timestamp in seconds)
    val morningEnabled: Boolean = true,
    val afternoonTaken: Boolean = false,
    val afternoonTime: Long = 0L,         // マイコンからの時刻 (Unix timestamp in seconds)
    val afternoonReceivedTime: Long = 0L, // スマホ受信時刻 (Unix timestamp in seconds)
    val afternoonEnabled: Boolean = true,
    val eveningTaken: Boolean = false,
    val eveningTime: Long = 0L,          // マイコンからの時刻 (Unix timestamp in seconds)
    val eveningReceivedTime: Long = 0L,  // スマホ受信時刻 (Unix timestamp in seconds)
    val eveningEnabled: Boolean = true
) {
    /** 指定した枠が服薬済みかどうか */
    fun isTaken(scheduleType: ScheduleType): Boolean = when (scheduleType) {
        ScheduleType.MORNING -> morningTaken
        ScheduleType.AFTERNOON -> afternoonTaken
        ScheduleType.EVENING -> eveningTaken
    }

    /** 指定した枠に服薬記録を反映した新しいレコードを返す */
    fun withTaken(scheduleType: ScheduleType, mcuTimestamp: Long, phoneTimestamp: Long): MedicineIntakeRecord =
        when (scheduleType) {
            ScheduleType.MORNING -> copy(
                morningTaken = true,
                morningTime = mcuTimestamp,
                morningReceivedTime = phoneTimestamp
            )
            ScheduleType.AFTERNOON -> copy(
                afternoonTaken = true,
                afternoonTime = mcuTimestamp,
                afternoonReceivedTime = phoneTimestamp
            )
            ScheduleType.EVENING -> copy(
                eveningTaken = true,
                eveningTime = mcuTimestamp,
                eveningReceivedTime = phoneTimestamp
            )
        }
}
