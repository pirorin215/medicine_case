package com.pirorin215.medicinecasemob.ui.screen

import android.app.TimePickerDialog
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.pirorin215.medicinecasemob.ui.data.AppSettingsData
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import com.pirorin215.medicinecasemob.ui.viewModel.ScheduleSettingsViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScheduleSettingsViewModel = hiltViewModel()
) {
    val schedules by viewModel.schedules.collectAsState(initial = emptyList())
    val preferredReminderMinutes by viewModel.preferredReminderMinutes.collectAsState(initial = emptyList())
    val onlyNotifyWhenBleConnected by viewModel.onlyNotifyWhenBleConnected.collectAsState(initial = false)
    val notificationIntervalMinutes by viewModel.notificationIntervalMinutes.collectAsState(initial = 60)
    val uiFontSizeScale by viewModel.uiFontSizeScale.collectAsState()
    val context = LocalContext.current

    var showTimeRangeDialog by remember { mutableStateOf(false) }
    var timeRangeDialogSchedule by remember { mutableStateOf<ScheduleType?>(null) }
    var timeRangeDialogState by remember { mutableStateOf<TimeRangeDialogState?>(null) }
    var showNotificationIntervalDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("アプリ設定") },
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
            // --- Schedule Settings ---
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "服薬スケジュール",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "服薬時刻を過ぎても服薬されていない場合に通知します",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ScheduleType.entries.forEach { type ->
                        // Use DB data if available, otherwise use defaults
                        val schedule = schedules.find { it.id == type.id }
                        val enabled = schedule?.enabled ?: true
                        val startHour = schedule?.startHour ?: type.defaultStartHour
                        val startMinute = schedule?.startMinute ?: type.defaultStartMinute
                        val endHour = schedule?.endHour ?: type.defaultEndHour
                        val endMinute = schedule?.endMinute ?: type.defaultEndMinute

                        ScheduleRow(
                            type = type,
                            enabled = enabled,
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                            onEnabledChange = { viewModel.updateScheduleEnabled(type, it) },
                            onTimeClick = {
                                timeRangeDialogSchedule = type
                                timeRangeDialogState = TimeRangeDialogState(
                                    startHour = startHour,
                                    startMinute = startMinute,
                                    endHour = endHour,
                                    endMinute = endMinute
                                )
                                showTimeRangeDialog = true
                            }
                        )

                        if (type != ScheduleType.EVENING) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // --- Preferred Reminder Times (枠とは独立した3つの時刻) ---
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "通知推奨時刻",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "設定した時刻に服薬されていない場合に通知します（有効な枠の時間帯外に設定した時刻は無視されます）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    listOf(1, 2, 3).forEach { index ->
                        val defaultMinute = AppSettingsData.DEFAULT_PREFERRED_REMINDER_MINUTES[index - 1]
                        val minuteOfDay = preferredReminderMinutes.getOrNull(index - 1) ?: defaultMinute

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "推奨時刻$index",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m -> viewModel.updatePreferredReminderTime(index, h, m) },
                                        minuteOfDay / 60,
                                        minuteOfDay % 60,
                                        true
                                    ).show()
                                }
                            ) {
                                Text(
                                    text = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }


            // --- Notification Settings ---
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "通知設定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BLE接続時のみ通知",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "ケースと接続できない時は通知を抑制します",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = onlyNotifyWhenBleConnected,
                            onCheckedChange = { viewModel.updateOnlyNotifyWhenBleConnected(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "通知リマインダー間隔",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "服薬忘れのリマインダー通知の間隔を設定します",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showNotificationIntervalDialog = true }
                        ) {
                            Text(
                                text = "${notificationIntervalMinutes}分",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    }
                    }


                    // --- UI Settings ---
                    Card(
                    modifier = Modifier.fillMaxWidth()
                    ) {
                    Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                    ) {
                    Text(
                        text = "表示設定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "文字サイズ: ${String.format("%.1f", uiFontSizeScale)}倍",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "画面全体の文字サイズを調整します（デフォルト1.2倍）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = uiFontSizeScale,
                        onValueChange = { viewModel.updateUiFontSizeScale(it) },
                        valueRange = 0.8f..2.0f,
                        steps = 11 // 0.1 increments
                    )
                    }
                    }


                    // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "設定について",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• スケジュール: 時間帯を設定して服薬記録を管理\n" +
                              "• 各時間帯で開始時刻と終了時刻を設定可能\n" +
                              "• 通知は終了時刻に送信されます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Notification interval dialog
    if (showNotificationIntervalDialog) {
        NotificationIntervalDialog(
            currentMinutes = notificationIntervalMinutes,
            onDismiss = { showNotificationIntervalDialog = false },
            onConfirm = { minutes ->
                viewModel.updateNotificationIntervalMinutes(minutes)
                showNotificationIntervalDialog = false
            }
        )
    }

    // Time range dialog
    if (showTimeRangeDialog && timeRangeDialogSchedule != null && timeRangeDialogState != null) {
        TimeRangeDialog(
            schedule = timeRangeDialogSchedule!!,
            state = timeRangeDialogState!!,
            onDismiss = { showTimeRangeDialog = false },
            onConfirm = { startH, startM, endH, endM ->
                viewModel.updateScheduleTimeRange(timeRangeDialogSchedule!!, startH, startM, endH, endM)
                showTimeRangeDialog = false
            }
        )
    }
}

