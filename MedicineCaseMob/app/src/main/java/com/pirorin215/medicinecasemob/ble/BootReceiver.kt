package com.pirorin215.medicinecasemob.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pirorin215.medicinecasemob.util.LogManager

/**
 * 端末起動時 (BOOT_COMPLETED) およびアプリ更新時 (MY_PACKAGE_REPLACED) に
 * MedicineBleScanService を自動起動するレシーバ。
 *
 * これまでは MainActivity からのみサービスが起動されていたため、再起動やアプリ更新後は
 * ユーザがアプリを開かない限りバックグラウンドでの服薬取得が行われない状態だった。
 *
 * connectedDevice 型の FGS は BOOT_COMPLETED からの起動が許可されている型であり
 * (制限対象は camera/microphone/dataSync/mediaPlayback/phoneCall/mediaProjection のみ)、
 * サービスの onStartCommand が即座に startForeground を呼ぶため制限にも適合する。
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val log = LogManager.getInstance()
        val action = intent.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        log.d(TAG, "Received $action - starting MedicineBleScanService")

        val serviceIntent = Intent(context, MedicineBleScanService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            log.d(TAG, "Service start requested")
        } catch (e: Exception) {
            // 起動制限などで失敗した場合は次回の WorkManager サイクルまたはアプリ起動時に回復する
            log.e(TAG, "Failed to start service on $action: ${e.message}")
        }
    }
}
