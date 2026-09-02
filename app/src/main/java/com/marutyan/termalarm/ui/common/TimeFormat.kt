package com.marutyan.termalarm.ui.common

// AlarmSchedule.startMinutes/endMinutes(深夜0時からの経過分)を"7:00"のような表示用文字列へ変換する。
// 一覧画面・編集画面の両方で使うため共通化する(SPEC上の計算式ではなく単なる表示整形なのでdomain/には置かない)。
fun formatClockMinutes(minutesOfDay: Int): String {
    val hour = minutesOfDay / 60
    val minute = minutesOfDay % 60
    return "$hour:${minute.toString().padStart(2, '0')}"
}
