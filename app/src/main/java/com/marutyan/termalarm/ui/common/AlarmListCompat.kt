package com.marutyan.termalarm.ui.alarmlist

import java.time.DayOfWeek

/**
 * ui/alarmedit/ および timer/ からの古い参照を解決するための互換定義。
 * 後続タスクで該当コードが刷新された際に削除する。
 */
enum class TermAlarmTab {
    ALARM,
    CLOCK,
    TIMER,
    STOPWATCH,
}

fun orderedDaysOfWeek(): List<DayOfWeek> = DayOfWeek.entries
