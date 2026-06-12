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
        private const val INTAKE_POLL_INTERVAL_MS = 60000L // 1分ごとのintake取得
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
    private var lastIntakePollTime = 0L

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

        // Start time slot monitoring for untaken slots
        startTimeSlotMonitoring()

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

                    // Query intake and wait for response before notifying
                    val intakeResult = bleManager.queryIntake()
                    logManager.d(TAG, "Intake query result on service ready: $intakeResult")

                    // Wait for observer to process the intake event and update DB
                    delay(1000)

                    // Save last connected device address
                    val currentState = bleManager.connectionState.value
                    if (currentState is BleManager.ConnectionState.Connected) {
                        repository.updateLastDeviceAddress(currentState.device.address)
                        logManager.d(TAG, "Saved last device address: ${currentState.device.address}")
                    }

                    // Update poll timestamp
                    lastIntakePollTime = System.currentTimeMillis()

                    // Check for missed intakes and notify immediately
                    checkAndNotify(forceNotification = true)
                }
            }
        }

        // Observe scan results to auto-connect to matching devices in background
        serviceScope.launch {
            bleManager.scanResults.collect { results ->
                if (results.isEmpty()) return@collect
                
                // Only auto-connect if we are currently disconnected
                if (bleManager.connectionState.value is BleManager.ConnectionState.Disconnected) {
                    val matchingDevice = results.firstOrNull { 
                        it.device.name?.startsWith(BleManager.DEVICE_NAME_PREFIX) == true 
                    }?.device
                    
                    if (matchingDevice != null) {
                        logManager.d(TAG, "Auto-connecting to discovered device: ${matchingDevice.name}")
                        bleManager.connectToDevice(matchingDevice)
                    }
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

                    // Record intake (DB-level duplicate check is done inside)
                    recordIntakeLocally(timestamp)
                }

                // Consume and clear debug timestamps
                bleManager.consumeIntakeEvent()
                bleManager.clearLastIntakeTimestamp()
            }
        }
    }

    private suspend fun recordIntakeLocally(mcuTimestamp: Long) {
        // DB-level duplicate check: if this mcu_timestamp is already recorded, skip
        if (repository.isMcuTimestampRecorded(mcuTimestamp)) {
            logManager.d(TAG, "Duplicate intake event ignored (already in DB): $mcuTimestamp")
            return
        }

        val phoneTimestamp = System.currentTimeMillis() / 1000

        // Use MCU timestamp if available, otherwise use phone timestamp
        val effectiveTimestamp = if (mcuTimestamp > 0) mcuTimestamp else phoneTimestamp

        // Determine period using simple time-based logic (new design)
        val settings = repository.settingsFlow.first()
        val schedules = repository.getSchedulesFromSettings(settings)
        val scheduleType = determineScheduleTypeSimple(effectiveTimestamp, schedules)

        if (scheduleType == null) {
            logManager.d(TAG, "Ignoring intake: no valid schedule for current time (outside activity hours)")
            return
        }

        // Ensure today's record exists and get it (single DB query)
        val todayRecord = repository.ensureTodayRecordExists()

        // Check already taken using FRESH data from DB
        if (todayRecord.isTaken(scheduleType)) {
            logManager.d(TAG, "Ignoring intake: already recorded for $scheduleType")
            return
        }

        // Record using FRESH data from DB
        val updatedRecord = todayRecord.withTaken(scheduleType, mcuTimestamp, phoneTimestamp)

        repository.insertIntakeRecord(updatedRecord)
        logManager.d(TAG, "Intake recorded: $scheduleType mcu_time=$mcuTimestamp, phone_received=$phoneTimestamp")
    }

    /**
     * 時刻ベースのシンプルな枠判定（新しい設計）
     *
     * 既存の設定データから境界時刻を計算し、時刻だけで枠を判定
     * - 朝: 7:00 - 11:00 (朝の終了時刻まで)
     * - 昼: 11:00 - 17:00 (昼の終了時刻まで)
     * - 夜: 17:00 - 23:00 (夜の終了時刻まで)
     *
     * 重要：昨日以前の服薬検知は無視する（日付が異なる場合はnullを返す）
     *
     * @param timestamp 判定するタイムスタンプ（Unix秒）
     * @param schedules スケジュール設定（既存データからの互換性維持）
     * @return 枠タイプ。該当しない場合はnull
     */
    private fun determineScheduleTypeSimple(
        timestamp: Long,
        schedules: List<com.pirorin215.medicinecasemob.ui.data.MedicineSchedule>
    ): com.pirorin215.medicinecasemob.ui.data.ScheduleType? {
        val currentCalendar = Calendar.getInstance()
        val currentDay = currentCalendar.get(Calendar.DAY_OF_YEAR)
        val currentYear = currentCalendar.get(Calendar.YEAR)

        val intakeCalendar = Calendar.getInstance()
        intakeCalendar.timeInMillis = timestamp * 1000
        val intakeDay = intakeCalendar.get(Calendar.DAY_OF_YEAR)
        val intakeYear = intakeCalendar.get(Calendar.YEAR)

        // 日付が異なる場合は無効（昨日以前の服薬は無視）
        if (intakeYear != currentYear || intakeDay != currentDay) {
            logManager.d(TAG, "Ignoring intake: different date (intake: $intakeYear/$intakeDay, current: $currentYear/$currentDay)")
            return null
        }

        // 同一日の場合のみ、時刻で枠判定
        val hour = intakeCalendar.get(Calendar.HOUR_OF_DAY)

        // 設定データから境界時刻を計算
        val morningSchedule = schedules.find { it.id == 0 }
        val afternoonSchedule = schedules.find { it.id == 1 }
        val eveningSchedule = schedules.find { it.id == 2 }

        val morningEndHour = morningSchedule?.endHour ?: 11
        val afternoonEndHour = afternoonSchedule?.endHour ?: 17
        val eveningEndHour = eveningSchedule?.endHour ?: 23
        val activityStartHour = morningSchedule?.startHour ?: 7

        // 設定値ベースの時刻判定
        return when {
            hour < activityStartHour -> null  // 活動前
            hour < morningEndHour -> com.pirorin215.medicinecasemob.ui.data.ScheduleType.MORNING
            hour < afternoonEndHour -> com.pirorin215.medicinecasemob.ui.data.ScheduleType.AFTERNOON
            hour < eveningEndHour -> com.pirorin215.medicinecasemob.ui.data.ScheduleType.EVENING
            else -> null  // 活動後
        }
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
                        logManager.d(TAG, "Bluetooth turned OFF - stopping scan and disconnecting")
                        stopContinuousScanning()
                        bleManager.disconnect()  // Explicitly disconnect to clear invalid GATT object
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

                // Check if already connected or connecting
                val currentState = bleManager.connectionState.value
                if (currentState is BleManager.ConnectionState.Connected ||
                    currentState is BleManager.ConnectionState.Connecting) {
                    logManager.d(TAG, "Already connected or connecting ($currentState), skipping scan")

                    // 定期ポーリング: 現在枠が未服薬の場合のみ1分ごとにBLEから最新intakeを取得
                    if (currentState is BleManager.ConnectionState.Connected && bleManager.serviceReady.value) {
                        val elapsed = System.currentTimeMillis() - lastIntakePollTime
                        if (elapsed >= INTAKE_POLL_INTERVAL_MS && shouldPollIntake()) {
                            logManager.d(TAG, "Polling intake from BLE (last poll ${elapsed}ms ago)")
                            val result = bleManager.queryIntake(timeoutMs = 2000L)
                            if (result != null) {
                                logManager.d(TAG, "Intake poll result: $result")
                                // ObserverがDB更新するのを待つ
                                delay(500)
                            }
                            lastIntakePollTime = System.currentTimeMillis()
                        }
                    }

                    // 定期的に通知チェックを実行
                    checkAndNotify()

                    delay(SCAN_INTERVAL_MS)
                    continue
                }

                // 定期的に通知チェックを実行
                checkAndNotify()

                // 現在枠が服薬済みならスキャンをスキップ（無駄なBLE通信を削減）
                if (!shouldPollIntake()) {
                    delay(SCAN_INTERVAL_MS)
                    continue
                }

                // Get last connected address for faster reconnection
                val settings = repository.settingsFlow.first()
                val lastAddress = settings.lastDeviceAddress

                bleManager.startScan(lastAddress)

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
                repository.resetDailyNotificationFlags()
            }

            val isConnected = bleManager.connectionState.value is BleManager.ConnectionState.Connected

            // DBから最新の服薬記録を取得
            // （ポーリング or サービス接続時の queryIntake で既にDBは最新化されている）
            val todayRecord = repository.ensureTodayRecordExists()

            // Load settings from repository
            val settings = repository.settingsFlow.first()
            val schedules = repository.getSchedulesFromSettings(settings)

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

    /**
     * 現在時刻に該当する（または少し先の）スケジュール枠が未服薬かどうかを判定。
     * ポーリングの必要性を判定するために使用。
     *
     * @return true: 未服薬の枠あり（ポーリング必要）, false: 服薬済み or 該当枠なし（スキップ）
     */
    private suspend fun shouldPollIntake(): Boolean {
        // 1分先のスロットを確認（1分前からスキャンを開始するため）
        val currentSlot = getCurrentSlot(lookAheadMinutes = 1) ?: run {
            val calendar = Calendar.getInstance()
            val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            logManager.d(TAG, "shouldPollIntake: no active or upcoming slot at $currentMinutes -> skip")
            return false
        }

        // 今日のレコードを取得して服薬状態を確認
        val todayStart = repository.getTodayStartTimestamp()
        val todayRecord = repository.getIntakeRecordByDateSync(todayStart)

        val scheduleType = com.pirorin215.medicinecasemob.ui.data.ScheduleType.fromId(currentSlot.id) ?: return false
        val isTaken = todayRecord?.isTaken(scheduleType) == true

        logManager.d(TAG, "shouldPollIntake: slot=$scheduleType (lookahead), taken=$isTaken -> ${if (!isTaken) "poll" else "skip"}")
        return !isTaken
    }

    /**
     * 未服薬枠への遷移を監視し、未服薬枠に入ったら即座にBLE接続を開始する
     */
    private fun startTimeSlotMonitoring() {
        serviceScope.launch {
            var lastSlot: com.pirorin215.medicinecasemob.ui.data.MedicineSchedule? = null

            while (isActive) {
                // 1分先のスロットを監視（1分前から接続準備を開始するため）
                val currentSlot = getCurrentSlot(lookAheadMinutes = 1)

                // 枠が変わった場合のみチェック
                if (currentSlot != lastSlot) {
                    logManager.d(TAG, "Time slot changed (with 1min lookahead): ${lastSlot?.id} -> ${currentSlot?.id}")

                    // 新しい枠が有効で、かつ未服薬なら即時接続
                    if (currentSlot != null && currentSlot.enabled) {
                        val slotTaken = isSlotTaken(currentSlot)
                        logManager.d(TAG, "New upcoming slot detected: id=${currentSlot.id}, taken=$slotTaken")

                        if (!slotTaken) {
                            logManager.d(TAG, "Untaken upcoming slot detected - initiating look-ahead connection")

                            // 接続済みでなければスキャン開始
                            val currentState = bleManager.connectionState.value
                            if (currentState is BleManager.ConnectionState.Disconnected) {
                                val settings = repository.settingsFlow.first()
                                val lastAddress = settings.lastDeviceAddress

                                logManager.d(TAG, "Starting immediate scan for upcoming untaken slot")
                                bleManager.startScan(lastAddress)
                            } else {
                                logManager.d(TAG, "Already connected, skipping immediate scan")
                            }
                        }
                    }

                    lastSlot = currentSlot
                }

                // 30秒ごとにチェック（1分前を確実に捉えるため、監視間隔を短縮）
                delay(30000)
            }
        }
    }

    /**
     * 現在時刻（または指定分後）に該当するスケジュール枠を取得
     * 
     * @param lookAheadMinutes 先読みする時間（分）
     */
    private suspend fun getCurrentSlot(lookAheadMinutes: Int = 0): com.pirorin215.medicinecasemob.ui.data.MedicineSchedule? {
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE) + lookAheadMinutes

        val settings = repository.settingsFlow.first()
        val schedules = repository.getSchedulesFromSettings(settings)

        return schedules
            .filter { it.enabled }
            .find { currentMinutes in it.startMinuteOfDay until it.endMinuteOfDay }
    }

    /**
     * 指定された枠が服薬済みかどうかを判定
     */
    private suspend fun isSlotTaken(slot: com.pirorin215.medicinecasemob.ui.data.MedicineSchedule): Boolean {
        val todayStart = repository.getTodayStartTimestamp()
        val todayRecord = repository.getIntakeRecordByDateSync(todayStart) ?: return false

        val scheduleType = com.pirorin215.medicinecasemob.ui.data.ScheduleType.fromId(slot.id) ?: return false

        return todayRecord.isTaken(scheduleType)
    }

}