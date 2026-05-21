package com.pirorin215.medicinecasemob.ui.viewModel

import androidx.lifecycle.ViewModel
import com.pirorin215.medicinecasemob.util.LogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppLogsViewModel @Inject constructor(
    private val logManager: LogManager
) : ViewModel() {

    val appLogs: StateFlow<List<String>> = logManager.logs

    fun refreshLogs() {
        // Logs are auto-collected by LogManager
    }

    fun clearLogs() {
        logManager.clearLogs()
    }
}
