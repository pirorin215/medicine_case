package com.pirorin215.medicinecasemob.notification

import android.content.Context
import com.pirorin215.medicinecasemob.util.LogManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderWorkManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ReminderWorkManager"
    }

    /**
     * Start periodic notification checks every 5 minutes.
     */
    fun startPeriodicChecks() {
        LogManager.getInstance().d(TAG, "Starting periodic notification checks")

        val constraints = Constraints.Builder()
            .setRequiresCharging(false)
            .build()

        val notificationCheckRequest = PeriodicWorkRequestBuilder<NotificationScheduler>(
            15, TimeUnit.MINUTES  // Minimum interval for PeriodicWorkRequest
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NotificationScheduler.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            notificationCheckRequest
        )

        LogManager.getInstance().d(TAG, "Periodic notification checks scheduled")
    }

    /**
     * Stop periodic notification checks.
     */
    fun stopPeriodicChecks() {
        LogManager.getInstance().d(TAG, "Stopping periodic notification checks")
        WorkManager.getInstance(context).cancelUniqueWork(NotificationScheduler.WORK_NAME)
    }
}
