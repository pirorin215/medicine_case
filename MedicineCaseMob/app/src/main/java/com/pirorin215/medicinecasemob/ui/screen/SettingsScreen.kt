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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import com.pirorin215.medicinecasemob.ui.viewModel.SettingsViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val detectionSettings by viewModel.detectionSettings.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val context = LocalContext.current

    // Auto-clear success message after 5 seconds
    LaunchedEffect(saveSuccess) {
        if (saveSuccess != null) {
            Log.d("SettingsScreen", "Showing message: $saveSuccess")
            delay(5000)
            viewModel.clearSaveSuccess()
            Log.d("SettingsScreen", "Message cleared after 5 seconds")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
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
            // Save success message
            saveSuccess?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.contains("失敗")) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

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

                    ScheduleType.values().forEach { type ->
                        // Use DB data if available, otherwise use defaults
                        val schedule = schedules.find { it.id == type.id }
                        val enabled = schedule?.enabled ?: true
                        val hour = schedule?.hour ?: type.defaultHour
                        val minute = schedule?.minute ?: 0

                        ScheduleRow(
                            type = type,
                            enabled = enabled,
                            hour = hour,
                            minute = minute,
                            onEnabledChange = { viewModel.updateScheduleEnabled(type, it) },
                            onTimeClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> viewModel.updateScheduleTime(type, h, m) },
                                    hour,
                                    minute,
                                    true
                                ).show()
                            }
                        )

                        if (type != ScheduleType.EVENING) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // --- Detection Settings ---
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "服薬検知設定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Movement Threshold Slider
                    detectionSettings?.let { settings ->
                        Text(
                            text = "検出角度: ${settings.movementThreshold.toInt()}°",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "薬ケースを何度傾けたら服薬と判定するか",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = settings.movementThreshold,
                            onValueChange = { viewModel.updateMovementThreshold(it) },
                            valueRange = 10f..180f,
                            steps = 170
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Cooldown Time Slider
                        val cooldownSeconds = settings.cooldownTime / 1000
                        Text(
                            text = "クールダウン: ${cooldownSeconds.toInt()}秒",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "服薬検知後、何秒間は次の検知を無視するか",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = cooldownSeconds.toFloat(),
                            onValueChange = { viewModel.updateCooldownTime((it * 1000).toLong()) },
                            valueRange = 1f..300f,
                            steps = 299
                        )
                    }
                }
            }

            // Save Button
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("保存中...")
                } else {
                    Text("設定を保存")
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
                        text = "• スケジュール: 服薬時刻を設定し、通知を受け取れます\n" +
                              "• 検出角度: 小さくすると少しの傾きでも検知されます（10-180度）\n" +
                              "• クールダウン: 連続検知を防ぐための待機時間（1-300秒）\n" +
                              "• 検知設定はマイコンとスマホの両方に保存されます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    type: ScheduleType,
    enabled: Boolean,
    hour: Int,
    minute: Int,
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

        // Time picker button
        TextButton(
            onClick = onTimeClick,
            enabled = enabled
        ) {
            Text(
                text = String.format("%02d:%02d", hour, minute),
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
