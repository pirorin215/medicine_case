package com.pirorin215.medicinecasemob.notification

import android.content.Context
import com.pirorin215.medicinecasemob.util.LogManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import kotlinx.coroutines.flow.first
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class NotificationScheduler @AssistedInject constructor(
    private val repository: MedicineRepository,
    private val notificationService: NotificationService,
    private val bleManager: com.pirorin215.medicinecasemob.ble.BleManager,
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NotificationScheduler"
        const val WORK_NAME = "medicine_reminder_check"
    }

    override suspend fun doWork(): Result {
        LogManager.getInstance().d(TAG, "Scheduled notification check running")

        return try {
            // Get current time
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            // Reset notification flags at midnight
            if (currentHour == 0) {
                LogManager.getInstance().d(TAG, "Resetting notification flags at midnight")
                repository.updateEndNotificationFlags(morning = false, afternoon = false, evening = false)
                repository.updateInSlotNotificationFlags(morning = false, afternoon = false, evening = false)
            }

            // Ensure today's record exists and get it (single DB query)
            val todayRecord = repository.ensureTodayRecordExists()

            // Load settings from repository (DataStore)
            val settings = repository.settingsFlow.first()
            val schedules = repository.getSchedulesFromSettings(settings)

            // Check and notify
            val isConnectedToBle = bleManager.connectionState.value is com.pirorin215.medicinecasemob.ble.BleManager.ConnectionState.Connected
            notificationService.checkAndNotifyMissedIntakes(
                schedules = schedules,
                todayRecord = todayRecord,
                isConnectedToBle = isConnectedToBle,
                forceNotification = false
            )

            Result.success()
        } catch (e: Exception) {
            LogManager.getInstance().e(TAG, "Error in notification check: " + e.message)
            Result.failure()
        }
    }
}
