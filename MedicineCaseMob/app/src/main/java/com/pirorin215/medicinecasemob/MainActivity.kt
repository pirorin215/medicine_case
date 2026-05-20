package com.pirorin215.medicinecasemob

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import com.pirorin215.medicinecasemob.ble.MedicineBleScanService
import com.pirorin215.medicinecasemob.ui.screen.MainScreen
import com.pirorin215.medicinecasemob.ui.viewModel.MainViewModel
import com.pirorin215.medicinecasemob.ui.theme.MedicineCaseMobTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            Log.d(TAG, "Permission results: $permissions")
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                Toast.makeText(this, "BLE権限が許可されました", Toast.LENGTH_SHORT).show()
                startBleService()
            } else {
                Toast.makeText(this, "BLE権限が必要です", Toast.LENGTH_LONG).show()
            }
        }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        enableEdgeToEdge()

        // Check and request BLE permissions if needed
        val missingPermissions = bluetoothPermissions.filter { permission ->
            checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        Log.d(TAG, "Missing permissions: $missingPermissions")

        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${missingPermissions.toTypedArray()}")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d(TAG, "All permissions already granted")
            startBleService()
        }

        setContent {
            MedicineCaseMobTheme {
                var showSettingsScreen by remember { mutableStateOf(false) }

                if (showSettingsScreen) {
                    com.pirorin215.medicinecasemob.ui.screen.SettingsScreen(
                        onNavigateBack = { showSettingsScreen = false }
                    )
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        MainScreen(
                            modifier = Modifier.padding(innerPadding),
                            onNavigateToSettings = { showSettingsScreen = true }
                        )
                    }
                }
            }
        }
    }

    private fun startBleService() {
        Log.d(TAG, "Starting MedicineBleScanService...")
        val serviceIntent = Intent(this, MedicineBleScanService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
            Log.d(TAG, "Foreground service started")
        } else {
            startService(serviceIntent)
            Log.d(TAG, "Service started")
        }
    }
}
