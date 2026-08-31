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
        val enabledSchedules = schedules.filter { it.enabled }.sortedByDescending { it.endMinuteOfDay }

        // --- In-slot (Intelligent) Notification Logic ---
        // Find if we are currently WITHIN an active slot
        val currentSlot = enabledSchedules.find {
            currentMinutes in it.startMinuteOfDay until it.endMinuteOfDay
        }

        if (currentSlot != null) {
            val scheduleType = ScheduleType.fromId(currentSlot.id) ?: return

            // Check if already taken
            val alreadyTaken = todayRecord?.isTaken(scheduleType) == true

            if (!alreadyTaken) {
                // --- Preferred-time notifications (枠とは独立した最大3つの時刻) ---
                // 推奨時刻が現在進行中の有効枠内にあり、到達済みで未通知なら通知する。
                // 有効な枠の範囲外に設定された推奨時刻は無視される。
                val preferredConfigs = listOf(
                    Triple(1, appSettings.preferredReminderMinute1, appSettings.preferredNotified1),
                    Triple(2, appSettings.preferredReminderMinute2, appSettings.preferredNotified2),
                    Triple(3, appSettings.preferredReminderMinute3, appSettings.preferredNotified3)
                )
                val triggeredPreferred = preferredConfigs.firstOrNull { (_, preferredMinutes, notified) ->
                    !notified &&
                        preferredMinutes in currentSlot.startMinuteOfDay until currentSlot.endMinuteOfDay &&
                        currentMinutes >= preferredMinutes
                }

                if (triggeredPreferred != null) {
                    val (preferredIndex, preferredMinutes, _) = triggeredPreferred
                    logManager.d(TAG, "In-slot notification triggered: preferred time #$preferredIndex reached (${preferredMinutes / 60}:${preferredMinutes % 60})")
                    sendNotification(scheduleType, isInSlot = true)

                    repository.updatePreferredNotificationFlags(
                        preferred1 = appSettings.preferredNotified1 || preferredIndex == 1,
                        preferred2 = appSettings.preferredNotified2 || preferredIndex == 2,
                        preferred3 = appSettings.preferredNotified3 || preferredIndex == 3
                    )
                    repository.updateLastNotificationTimestamp(currentTimeSeconds.toLong())
                    return // Exit after sending in-slot notification
                }

                // --- Chance notification (BLE connected within slot) ---
                // チャンス通知は枠につき1日最大1回
                val chanceNotified = when (scheduleType) {
                    ScheduleType.MORNING -> appSettings.chanceNotifiedMorning
                    ScheduleType.AFTERNOON -> appSettings.chanceNotifiedAfternoon
                    ScheduleType.EVENING -> appSettings.chanceNotifiedEvening
                }

                if (forceNotification && !chanceNotified) {
                    logManager.d(TAG, "Chance notification triggered: BLE connected within slot")
                    sendNotification(scheduleType, isInSlot = true)

                    when (scheduleType) {
                        ScheduleType.MORNING -> repository.updateChanceNotificationFlags(
                            morning = true,
                            afternoon = appSettings.chanceNotifiedAfternoon,
                            evening = appSettings.chanceNotifiedEvening
                        )
                        ScheduleType.AFTERNOON -> repository.updateChanceNotificationFlags(
                            morning = appSettings.chanceNotifiedMorning,
                            afternoon = true,
                            evening = appSettings.chanceNotifiedEvening
                        )
                        ScheduleType.EVENING -> repository.updateChanceNotificationFlags(
                            morning = appSettings.chanceNotifiedMorning,
                            afternoon = appSettings.chanceNotifiedAfternoon,
                            evening = true
                        )
                    }
                    repository.updateLastNotificationTimestamp(currentTimeSeconds.toLong())
                    return // Exit after sending in-slot notification
                }
            }
        }

        // --- EXISTING: End-of-slot (Deadline) Notification Logic ---
        // 2. Check if there's an active (currently running) slot
        val activeSchedule = enabledSchedules.firstOrNull { schedule ->
            currentMinutes in schedule.startMinuteOfDay until schedule.endMinuteOfDay
        }

        // If there's an active slot, don't notify about previous slots
        if (activeSchedule != null) {
            logManager.d(TAG, "Active slot exists: ${ScheduleType.fromId(activeSchedule.id)}. Skipping notifications for previous slots.")
            return
        }

        // 3. Find the latest ended schedule to check
        val latestEndedSchedule = enabledSchedules
            .filter { currentMinutes >= it.endMinuteOfDay }
            .maxByOrNull { it.endMinuteOfDay }

        if (latestEndedSchedule == null) return

        val scheduleType = ScheduleType.fromId(latestEndedSchedule.id) ?: return

        // 3. Check if already taken
        val alreadyTaken = todayRecord?.isTaken(scheduleType) == true

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
        notificationManager.notify(notificationIdFor(scheduleType), notification)
    }

    /**
     * 服薬が検知・DBに記録されたことをユーザに知らせる確認通知。
     * 服薬忘れ通知と同じIDを使い、忘れ通知を上書き（キャンセル相当）する。
     * これにより「服薬済みなのに忘れ通知が残る」矛盾を防ぐ。
     */
    fun notifyIntakeConfirmed(scheduleType: ScheduleType) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("服薬を確認しました")
            .setContentText("${scheduleType.displayName}の服薬を確認しました。")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationIdFor(scheduleType), notification)
    }

    private fun notificationIdFor(scheduleType: ScheduleType): Int =
        scheduleType.id * 1000 + Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
}
