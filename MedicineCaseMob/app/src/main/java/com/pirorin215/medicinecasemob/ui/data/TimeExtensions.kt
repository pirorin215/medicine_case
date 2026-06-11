package com.pirorin215.medicinecasemob.ui.data

/**
 * 日中分（例: 540 = 9:00）を "HH:mm" 形式の文字列に変換する拡張関数。
 * ScheduleSettingsViewModel 等で重複していたフォーマット処理を統一。
 */
fun Int.formatTimeOfDay(): String = String.format("%02d:%02d", this / 60, this % 60)

/**
 * 日中分の範囲を "HH:mm-HH:mm" 形式に変換。
 */
fun formatTimeRange(startMinuteOfDay: Int, endMinuteOfDay: Int): String =
    "${startMinuteOfDay.formatTimeOfDay()}-${endMinuteOfDay.formatTimeOfDay()}"
