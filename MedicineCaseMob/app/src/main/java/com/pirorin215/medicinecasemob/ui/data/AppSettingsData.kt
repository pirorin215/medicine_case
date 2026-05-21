package com.pirorin215.medicinecasemob.ui.data

/**
 * アプリ全体の設定データクラス
 */
data class AppSettingsData(
    // スケジュール設定 - 朝
    val morningEnabled: Boolean,
    val morningStartHour: Int,
    val morningStartMinute: Int,
    val morningEndHour: Int,
    val morningEndMinute: Int,
    val morningReminderHour: Int = 9,
    val morningReminderMinute: Int = 0,

    // スケジュール設定 - 昼
    val afternoonEnabled: Boolean,
    val afternoonStartHour: Int,
    val afternoonStartMinute: Int,
    val afternoonEndHour: Int,
    val afternoonEndMinute: Int,
    val afternoonReminderHour: Int = 13,
    val afternoonReminderMinute: Int = 0,

    // スケジュール設定 - 夜
    val eveningEnabled: Boolean,
    val eveningStartHour: Int,
    val eveningStartMinute: Int,
    val eveningEndHour: Int,
    val eveningEndMinute: Int,
    val eveningReminderHour: Int = 20,
    val eveningReminderMinute: Int = 0,

    // 通知設定
    val onlyNotifyWhenBleConnected: Boolean,
    val notificationIntervalMinutes: Int,
    val lastNotificationTimestamp: Int,

    // 検知設定
    val movementThreshold: Float,
    val cooldownTime: Long,

    // UI設定
    val uiFontSizeScale: Float,

    // 内部管理用（通知済みフラグ）
    val notifiedAtEndOfMorning: Boolean,
    val notifiedAtEndOfAfternoon: Boolean,
    val notifiedAtEndOfEvening: Boolean,

    // 内部管理用（枠内通知済みフラグ）
    val notifiedInSlotMorning: Boolean = false,
    val notifiedInSlotAfternoon: Boolean = false,
    val notifiedInSlotEvening: Boolean = false
)
