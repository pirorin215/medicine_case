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
    val schedules by viewModel.schedules.collectAsState()
    val context = LocalContext.current

    var showTimeRangeDialog by remember { mutableStateOf(false) }
    var timeRangeDialogSchedule by remember { mutableStateOf<ScheduleType?>(null) }
    var timeRangeDialogState by remember { mutableStateOf<TimeRangeDialogState?>(null) }

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

data class TimeRangeDialogState(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)
