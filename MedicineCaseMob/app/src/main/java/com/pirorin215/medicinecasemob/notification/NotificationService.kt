package com.pirorin215.medicinecasemob.notification

import android.app.NotificationManager
import android.content.Context
import com.pirorin215.medicinecasemob.util.LogManager
import androidx.core.app.NotificationCompat
import com.pirorin215.medicinecasemob.R
import com.pirorin215.medicinecasemob.ui.data.MedicineIntakeRecord
import com.pirorin215.medicinecasemob.ui.data.MedicineSchedule
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    private val logManager: com.pirorin215.medicinecasemob.util.LogManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "medicine_reminder"
    }

    /**
     * Check and send notifications for missed intakes.
     *
     * @param schedules All configured schedules
     * @param todayRecord Today's intake record (null if no record exists)
     * @param isConnectedToBle Whether BLE is connected to the device
     * @param forceNotification If true, send notification immediately (used when BLE connects)
     */
    fun checkAndNotifyMissedIntakes(
        schedules: List<MedicineSchedule>,
        todayRecord: MedicineIntakeRecord?,
        isConnectedToBle: Boolean,
        forceNotification: Boolean = false
    ) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentMinutes = currentHour * 60 + currentMinute

        logManager.d(TAG, "checkAndNotifyMissedIntakes: currentTime=$currentHour:$currentMinute, force=$forceNotification, bleConnected=$isConnectedToBle")

        // Sort schedules by end time
        val sortedSchedules = schedules.sortedBy { it.endHour * 60 + it.endMinute }

        for (schedule in sortedSchedules) {
            if (!schedule.enabled) continue

            val endMinutes = schedule.endHour * 60 + schedule.endMinute
            val scheduleType = ScheduleType.fromId(schedule.id) ?: continue

            // Check if already taken
            val alreadyTaken = when (scheduleType) {
                ScheduleType.MORNING -> todayRecord?.morningTaken == true
                ScheduleType.AFTERNOON -> todayRecord?.afternoonTaken == true
                ScheduleType.EVENING -> todayRecord?.eveningTaken == true
            }

            if (alreadyTaken) {
                logManager.d(TAG, "Schedule $scheduleType already taken, skipping")
                continue
            }

            // Determine if we should notify now
            val shouldNotify = if (forceNotification && isConnectedToBle) {
                // BLE connected: notify immediately for any missed intake
                logManager.d(TAG, "BLE connected, checking if $scheduleType is missed")
                currentMinutes >= endMinutes
            } else {
                // Scheduled check: notify at end time or 30-min intervals
                val timeSinceEnd = currentMinutes - endMinutes

                if (timeSinceEnd < 0) {
                    // Not yet reached end time
                    logManager.d(TAG, "Schedule $scheduleType: end time not reached yet")
                    false
                } else {
                    // Calculate which notification this would be
                    val timeSinceEndLong = timeSinceEnd.toLong()
                    val notificationNumber = (timeSinceEndLong / 30L).toInt() + 1

                    // Get next schedule's start time (if any)
                    val currentScheduleIndex = sortedSchedules.indexOf(schedule)
                    val nextSchedule = if (currentScheduleIndex < sortedSchedules.size - 1) {
                        sortedSchedules[currentScheduleIndex + 1]
                    } else {
                        null
                    }

                    val nextStartMinutes = nextSchedule?.let {
                        if (it.enabled) it.startHour * 60 + it.startMinute else null
                    }

                    // Check if we should notify
                    val shouldNotifyScheduled = if (notificationNumber == 1) {
                        // First notification: at end time
                        timeSinceEndLong == 0L
                    } else {
                        // Subsequent notifications: every 30 minutes
                        // BUT only if BLE was connected at some point (otherwise skip after 1st)
                        // This is handled by checking timeSinceEnd % 30 == 0
                        timeSinceEndLong % 30L == 0L
                    }

                    // Stop notifications if next schedule is about to start
                    val shouldStop = nextStartMinutes != null &&
                        (currentMinutes + 30) >= nextStartMinutes

                    if (shouldStop) {
                        logManager.d(TAG, "Schedule $scheduleType: next schedule starting soon, stopping notifications")
                        false
                    } else {
                        shouldNotifyScheduled
                    }
                }
            }

            if (shouldNotify) {
                // Check if this is the 2nd+ notification and BLE is not connected
                val timeSinceEnd = currentMinutes - endMinutes
                val isSecondOrLater = timeSinceEnd > 0

                if (isSecondOrLater && !isConnectedToBle) {
                    logManager.d(TAG, "Schedule $scheduleType: skipping 2nd+ notification (BLE not connected)")
                    continue
                }

                // Send notification
                sendNotification(scheduleType)
                logManager.d(TAG, "Notification sent for $scheduleType at $currentHour:$currentMinute")
            }
        }
    }

    private fun sendNotification(scheduleType: ScheduleType) {
        val notificationId = scheduleType.id * 1000 + Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${scheduleType.displayName}の服薬がまだです")
            .setContentText("お薬を忘れずに服用してください")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
}
