package com.pirorin215.medicinecasemob.notification

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class NotificationScheduler @AssistedInject constructor(
    private val repository: MedicineRepository,
    private val notificationService: NotificationService,
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NotificationScheduler"
        const val WORK_NAME = "medicine_reminder_check"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Scheduled notification check running")

        return try {
            // Get today's date at midnight
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis / 1000

            // Get today's record
            val todayRecord = repository.getIntakeRecordByDateSync(todayStart)

            // Get schedules from repository
            val schedules = repository.getAllSchedulesSync()

            // Check and notify
            notificationService.checkAndNotifyMissedIntakes(
                schedules = schedules,
                todayRecord = todayRecord,
                isConnectedToBle = false, // Scheduled checks are not BLE-connected
                forceNotification = false
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in notification check", e)
            Result.failure()
        }
    }
}
