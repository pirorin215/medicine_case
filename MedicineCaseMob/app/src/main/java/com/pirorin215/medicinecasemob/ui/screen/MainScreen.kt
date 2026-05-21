package com.pirorin215.medicinecasemob.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.components.AppLogCard
import com.pirorin215.medicinecasemob.ui.data.MedicineIntakeRecord
import com.pirorin215.medicinecasemob.ui.data.MedicineSchedule
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
    onNavigateToDetectionSettings: () -> Unit = {},
    onNavigateToScheduleSettings: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {}
) {
    val context = LocalContext.current
    val intakeRecords by viewModel.intakeRecords.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val bleConnectionState by viewModel.bleConnectionState.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedRecordIds by viewModel.selectedRecordIds.collectAsState()
    val appLogs by viewModel.appLogs.collectAsState()

    var showBleDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAppLogsOverlay by remember { mutableStateOf(false) }

    if (showBleDialog) {
        BleConnectionDialog(
            viewModel = viewModel,
            onDismiss = { showBleDialog = false }
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("履歴を削除") },
            text = { Text("選択した${selectedRecordIds.size}件の服薬履歴を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedRecords()
                        showDeleteSelectedDialog = false
                    }
                ) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medicine Case") },
                actions = {
                    if (!isSelectMode) {
                        IconButton(onClick = { showBleDialog = true }) {
                            Icon(
                                imageVector = if (isConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                contentDescription = "BLE接続状態",
                                tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "メニュー"
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("マイコン設定") },
                                    onClick = {
                                        showMenu = false
                                        onNavigateToDetectionSettings()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("アプリ設定") },
                                    onClick = {
                                        showMenu = false
                                        onNavigateToScheduleSettings()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("マイコンデバッグ") },
                                    onClick = {
                                        showMenu = false
                                        onNavigateToDebug()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("アプリログ") },
                                    onClick = {
                                        showMenu = false
                                        showAppLogsOverlay = !showAppLogsOverlay
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("全履歴削除", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.clearHistory()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Schedule overview
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    ScheduleOverviewCard(
                        schedules = schedules,
                        onClick = onNavigateToScheduleSettings
                    )
                }

                // History header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectMode) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedRecordIds.size}件選択",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { viewModel.selectAllRecords() }) {
                                    Icon(Icons.Default.SelectAll, contentDescription = "全選択")
                                }
                                IconButton(onClick = {
                                    if (selectedRecordIds.isNotEmpty()) {
                                        showDeleteSelectedDialog = true
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "削除",
                                        tint = if (selectedRecordIds.isNotEmpty())
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.exitSelectMode() }) {
                                    Icon(Icons.Default.Close, contentDescription = "選択解除")
                                }
                            }
                        } else {
                            Text(
                                text = "服薬履歴",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (intakeRecords.isNotEmpty()) {
                                TextButton(onClick = { viewModel.enterSelectMode() }) {
                                    Text("選択")
                                }
                            }
                        }
                    }
                }

                // History records
                items(intakeRecords.take(30)) { record ->
                    HistoryRecordRow(
                        record = record,
                        isSelectMode = isSelectMode,
                        isSelected = record.id in selectedRecordIds,
                        onSelect = { viewModel.toggleRecordSelection(record.id) }
                    )
                }
            }

            // App Log Panel as an overlay at the bottom
            if (showAppLogsOverlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    AppLogCard(
                        logs = appLogs,
                        onDismiss = { showAppLogsOverlay = false },
                        onClearLogs = { viewModel.clearLogs() },
                        onSaveLogs = { viewModel.saveLogs(context) }
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleOverviewCard(
    schedules: List<MedicineSchedule>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScheduleType.entries.forEach { type ->
                val schedule = schedules.find { it.id == type.id }
                val enabled = schedule?.enabled ?: false

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = if (enabled && schedule != null) {
                            "%02d:%02d-%02d:%02d".format(schedule.startHour, schedule.startMinute, schedule.endHour, schedule.endMinute)
                        } else {
                            "オフ"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                if (type != ScheduleType.EVENING) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryRecordRow(
    record: MedicineIntakeRecord,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MM月dd日（E）", Locale.JAPAN)
    val dateStr = dateFormat.format(Date(record.date * 1000))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelectMode) Modifier.clickable { onSelect() } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelectMode && isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                CardDefaults.cardColors().containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox indicator in select mode
            if (isSelectMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.weight(1f))

            // Show all 3 periods inline
            ScheduleType.entries.forEach { type ->
                val enabled = when (type) {
                    ScheduleType.MORNING -> record.morningEnabled
                    ScheduleType.AFTERNOON -> record.afternoonEnabled
                    ScheduleType.EVENING -> record.eveningEnabled
                }
                val taken = when (type) {
                    ScheduleType.MORNING -> record.morningTaken
                    ScheduleType.AFTERNOON -> record.afternoonTaken
                    ScheduleType.EVENING -> record.eveningTaken
                }
                
                Text(
                    text = if (!enabled) {
                        "　${type.displayName}"
                    } else if (taken) {
                        "✅${type.displayName}"
                    } else {
                        "⬜${type.displayName}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface 
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(start = 8.dp)
                )
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

    AlertDialog(
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
                            Button(
                                onClick = { viewModel.startBleScan() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("スキャン開始")
                            }
                        }
                    }
                    is BleManager.ConnectionState.Scanning -> {
                        Text("スキャン中...")
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    is BleManager.ConnectionState.Connecting -> {
                        Text("接続中...")
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    is BleManager.ConnectionState.Connected -> {
                        val device = (bleConnectionState as BleManager.ConnectionState.Connected).device
                        Text("接続済: ${device.name}")

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { viewModel.syncTime() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("時刻同期")
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { viewModel.disconnectBle() },
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = { viewModel.connectToDevice(device) }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
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
                    TextButton(onClick = { viewModel.stopBleScan() }) {
                        Text("スキャン停止")
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("閉じる")
                    }
                }
            }
        }
    )
}

