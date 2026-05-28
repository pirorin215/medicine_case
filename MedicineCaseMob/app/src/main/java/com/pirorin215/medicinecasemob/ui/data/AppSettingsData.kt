package com.pirorin215.medicinecasemob.ui.data

/**
 * アプリ全体の設定データクラス
 *
 * 新しい枠設計（分単位統合）:
 * - 朝: morningStartMinute - afternoonStartMinute (例: 480分 - 600分)
 * - 昼: afternoonStartMinute - eveningStartMinute (例: 600分 - 1020分)
 * - 夜: eveningStartMinute - dayEndMinute (例: 1020分 - 1380分)
 *
 * UI制約: 各枠の境界が一致するよう調整（例: 朝の終了=昼の開始）
 */
data class AppSettingsData(
    // スケジュール設定 - 朝
    val morningEnabled: Boolean,
    val morningReminderMinute: Int = 9 * 60,  // デフォルト: 09:00 (540分)
    val morningNotifiedInSlot: Boolean = false,  // 枠内通知済みフラグ（内部管理用）

    // スケジュール設定 - 昼
    val afternoonEnabled: Boolean,
    val afternoonReminderMinute: Int = 13 * 60,  // デフォルト: 13:00 (780分)
    val afternoonNotifiedInSlot: Boolean = false,  // 枠内通知済みフラグ（内部管理用）

    // スケジュール設定 - 夜
    val eveningEnabled: Boolean,
    val eveningReminderMinute: Int = 20 * 60,  // デフォルト: 20:00 (1200分)
    val eveningNotifiedInSlot: Boolean = false,  // 枠内通知済みフラグ（内部管理用）

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

    // 接続設定
    val lastDeviceAddress: String? = null
)
