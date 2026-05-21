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
import com.pirorin215.medicinecasemob.MainActivity
import com.pirorin215.medicinecasemob.R
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

    private var scanJob: Job? = null

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

        // Start continuous scanning
        startContinuousScanning()
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
                    delay(SCAN_INTERVAL_MS)
                    continue
                }

                logManager.d(TAG, "Starting BLE scan...")
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
}
