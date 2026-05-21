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
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    private val logManager: com.pirorin215.medicinecasemob.util.LogManager,
    private val repository: com.pirorin215.medicinecasemob.ui.data.MedicineRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "medicine_reminder"
        private const val DEFAULT_NOTIFICATION_INTERVAL_MINUTES = 60
    }

    /**
     * Check and send notifications for missed intakes.
     *
     * @param schedules All configured schedules
     * @param todayRecord Today's intake record (null if no record exists)
     * @param isConnectedToBle Whether BLE is connected to the device
     * @param forceNotification If true, send notification immediately (used when BLE connects)
     */
    suspend fun checkAndNotifyMissedIntakes(
        schedules: List<MedicineSchedule>,
        todayRecord: MedicineIntakeRecord?,
        isConnectedToBle: Boolean,
        forceNotification: Boolean = false
    ) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentMinutes = currentHour * 60 + currentMinute
        val currentTimeSeconds = (System.currentTimeMillis() / 1000).toInt()

        // Load notification settings from repository
        val appSettings = repository.settingsFlow.first()
        val onlyNotifyWhenBle = appSettings.onlyNotifyWhenBleConnected
        val lastNotificationTime = appSettings.lastNotificationTimestamp
        val notificationIntervalMinutes = appSettings.notificationIntervalMinutes

        val secondsSinceLastNotification = currentTimeSeconds - lastNotificationTime

        // 1. Get all enabled schedules sorted by end time (descending)
        val enabledSchedules = schedules.filter { it.enabled }.sortedByDescending { it.endHour * 60 + it.endMinute }

        // --- NEW: In-slot (Intelligent) Notification Logic ---
        // Find if we are currently WITHIN an active slot
        val currentSlot = enabledSchedules.find {
            val startMinutes = it.startHour * 60 + it.startMinute
            val endMinutes = it.endHour * 60 + it.endMinute
            currentMinutes in startMinutes until endMinutes
        }

        if (currentSlot != null) {
            val scheduleType = ScheduleType.fromId(currentSlot.id) ?: return
            
            // Check if already taken
            val alreadyTaken = when (scheduleType) {
                ScheduleType.MORNING -> todayRecord?.morningTaken == true
                ScheduleType.AFTERNOON -> todayRecord?.afternoonTaken == true
                ScheduleType.EVENING -> todayRecord?.eveningTaken == true
            }

            if (!alreadyTaken) {
                val alreadyNotifiedInSlot = when (scheduleType) {
                    ScheduleType.MORNING -> appSettings.notifiedInSlotMorning
                    ScheduleType.AFTERNOON -> appSettings.notifiedInSlotAfternoon
                    ScheduleType.EVENING -> appSettings.notifiedInSlotEvening
                }

                if (!alreadyNotifiedInSlot) {
                    // Check triggers: BLE connect (force) OR Preferred Time reached
                    val preferredHour = when (scheduleType) {
                        ScheduleType.MORNING -> appSettings.morningReminderHour
                        ScheduleType.AFTERNOON -> appSettings.afternoonReminderHour
                        ScheduleType.EVENING -> appSettings.eveningReminderHour
                    }
                    val preferredMinute = when (scheduleType) {
                        ScheduleType.MORNING -> appSettings.morningReminderMinute
                        ScheduleType.AFTERNOON -> appSettings.afternoonReminderMinute
                        ScheduleType.EVENING -> appSettings.eveningReminderMinute
                    }
                    val preferredMinutes = preferredHour * 60 + preferredMinute

                    var shouldNotifyInSlot = false
                    if (forceNotification) {
                        shouldNotifyInSlot = true
                        logManager.d(TAG, "In-slot notification triggered: BLE connected (force)")
                    } else if (currentMinutes >= preferredMinutes) {
                        shouldNotifyInSlot = true
                        logManager.d(TAG, "In-slot notification triggered: Preferred time reached ($preferredHour:$preferredMinute)")
                    }

                    if (shouldNotifyInSlot) {
                        sendNotification(scheduleType, isInSlot = true)
                        
                        // Update in-slot notification flag
                        when (scheduleType) {
                            ScheduleType.MORNING -> repository.updateInSlotNotificationFlags(morning = true, afternoon = appSettings.notifiedInSlotAfternoon, evening = appSettings.notifiedInSlotEvening)
                            ScheduleType.AFTERNOON -> repository.updateInSlotNotificationFlags(morning = appSettings.notifiedInSlotMorning, afternoon = true, evening = appSettings.notifiedInSlotEvening)
                            ScheduleType.EVENING -> repository.updateInSlotNotificationFlags(morning = appSettings.notifiedInSlotMorning, afternoon = appSettings.notifiedInSlotAfternoon, evening = true)
                        }
                        repository.updateLastNotificationTimestamp(currentTimeSeconds.toLong())
                        return // Exit after sending in-slot notification
                    }
                }
            }
        }

        // --- EXISTING: End-of-slot (Deadline) Notification Logic ---
        // 2. Find the latest ended schedule to check
        val latestEndedSchedule = enabledSchedules
            .filter { currentMinutes >= (it.endHour * 60 + it.endMinute) }
            .maxByOrNull { it.endHour * 60 + it.endMinute }

        if (latestEndedSchedule == null) return

        val scheduleType = ScheduleType.fromId(latestEndedSchedule.id) ?: return

        // 3. Check if already taken
        val alreadyTaken = when (scheduleType) {
            ScheduleType.MORNING -> todayRecord?.morningTaken == true
            ScheduleType.AFTERNOON -> todayRecord?.afternoonTaken == true
            ScheduleType.EVENING -> todayRecord?.eveningTaken == true
        }

        if (alreadyTaken) {
            logManager.d(TAG, "Schedule $scheduleType already taken. No notification.")
            return
        }

        // 4. Check if we should notify now
        val alreadyNotifiedAtEnd = when (scheduleType) {
            ScheduleType.MORNING -> appSettings.notifiedAtEndOfMorning
            ScheduleType.AFTERNOON -> appSettings.notifiedAtEndOfAfternoon
            ScheduleType.EVENING -> appSettings.notifiedAtEndOfEvening
        }

        var shouldNotify = false

        if (forceNotification) {
            // Force notification (e.g. on BLE connect) - always notify if not taken
            shouldNotify = true
            logManager.d(TAG, "Decided to notify: Force notification")
        } else if (!alreadyNotifiedAtEnd) {
            // First notification for this slot's end time
            // We check BLE requirement here
            if (!onlyNotifyWhenBle || isConnectedToBle) {
                shouldNotify = true
                logManager.d(TAG, "Decided to notify: First notification for slot end")
            }
        } else {
            // Already notified once, check if it's time for a reminder
            if (secondsSinceLastNotification >= notificationIntervalMinutes * 60) {
                // Reminder interval passed
                // IMPORTANT: Reminders only happen if BLE is connected (Spec 2.2)
                // This prevents annoying reminders when the case is not nearby.
                if (isConnectedToBle) {
                    shouldNotify = true
                    logManager.d(TAG, "Decided to notify: Reminder interval passed and BLE connected")
                }
            }
        }

        if (shouldNotify) {
            // Send notification
            sendNotification(scheduleType, isInSlot = false)
            logManager.d(TAG, "Notification sent for $scheduleType at $currentHour:$currentMinute")

            // Update flags
            if (!forceNotification) {
                when (scheduleType) {
                    ScheduleType.MORNING -> repository.updateEndNotificationFlags(
                        morning = true,
                        afternoon = appSettings.notifiedAtEndOfAfternoon,
                        evening = appSettings.notifiedAtEndOfEvening
                    )
                    ScheduleType.AFTERNOON -> repository.updateEndNotificationFlags(
                        morning = appSettings.notifiedAtEndOfMorning,
                        afternoon = true,
                        evening = appSettings.notifiedAtEndOfEvening
                    )
                    ScheduleType.EVENING -> repository.updateEndNotificationFlags(
                        morning = appSettings.notifiedAtEndOfMorning,
                        afternoon = appSettings.notifiedAtEndOfAfternoon,
                        evening = true
                    )
                }
            }

            // Update last notification timestamp
            repository.updateLastNotificationTimestamp(currentTimeSeconds.toLong())
        } else {
            logManager.d(TAG, "Decided NOT to notify: interval not met or BLE requirements not met. lastNotif=$lastNotificationTime, diffSec=$secondsSinceLastNotification")
        }
    }

    private fun sendNotification(scheduleType: ScheduleType, isInSlot: Boolean) {
        val notificationId = scheduleType.id * 1000 + Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        val title = if (isInSlot) "服薬の時間です" else "${scheduleType.displayName}の服薬がまだです"
        val text = if (isInSlot) "${scheduleType.displayName}の薬を飲む時間になりました。" else "お薬を忘れずに服用してください。"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
}
