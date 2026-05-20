package com.pirorin215.medicinecasemob.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pirorin215.medicinecasemob.R
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.data.MedicineIntakeRecord
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import com.pirorin215.medicinecasemob.ui.viewModel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val schedules by viewModel.schedules.collectAsState()
    val intakeRecords by viewModel.intakeRecords.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val bleConnectionState by viewModel.bleConnectionState.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()

    var showBleDialog by remember { mutableStateOf(false) }

    android.util.Log.d("MainScreen", "Recomposing, showBleDialog = $showBleDialog")

    if (showBleDialog) {
        android.util.Log.d("MainScreen", "Showing BleConnectionDialog")
        BleConnectionDialog(
            viewModel = viewModel,
            onDismiss = {
                android.util.Log.d("MainScreen", "BleConnectionDialog onDismiss called")
                showBleDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medicine Case") },
                actions = {
                    // BLE接続状態表示
                    IconButton(onClick = { showBleDialog = true }) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                            contentDescription = "BLE接続状態",
                            tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Today's status
            item {
                TodayStatusCard(
                    schedules = schedules,
                    todayRecord = viewModel.getTodayRecord()
                )
            }

            // History
            item {
                Text(
                    text = "服薬履歴",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(intakeRecords.take(30)) { record ->
                HistoryRecordCard(record)
            }
        }
    }
}

@Composable
fun TodayStatusCard(
    schedules: List<com.pirorin215.medicinecasemob.ui.data.MedicineSchedule>,
    todayRecord: MedicineIntakeRecord?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "本日の服用状況",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            ScheduleType.values().forEach { type ->
                val schedule = schedules.find { it.id == type.id }
                if (schedule?.enabled == true) {
                    ScheduleStatusRow(
                        type = type,
                        time = "${schedule.hour}:${String.format("%02d", schedule.minute)}",
                        taken = when (type) {
                            ScheduleType.MORNING -> todayRecord?.morningTaken == true
                            ScheduleType.AFTERNOON -> todayRecord?.afternoonTaken == true
                            ScheduleType.EVENING -> todayRecord?.eveningTaken == true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleStatusRow(
    type: ScheduleType,
    time: String,
    taken: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (taken) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (taken) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Text(
            text = time,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HistoryRecordCard(record: MedicineIntakeRecord) {
    val dateFormat = SimpleDateFormat("MM月dd日(E)", Locale.JAPAN)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = dateFormat.format(Date(record.date * 1000)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            ScheduleType.values().forEach { type ->
                val taken = when (type) {
                    ScheduleType.MORNING -> record.morningTaken
                    ScheduleType.AFTERNOON -> record.afternoonTaken
                    ScheduleType.EVENING -> record.eveningTaken
                }

                if (taken || record.date == System.currentTimeMillis() / 1000) {
                    ScheduleStatusRow(
                        type = type,
                        time = "",  // Show empty for history
                        taken = taken
                    )
                }
            }
        }
    }
}

@Composable
fun BleConnectionDialog(
    viewModel: MainViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val bleConnectionState by viewModel.bleConnectionState.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BLE接続") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (bleConnectionState) {
                    is BleManager.ConnectionState.Disconnected -> {
                        Text("Medicine Caseデバイスを探しています...")

                        if (scanResults.isEmpty()) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.startBleScan() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("スキャン開始")
                            }
                        }
                    }
                    is BleManager.ConnectionState.Scanning -> {
                        Text("スキャン中...")
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    is BleManager.ConnectionState.Connecting -> {
                        Text("接続中...")
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    is BleManager.ConnectionState.Connected -> {
                        val device = (bleConnectionState as BleManager.ConnectionState.Connected).device
                        Text("接続済: ${device.name}")

                        Spacer(modifier = Modifier.height(8.dp))

                        androidx.compose.material3.Button(
                            onClick = {
                                viewModel.syncTime()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("時刻同期")
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        androidx.compose.material3.Button(
                            onClick = {
                                viewModel.disconnectBle()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("切断")
                        }
                    }
                }

                // Show scan results
                if (scanResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("見つかったデバイス:", style = MaterialTheme.typography.titleSmall)
                    scanResults.forEach { result ->
                        val device = result.device
                        androidx.compose.material3.Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = {
                                viewModel.connectToDevice(device)
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = device.name ?: "不明",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (bleConnectionState) {
                is BleManager.ConnectionState.Scanning -> {
                    androidx.compose.material3.TextButton(onClick = { viewModel.stopBleScan() }) {
                        Text("スキャン停止")
                    }
                }
                else -> {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("閉じる")
                    }
                }
            }
        }
    )
}
