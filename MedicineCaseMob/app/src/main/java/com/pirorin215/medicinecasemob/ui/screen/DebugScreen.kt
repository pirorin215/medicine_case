package com.pirorin215.medicinecasemob.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pirorin215.medicinecasemob.ble.BleManager
import com.pirorin215.medicinecasemob.ui.viewModel.DebugViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onNavigateBack: () -> Unit,
    viewModel: DebugViewModel = hiltViewModel()
) {
    val bleConnectionState by viewModel.bleConnectionState.collectAsState()
    val serviceReady by viewModel.serviceReady.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val intakeHistory by viewModel.filteredHistory.collectAsState()
    val latestFirmwareResponse by viewModel.latestFirmwareResponse.collectAsState()
    val firmwareVersion by viewModel.firmwareVersion.collectAsState()
    val selectedCommand by viewModel.selectedPredefinedCommand.collectAsState()
    val manualCommandInput by viewModel.manualCommandInput.collectAsState()
    val historyFilter by viewModel.historyFilter.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("マイコンデバッグ") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // BLE connection state
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BLE接続状態",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = viewModel.getDeviceInfo(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Service Ready: $serviceReady",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (serviceReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Firmware Version: ${firmwareVersion ?: "不明"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Microcontroller control
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "マイコン制御",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Predefined Command Section
                    Text(
                        text = "定型コマンド",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            val selectedLabel = viewModel.predefinedCommands.find { it.first == selectedCommand }?.second ?: selectedCommand
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                viewModel.predefinedCommands.forEach { (cmd, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            viewModel.onPredefinedCommandSelected(cmd)
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.executePredefinedCommand() },
                            enabled = serviceReady
                        ) {
                            Text("実行")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Manual Command Section
                    Text(
                        text = "手動コマンド送信",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualCommandInput,
                            onValueChange = { viewModel.onManualCommandInputChange(it) },
                            placeholder = { Text("例: SET:time:1716531234", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true
                        )

                        Button(
                            onClick = { viewModel.sendManualCommand() },
                            enabled = serviceReady && manualCommandInput.isNotBlank()
                        ) {
                            Text("送信")
                        }
                    }
                }
            }

            // MCU response history
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "マイコン応答履歴",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${intakeHistory.size}件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HistoryFilterButton(
                            text = "ALL",
                            selected = historyFilter == com.pirorin215.medicinecasemob.ui.viewModel.HistoryFilter.ALL,
                            onClick = { viewModel.setHistoryFilter(com.pirorin215.medicinecasemob.ui.viewModel.HistoryFilter.ALL) }
                        )
                        HistoryFilterButton(
                            text = "time",
                            selected = historyFilter == com.pirorin215.medicinecasemob.ui.viewModel.HistoryFilter.TIME,
                            onClick = { viewModel.setHistoryFilter(com.pirorin215.medicinecasemob.ui.viewModel.HistoryFilter.TIME) }
                        )
                        HistoryFilterButton(
                            text = "detection",
                            selected = historyFilter == com.pirorin215.medicinecasemob.ui.viewModel.HistoryFilter.DETECTION,
                            onClick = { viewModel.setHistoryFilter(com.pirorin215.medicinecasemob.ui.viewModel.HistoryFilter.DETECTION) }
                        )
                        HistoryFilterButton(
                            text = "intake",
                            selected = historyFilter == com.pirorin215.medicinecasemob.ui.viewModel.HistoryFilter.INTAKE,
                            onClick = { viewModel.setHistoryFilter(com.pirorin215.medicinecasemob.ui.viewModel.HistoryFilter.INTAKE) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (intakeHistory.isEmpty()) {
                        Text(
                            text = "履歴なし",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            items(intakeHistory.size) { index ->
                                val event = intakeHistory[index]
                                IntakeHistoryItem(event, viewModel)
                                if (index < intakeHistory.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Scan results
            if (scanResults.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "スキャン結果 (${scanResults.size}件)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        scanResults.forEach { result ->
                            val device = result.device
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "名前: ${device.name ?: "不明"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "アドレス: ${device.address}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "RSSI: ${result.rssi} dBm",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
        )
    }
}

@Composable
private fun IntakeHistoryItem(
    event: com.pirorin215.medicinecasemob.ui.viewModel.IntakeEventHistoryItem,
    viewModel: DebugViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                event.rawEvent.startsWith("OK:") -> MaterialTheme.colorScheme.secondaryContainer
                event.rawEvent.startsWith("ERR:") -> MaterialTheme.colorScheme.surfaceVariant
                event.rawEvent.startsWith("INTAKE:") -> MaterialTheme.colorScheme.primaryContainer
                event.rawEvent == "NONE" -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Received time
            Text(
                text = "受信: ${viewModel.formatReceivedTime(event.receivedAt)}",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Raw event
            Text(
                text = "応答: ${event.rawEvent}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = when {
                    event.rawEvent.startsWith("OK:") -> MaterialTheme.colorScheme.primary
                    event.rawEvent.startsWith("ERR:") -> MaterialTheme.colorScheme.error
                    event.rawEvent.startsWith("INTAKE:") -> MaterialTheme.colorScheme.primary
                    event.rawEvent == "NONE" -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            // Additional info for INTAKE events
            if (event.rawEvent.startsWith("INTAKE:")) {
                Spacer(modifier = Modifier.height(4.dp))

                // MCU timestamp
                Text(
                    text = "マイコン時刻: ${viewModel.formatTimestamp(event.mcuTimestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status
                Text(
                    text = if (event.wasRecorded) "✅ 記録済み" else "⬜ 範囲外",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (event.wasRecorded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // Schedule type
                if (event.scheduleType != null) {
                    Text(
                        text = "時間帯: ${event.scheduleType.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
