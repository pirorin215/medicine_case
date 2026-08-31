package com.pirorin215.medicinecasemob.ui.data

/**
 * アプリ全体の設定データクラス
 *
 * 枠設計（分単位統合）:
 * - 朝: morningStartMinute - afternoonStartMinute (例: 480分 - 600分)
 * - 昼: afternoonStartMinute - eveningStartMinute (例: 600分 - 1020分)
 * - 夜: eveningStartMinute - dayEndMinute (例: 1020分 - 1380分)
 *
 * UI制約: 各枠の境界が一致するよう調整（例: 朝の終了=昼の開始）
 *
 * 通知推奨時刻は朝昼夜の枠とは独立に最大3つ設定できる。
 * 有効な枠（enabled=true）の時間帯外に設定された推奨時刻は通知時に無視される。
 */
data class AppSettingsData(
    // スケジュール設定 - 朝
    val morningEnabled: Boolean,

    // スケジュール設定 - 昼
    val afternoonEnabled: Boolean,

    // スケジュール設定 - 夜
    val eveningEnabled: Boolean,

    // 通知推奨時刻（枠とは独立に最大3つ。有効枠外に設定した分は無視される）
    val preferredReminderMinute1: Int = DEFAULT_PREFERRED_REMINDER_MINUTES[0],
    val preferredReminderMinute2: Int = DEFAULT_PREFERRED_REMINDER_MINUTES[1],
    val preferredReminderMinute3: Int = DEFAULT_PREFERRED_REMINDER_MINUTES[2],
    // 推奨時刻ごとの通知済みフラグ（内部管理用、毎日0時リセット）
    val preferredNotified1: Boolean = false,
    val preferredNotified2: Boolean = false,
    val preferredNotified3: Boolean = false,

    // 枠設定（分単位統合）
    val morningStartMinute: Int = 8 * 60,       // 朝の開始（例: 08:00 → 480分）
    val afternoonStartMinute: Int = 10 * 60,    // 昼の開始（例: 10:00 → 600分）
    val eveningStartMinute: Int = 17 * 60,      // 夜の開始（例: 17:00 → 1020分）
    val dayEndMinute: Int = 23 * 60,            // 1日の終了（例: 23:00 → 1380分）

    // 通知設定
    val onlyNotifyWhenBleConnected: Boolean,
    val notificationIntervalMinutes: Int,
    val lastNotificationTimestamp: Int,

    // 検知設定
    val movementThreshold: Float,
    val cooldownTime: Long,

    // UI設定
    val uiFontSizeScale: Float,

    // 内部管理用（通知済みフラグ - 旧バージョン互換性維持）
    val notifiedAtEndOfMorning: Boolean = false,
    val notifiedAtEndOfAfternoon: Boolean = false,
    val notifiedAtEndOfEvening: Boolean = false,

    // 枠内チャンス通知（BLE接続時）の通知済みフラグ（枠につき1日1回）
    val chanceNotifiedMorning: Boolean = false,
    val chanceNotifiedAfternoon: Boolean = false,
    val chanceNotifiedEvening: Boolean = false,

    // 接続設定
    val lastDeviceAddress: String? = null
) {
    companion object {
        /** 推奨時刻のデフォルト値（分単位）: 09:00 / 13:00 / 20:00 */
        val DEFAULT_PREFERRED_REMINDER_MINUTES = listOf(9 * 60, 13 * 60, 20 * 60)
    }
}