@Composable
private fun ScheduleRow(
    type: ScheduleType,
    enabled: Boolean,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onEnabledChange: (Boolean) -> Unit,
    onTimeClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Schedule name and switch
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            // Time range button
            TextButton(
                onClick = onTimeClick,
                enabled = enabled
            ) {
                Text(
                    text = "%02d:%02d 〜 %02d:%02d".format(startHour, startMinute, endHour, endMinute),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

@Composable
private fun TimeRangeDialog(
    schedule: ScheduleType,
    state: TimeRangeDialogState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int, Int) -> Unit
) {
    var tempStartHour by remember { mutableStateOf(state.startHour) }
    var tempStartMinute by remember { mutableStateOf(state.startMinute) }
    var tempEndHour by remember { mutableStateOf(state.endHour) }
    var tempEndMinute by remember { mutableStateOf(state.endMinute) }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${schedule.displayName}の時間帯を設定") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start time
                Text(
                    text = "開始時刻",
                    style = MaterialTheme.typography.titleSmall
                )
                Button(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                tempStartHour = h
                                tempStartMinute = m
                            },
                            tempStartHour,
                            tempStartMinute,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("%02d:%02d".format(tempStartHour, tempStartMinute))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // End time
                Text(
                    text = "終了時刻",
                    style = MaterialTheme.typography.titleSmall
                )
                Button(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                tempEndHour = h
                                tempEndMinute = m
                            },
                            tempEndHour,
                            tempEndMinute,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("%02d:%02d".format(tempEndHour, tempEndMinute))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Validate: end time must be after start time
                    val startMinutes = tempStartHour * 60 + tempStartMinute
                    val endMinutes = tempEndHour * 60 + tempEndMinute

                    if (endMinutes > startMinutes) {
                        onConfirm(tempStartHour, tempStartMinute, tempEndHour, tempEndMinute)
                    } else {
                        Log.e("ScheduleSettingsScreen", "Invalid time range: end must be after start")
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun NotificationIntervalDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var tempMinutes by remember { mutableStateOf(currentMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知リマインダー間隔を設定") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "リマインダー通知の間隔（分）を設定してください",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "間隔（分）",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { if (tempMinutes > 1) tempMinutes-- },
                            enabled = tempMinutes > 1
                        ) {
                            Text("-")
                        }
                        Text(
                            text = "$tempMinutes",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Button(
                            onClick = { if (tempMinutes < 1440) tempMinutes++ },
                            enabled = tempMinutes < 1440
                        ) {
                            Text("+")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tempMinutes) }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

data class TimeRangeDialogState(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)
