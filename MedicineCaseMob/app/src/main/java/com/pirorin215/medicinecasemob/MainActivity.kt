package com.pirorin215.medicinecasemob

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import com.pirorin215.medicinecasemob.util.LogManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pirorin215.medicinecasemob.ble.MedicineBleScanService
import com.pirorin215.medicinecasemob.navigation.MedicineNavGraph
import com.pirorin215.medicinecasemob.notification.ReminderWorkManager

import com.pirorin215.medicinecasemob.ui.viewModel.MainViewModel
import com.pirorin215.medicinecasemob.ui.theme.MedicineCaseMobTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val cn = ComponentName(context, com.pirorin215.medicinecasemob.notification.MedicineNotificationListener::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(cn.flattenToString())
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var logManager: LogManager

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject
    lateinit var reminderWorkManager: ReminderWorkManager

    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val notificationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        emptyArray()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            logManager.d(TAG, "Permission results: $permissions")
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                Toast.makeText(this, "BLE権限が許可されました", Toast.LENGTH_SHORT).show()
                startBleService()
                requestNotificationPermissions()
            } else {
                Toast.makeText(this, "BLE権限が必要です", Toast.LENGTH_LONG).show()
            }
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                logManager.d(TAG, "Notification permission granted")
            } else {
                logManager.d(TAG, "Notification permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logManager.d(TAG, "onCreate called")
        enableEdgeToEdge()

        // Create notification channel
        createNotificationChannel()

        // Start periodic reminder checks
        reminderWorkManager.startPeriodicChecks()

        // Check and request BLE permissions if needed
        val missingPermissions = bluetoothPermissions.filter { permission ->
            checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        logManager.d(TAG, "Missing permissions: $missingPermissions")

        if (missingPermissions.isNotEmpty()) {
            logManager.d(TAG, "Requesting permissions: ${missingPermissions.toTypedArray()}")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            logManager.d(TAG, "All permissions already granted")
            startBleService()
            requestNotificationPermissions()
        }

        setContent {
            val viewModel: com.pirorin215.medicinecasemob.ui.viewModel.MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsState(initial = null)

            var showNotificationAccessDialog by remember { mutableStateOf(false) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                val isAccessGranted = isNotificationListenerEnabled(this@MainActivity)
                if (!isAccessGranted) {
                    showNotificationAccessDialog = true
                }
            }

            MedicineCaseMobTheme(fontSizeScale = settings?.uiFontSizeScale ?: 1.2f) {
                MedicineNavGraph()

                if (showNotificationAccessDialog) {
                    NotificationAccessPermissionDialog(
                        onDismiss = { showNotificationAccessDialog = false },
                        onOpenSettings = {
                            try {
                                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                this@MainActivity.startActivity(intent)
                            } catch (e: Exception) {
                                logManager.e(TAG, "Failed to open notification settings: ${e.message}")
                            }
                            showNotificationAccessDialog = false
                        }
                    )
                }
            }
        }
    }

    private fun startBleService() {
        logManager.d(TAG, "Starting MedicineBleScanService...")
        val serviceIntent = Intent(this, MedicineBleScanService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
            logManager.d(TAG, "Foreground service started")
        } else {
            startService(serviceIntent)
            logManager.d(TAG, "Service started")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "服薬リマインダー"
            val descriptionText = "服薬時間のお知らせ"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("medicine_reminder", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            logManager.d(TAG, "Notification channel created")
        }
    }

    private fun requestNotificationPermissions() {
        if (notificationPermissions.isNotEmpty()) {
            val missingNotificationPermissions = notificationPermissions.filter { permission ->
                checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (missingNotificationPermissions.isNotEmpty()) {
                logManager.d(TAG, "Requesting notification permissions: $missingNotificationPermissions")
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                logManager.d(TAG, "All notification permissions already granted")
            }
        }
    }
}

@Composable
fun NotificationAccessPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Filled.Warning, contentDescription = "警告")
        },
        title = {
            Text("通知アクセス権限の設定が必要です")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "このアプリがバックグラウンドで強制終了されずに動作し続けるためには、" +
                            "「通知アクセス権」の許可が必要です。\n\n" +
                            "※通知の読み取りなどは行いません。常時起動を保証するためのシステム機能として利用します。",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "下のボタンから設定画面を開き、「MedicineCaseMob」の通知アクセスを許可してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("設定を開く")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("後で")
            }
        }
    )
}
