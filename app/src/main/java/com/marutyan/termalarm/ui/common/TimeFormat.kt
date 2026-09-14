package com.marutyan.termalarm.ui.common

import android.text.format.DateFormat
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.intervalSummary
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 時刻の見せ方を1か所へまとめる。このアプリは常に24時間表記にする。
 *
 * 一般的な時計アプリは端末の「24時間表示」設定に従い、12時間表記では「午後3:54」のように
 * 午前・午後を頭に付ける。ただしこのアプリは時間帯を「7:00–9:00」のように範囲で見せるため、
 * 午前・午後が付くと横に長くなって読み取りづらい。
 * 地域ごとの並び(区切り文字など)だけは端末に合わせる。
 */

/** 24時間表記の「時と分」の書式。withSecondsがtrueなら秒まで含める */
fun clockTimePattern(withSeconds: Boolean = false): String =
    DateFormat.getBestDateTimePattern(Locale.getDefault(), if (withSeconds) "Hms" else "Hm")

// AlarmSchedule.startMinutes/endMinutes(深夜0時からの経過分)を、端末の設定に合わせた表示用文字列へ変換する。
// 一覧画面・編集画面の両方で使うため共通化する(SPEC上の計算式ではなく単なる表示整形なのでdomain/には置かない)。
fun formatClockMinutes(minutesOfDay: Int, pattern: String): String =
    LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
        .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

/**
 * タームの範囲と間隔を1行にまとめる。例:「7:00 – 9:00 · 5分ごと」。
 *
 * 一覧・鳴動画面・当日終了のゲーム画面で同じ形にするため、ここだけで組み立てる。
 * 時刻の書き方は端末の設定に従うため、間隔の言い方(domain)と分けてここに置く。
 */
fun formatRangeAndInterval(schedule: AlarmSchedule, pattern: String): String {
    val start = formatClockMinutes(schedule.startMinutes, pattern)
    val end = formatClockMinutes(schedule.endMinutes, pattern)
    return "$start – $end · ${intervalSummary(schedule)}"
}
