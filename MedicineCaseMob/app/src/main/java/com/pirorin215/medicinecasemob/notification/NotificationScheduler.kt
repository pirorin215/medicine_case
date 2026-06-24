package com.pirorin215.medicinecasemob.notification

import android.content.Context
import com.pirorin215.medicinecasemob.util.LogManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pirorin215.medicinecasemob.ui.data.MedicineRepository
import com.pirorin215.medicinecasemob.ui.data.ScheduleType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch
import java.util.Calendar

@HiltWorker
class NotificationScheduler @AssistedInject constructor(
    private val repository: MedicineRepository,
    private val notificationService: NotificationService,
    private val bleManager: com.pirorin215.medicinecasemob.ble.BleManager,
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NotificationScheduler"
        const val WORK_NAME = "medicine_reminder_check"
    }

    override suspend fun doWork(): Result {
        val log = LogManager.getInstance()
        log.d(TAG, "Scheduled notification check running")

        return try {
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            // Reset notification flags at midnight
            if (currentHour == 0) {
                log.d(TAG, "Resetting notification flags at midnight")
                repository.resetDailyNotificationFlags()
            }

            val settings = repository.settingsFlow.first()
            val schedules = repository.getSchedulesFromSettings(settings)

            // 安全網: フォアグラウンドサービスが死んでいても、未服薬枠があれば
            // WorkManager から直接 BLE 取得を試みて DB を最新化する。
            // これにより「アプリを開かないと服薬情報が取得されない」状態を防ぐ。
            if (hasUntakenCurrentSlot(schedules)) {
                log.d(TAG, "Untaken slot detected; attempting background BLE fetch")
                tryFetchIntakeFromBle()
            }

            // 取得後の最新 DB 状態で通知判定（ensureTodayRecordExists は最新レコードを再取得する）
            val todayRecord = repository.ensureTodayRecordExists()
            val isConnectedToBle = bleManager.connectionState.value is com.pirorin215.medicinecasemob.ble.BleManager.ConnectionState.Connected

            notificationService.checkAndNotifyMissedIntakes(
                schedules = schedules,
                todayRecord = todayRecord,
                isConnectedToBle = isConnectedToBle,
                forceNotification = false
            )

            Result.success()
        } catch (e: Exception) {
            log.e(TAG, "Error in notification check: " + e.message)
            Result.failure()
        }
    }

    /**
     * 現在時刻（1分先読み）に未服薬の枠があるか。
     * 取得の必要性判定にのみ使用する。
     */
    private suspend fun hasUntakenCurrentSlot(
        schedules: List<com.pirorin215.medicinecasemob.ui.data.MedicineSchedule>
    ): Boolean {
        val log = LogManager.getInstance()
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE) + 1 // 1分先読み

        val slot = schedules
            .filter { it.enabled }
            .find { currentMinutes in it.startMinuteOfDay until it.endMinuteOfDay } ?: run {
            log.d(TAG, "hasUntakenCurrentSlot: no active slot at $currentMinutes")
            return false
        }

        val scheduleType = ScheduleType.fromId(slot.id) ?: return false
        val todayStart = repository.getTodayStartTimestamp()
        val todayRecord = repository.getIntakeRecordByDateSync(todayStart)
        val taken = todayRecord?.isTaken(scheduleType) == true
        log.d(TAG, "hasUntakenCurrentSlot: slot=$scheduleType taken=$taken")
        return !taken
    }

    /**
     * BLE 接続を試みて最新の服薬状態を取得し DB へ反映する安全網。
     *
     * - サービス稼働中で接続済みなら queryIntake のみ（DB反映はサービスのObserverに任せる）。
     * - 未接続なら自前でスキャン→接続→queryIntake を行い、終了後に切断する。
     *   サービスのObserverが存在しない可能性があるため、結果は自前で DB に記録する。
     *
     * 注意: 稀にこの取得中にユーザがアプリを開いてサービスが起動すると接続が競合し得るが、
     * サービスのスキャンループが数秒で再接続して自己回復するため許容する。
     */
    private suspend fun tryFetchIntakeFromBle() {
        val log = LogManager.getInstance()

        if (!bleManager.isBluetoothEnabled()) {
            log.d(TAG, "tryFetchIntakeFromBle: Bluetooth disabled, skip")
            return
        }

        val initialState = bleManager.connectionState.value
        val alreadyConnected = initialState is com.pirorin215.medicinecasemob.ble.BleManager.ConnectionState.Connected
        var weConnected = false

        try {
            if (alreadyConnected) {
                // サービスが接続を保持している: クエリのみ
                if (bleManager.serviceReady.value) {
                    val result = bleManager.queryIntake(timeoutMs = 3000L)
                    log.d(TAG, "Worker fetch (already connected): $result")
                    recordIntakeResult(result)
                    delay(1000)
                }
                return
            }

            // 未接続: 自前で接続を試みる
            weConnected = true
            val settings = repository.settingsFlow.first()
            // スキャン(最大10s) + 接続/サービス発見(数s) を待つ。
            // startScan がアドレス直結せずスキャンに落ちた場合は、ここでスキャン結果を
            // 監視してデバイス発見時に接続する（発見→接続は通常サービスの役割）。
            val ready = withTimeoutOrNull(20000L) {
                val watcher = launch {
                    bleManager.scanResults.collect { results ->
                        if (results.isEmpty()) return@collect
                        if (bleManager.connectionState.value is com.pirorin215.medicinecasemob.ble.BleManager.ConnectionState.Disconnected) {
                            results.firstOrNull {
                                it.device.name?.startsWith(com.pirorin215.medicinecasemob.ble.BleManager.DEVICE_NAME_PREFIX) == true
                            }?.let { bleManager.connectToDevice(it.device) }
                        }
                    }
                }
                try {
                    bleManager.startScan(settings.lastDeviceAddress)
                    bleManager.serviceReady.first { it }
                } finally {
                    watcher.cancel()
                }
            } != null

            if (!ready) {
                log.d(TAG, "Worker fetch: could not connect within timeout")
                return
            }

            val result = bleManager.queryIntake(timeoutMs = 3000L)
            log.d(TAG, "Worker fetch (one-shot connected): $result")
            recordIntakeResult(result)
            delay(1000) // DB書き込みを確定させるため少し待つ
        } catch (e: Exception) {
            log.e(TAG, "Worker fetch error: ${e.message}")
        } finally {
            if (weConnected) {
                bleManager.stopScan()
                bleManager.disconnect()
            }
        }
    }

    /**
     * queryIntake の結果を DB に記録する。
     * "INTAKE:<ts>" のみ記録し、"NONE"/null は無視する。
     */
    private suspend fun recordIntakeResult(result: String?) {
        val log = LogManager.getInstance()
        if (result == null) {
            log.d(TAG, "recordIntakeResult: no response")
            return
        }
        if (result.startsWith("INTAKE:")) {
            val ts = result.removePrefix("INTAKE:").toLongOrNull() ?: 0L
            repository.recordIntakeEvent(ts)?.let { confirmed ->
                // 対象スロットに新規で服薬チェックが付いたら確認通知を送る
                notificationService.notifyIntakeConfirmed(confirmed)
                log.d(TAG, "Intake confirmed notification sent for $confirmed")
            }
        } else {
            log.d(TAG, "recordIntakeResult: $result (no intake)")
        }
    }
}
