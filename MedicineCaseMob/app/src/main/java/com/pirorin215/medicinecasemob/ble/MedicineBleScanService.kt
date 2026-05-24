package com.pirorin215.medicinecasemob.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.pirorin215.medicinecasemob.util.LogManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import com.pirorin215.medicinecasemob.MainActivity
import com.pirorin215.medicinecasemob.R
import com.pirorin215.medicinecasemob.notification.NotificationService
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MedicineBleScanService : Service() {
    private val logManager = LogManager.getInstance()


    companion object {
        private const val TAG = "MedicineBleScanService"
        const val DEVICE_NAME = "MedicineCase-0001"
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "MedicineBleScanServiceChannel"
        private const val SCAN_INTERVAL_MS = 10000L // 10秒ごとのスキャン
    }

    private val binder = LocalBinder()
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null

    @Inject
    lateinit var bleManager: BleManager

    @Inject
    lateinit var repository: MedicineRepository

    @Inject
    lateinit var notificationService: NotificationService


    private var scanJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    inner class LocalBinder : Binder() {
        fun getService(): MedicineBleScanService = this@MedicineBleScanService
    }

    override fun onBind(intent: Intent?): IBinder {
        logManager.d(TAG, "onBind called")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        logManager.d(TAG, "onCreate called")

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        createNotificationChannel()

        // Register Bluetooth state receiver
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        // Start background observers
        observeBleEvents()

        // Start continuous scanning
        startContinuousScanning()
    }

    private fun observeBleEvents() {
        // Observe service ready state for sync/notify (wait for service discovery)
        serviceScope.launch {
            bleManager.serviceReady.collect { ready ->
                if (ready) {
                    logManager.d(TAG, "Service ready observed in Service - syncing time and getting version")
                    bleManager.syncTime()
                    bleManager.getVersion()
                    bleManager.getIntake()

                    // Check for missed intakes and notify immediately
                    checkAndNotify(forceNotification = true)
                }
            }
        }

        // Observe intake events for recording
        serviceScope.launch {
            bleManager.intakeEvent.collect { event ->
                if (event == null) return@collect

                logManager.d(TAG, "Processing intake event in Service: $event")

                if (event.startsWith("INTAKE:")) {
                    val timestampStr = event.removePrefix("INTAKE:")
                    val timestamp = timestampStr.toLongOrNull() ?: 0L

                    // Always record intake (even if timestamp is 0)
                    recordIntakeLocally(timestamp)
                    // Clear intake timestamp on firmware
                    bleManager.clearIntake()
                }

                // Consume and clear debug timestamps
                bleManager.consumeIntakeEvent()
                bleManager.clearLastIntakeTimestamp()
            }
        }
    }

    private suspend fun recordIntakeLocally(mcuTimestamp: Long) {
        val phoneTimestamp = System.currentTimeMillis() / 1000

        // Use MCU timestamp if available, otherwise use phone timestamp
        val effectiveTimestamp = if (mcuTimestamp > 0) mcuTimestamp else phoneTimestamp

        // Ensure today's record exists and get it
        val todayRecord = repository.ensureTodayRecordExists()

        // Determine period (use effective timestamp for schedule determination)
        val settings = repository.settingsFlow.first()
        val schedules = repository.getSchedulesFromSettings(settings)
        var scheduleType = determineScheduleTypeForTimestamp(effectiveTimestamp, schedules)

        if (scheduleType == null) {
            scheduleType = determineScheduleTypeAfterNotification(phoneTimestamp, schedules, settings)
        }

        if (scheduleType == null) {
            logManager.d(TAG, "Ignoring intake: no valid schedule for current time")
            return
        }

        // Check already taken
        val alreadyTaken = when (scheduleType) {
            com.pirorin215.medicinecasemob.ui.data.ScheduleType.MORNING -> todayRecord.morningTaken
            com.pirorin215.medicinecasemob.ui.data.ScheduleType.AFTERNOON -> todayRecord.afternoonTaken
            com.pirorin215.medicinecasemob.ui.data.ScheduleType.EVENING -> todayRecord.eveningTaken
        }

        if (alreadyTaken) {
            logManager.d(TAG, "Ignoring intake: already recorded for $scheduleType")
            return
        }

        // Record (the record is guaranteed to exist now)
        val updatedRecord = when (scheduleType) {
            com.pirorin215.medicinecasemob.ui.data.ScheduleType.MORNING -> todayRecord.copy(morningTaken = true, morningTime = effectiveTimestamp)
            com.pirorin215.medicinecasemob.ui.data.ScheduleType.AFTERNOON -> todayRecord.copy(afternoonTaken = true, afternoonTime = effectiveTimestamp)
            com.pirorin215.medicinecasemob.ui.data.ScheduleType.EVENING -> todayRecord.copy(eveningTaken = true, eveningTime = effectiveTimestamp)
        }

        repository.insertIntakeRecord(updatedRecord)
        logManager.d(TAG, "Intake recorded: $scheduleType at time=$effectiveTimestamp (mcu=$mcuTimestamp, phone=$phoneTimestamp)")
    }

    private fun determineScheduleTypeForTimestamp(
        timestamp: Long,
        schedules: List<com.pirorin215.medicinecasemob.ui.data.MedicineSchedule>
    ): com.pirorin215.medicinecasemob.ui.data.ScheduleType? {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp * 1000
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute

        for (schedule in schedules) {
            if (!schedule.enabled) continue
            val startMinutes = schedule.startHour * 60 + schedule.startMinute
            val endMinutes = schedule.endHour * 60 + schedule.endMinute
            if (currentMinutes in startMinutes..endMinutes) {
                return com.pirorin215.medicinecasemob.ui.data.ScheduleType.fromId(schedule.id)
            }
        }
        return null
    }

    private fun determineScheduleTypeAfterNotification(
        timestamp: Long,
        schedules: List<com.pirorin215.medicinecasemob.ui.data.MedicineSchedule>,
        settings: com.pirorin215.medicinecasemob.ui.data.AppSettingsData
    ): com.pirorin215.medicinecasemob.ui.data.ScheduleType? {
        val lastNotificationTime = settings.lastNotificationTimestamp
        val notificationIntervalMinutes = settings.notificationIntervalMinutes
        val gracePeriodMinutes = notificationIntervalMinutes * 2
        val minutesSinceLastNotification = if (lastNotificationTime > 0) {
            (timestamp - lastNotificationTime) / 60
        } else {
            Long.MAX_VALUE
        }

        if (minutesSinceLastNotification <= gracePeriodMinutes && lastNotificationTime > 0) {
            val lastSchedule = schedules.filter { it.enabled }.maxByOrNull { it.endHour * 60 + it.endMinute }
            if (lastSchedule != null) {
                return com.pirorin215.medicinecasemob.ui.data.ScheduleType.fromId(lastSchedule.id)
            }
        }
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logManager.d(TAG, "onStartCommand called")

        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        return START_STICKY // Service will be explicitly started and stopped
    }

    override fun onDestroy() {
        super.onDestroy()
        logManager.d(TAG, "onDestroy called")

        // Stop scanning
        stopContinuousScanning()

        // Unregister receiver
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            logManager.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        logManager.d(TAG, "Bluetooth turned OFF - stopping scan")
                        stopContinuousScanning()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        logManager.d(TAG, "Bluetooth turned ON - starting scan")
                        startContinuousScanning()
                    }
                }
            }
        }
    }

    private fun startContinuousScanning() {
        logManager.d(TAG, "Starting continuous scanning")

        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) { // Continue until cancelled
                if (!isBluetoothAvailable()) {
                    logManager.d(TAG, "Bluetooth not available, waiting...")
                    delay(5000)
                    continue
                }

                if (!isBluetoothEnabled()) {
                    logManager.d(TAG, "Bluetooth not enabled, waiting...")
                    delay(5000)
                    continue
                }

                // Check if already connected
                if (bleManager.connectionState.value is BleManager.ConnectionState.Connected) {
                    logManager.d(TAG, "Already connected, skipping scan")
                // 定期的に通知チェックを実行
                checkAndNotify()

                    delay(SCAN_INTERVAL_MS)
                    continue
                }


                // 定期的に通知チェックを実行
                checkAndNotify()
                bleManager.startScan()

                // Wait before next scan
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    private fun stopContinuousScanning() {
        logManager.d(TAG, "Stopping continuous scanning")
        scanJob?.cancel()
        scanJob = null
        bleManager.stopScan()
    }

    private fun isBluetoothAvailable(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    private fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Medicine Case BLE Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "スマート薬ケースとのBLE接続を維持するためのサービス"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Medicine Case")
            .setContentText("薬ケースと接続中...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private suspend fun checkAndNotify(forceNotification: Boolean = false) {
        try {
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            // Reset notification flags at midnight
            if (currentHour == 0) {
                logManager.d(TAG, "Resetting notification flags at midnight")
                repository.updateEndNotificationFlags(morning = false, afternoon = false, evening = false)
                repository.updateInSlotNotificationFlags(morning = false, afternoon = false, evening = false)
            }

            // Ensure today's record exists and get it
            val todayRecord = repository.ensureTodayRecordExists()

            // Load settings from repository
            val settings = repository.settingsFlow.first()
            val schedules = repository.getSchedulesFromSettings(settings)

            val isConnected = bleManager.connectionState.value is BleManager.ConnectionState.Connected

            notificationService.checkAndNotifyMissedIntakes(
                schedules = schedules,
                todayRecord = todayRecord,
                isConnectedToBle = isConnected,
                forceNotification = forceNotification
            )
        } catch (e: Exception) {
            logManager.e(TAG, "Error in checkAndNotify: " + e.message)
        }
    }

}