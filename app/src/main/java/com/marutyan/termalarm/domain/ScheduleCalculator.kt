package com.marutyan.termalarm.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

// 1日の分数。日またぎ判定・時刻計算で繰り返し使う定数
private const val MINUTES_PER_DAY = 1440

// repeatDaysが空でない場合に探索する最大日数（今日を含め最大15日分＝2週間強）。SPEC「最大14日先まで探して見つからなければnull」に対応
private const val MAX_SEARCH_DAYS_AHEAD = 14

// 深夜をまたぐセッションかどうか。endMinutesがstartMinutesより小さい場合は日をまたぐ扱いにする（SPEC「鳴動回数」節）
private fun crossesMidnight(schedule: AlarmSchedule): Boolean = schedule.endMinutes < schedule.startMinutes

// セッションの長さ(分)。日をまたぐ場合は24時間分を足して計算する
private fun sessionSpanMinutes(schedule: AlarmSchedule): Int =
    if (!crossesMidnight(schedule)) {
        schedule.endMinutes - schedule.startMinutes
    } else {
        schedule.endMinutes + MINUTES_PER_DAY - schedule.startMinutes
    }

/**
 * 1セッションで実際に鳴る回数（occurrence数）を返す。
 * startMinutes・endMinutesの両端を含むため span/interval + 1 になる（docs/SPEC.md「鳴動回数」）。
 */
fun occurrenceCount(schedule: AlarmSchedule): Int =
    sessionSpanMinutes(schedule) / schedule.intervalMinutes + 1

// セッション開始日の深夜0時から数えて、index番目(0始まり)の鳴動までの経過分
private fun occurrenceOffsetMinutes(schedule: AlarmSchedule, index: Int): Int =
    schedule.startMinutes + index * schedule.intervalMinutes

// sessionStartDateに始まるセッションのうち、index番目(0始まり)の鳴動時刻。
// plusMinutesの繰り上がりにより、日をまたぐ場合は自動的に翌日の日時になる
private fun occurrenceDateTime(schedule: AlarmSchedule, sessionStartDate: LocalDate, index: Int): LocalDateTime =
    LocalDateTime.of(sessionStartDate, LocalTime.MIDNIGHT)
        .plusMinutes(occurrenceOffsetMinutes(schedule, index).toLong())

/**
 * 瞬間atが属するセッションの開始日を求める。
 * 日をまたぐスケジュールで、atの時刻がstartMinutesより前（＝日をまたいだ後の時間帯）にある場合は前日が開始日になる。
 * 「今日はもう止める」でskippedSessionStartへ書き込む値や、残り鳴動回数の計算に使う（docs/SPEC.md「用語」）。
 */
fun sessionStartDate(schedule: AlarmSchedule, at: ZonedDateTime): LocalDate {
    val minuteOfDay = at.hour * 60 + at.minute
    return if (crossesMidnight(schedule) && minuteOfDay < schedule.startMinutes) {
        at.toLocalDate().minusDays(1)
    } else {
        at.toLocalDate()
    }
}

/**
 * nowより厳密に後（同時刻は含めない）で最も早い鳴動時刻を返す。
 * enabledがfalseならnull。skippedSessionStartと開始日が一致するセッションの鳴動は飛ばす。
 * repeatDaysが空なら「次の1回だけ」を意味するため、今日と（日またぎ考慮のため）前日のセッションのみを調べ、
 * それが過ぎていればnull（自動的に無効化される想定）。
 * repeatDaysが指定されていれば、該当曜日のセッション開始日を今日から最大14日先まで順に調べる。
 * タイムゾーン・DSTの解決はZonedDateTime.atZoneの既定動作に委ねる
 * （存在しない時刻はギャップ分繰り上げ、重複する時刻は繰り上げ前＝早い方のオフセットを採用する）。
 */
fun nextTrigger(schedule: AlarmSchedule, now: ZonedDateTime): ZonedDateTime? {
    if (!schedule.enabled) return null

    // 日またぎスケジュールは前日に始まったセッションがまだ終わっていない可能性があるため -1 日から調べる。
    // 日をまたがない場合、前日のセッションの鳴動は必ずnow以前になるため実害はない
    val dayOffsets = if (schedule.repeatDays.isEmpty()) -1..0 else -1..MAX_SEARCH_DAYS_AHEAD

    for (dayOffset in dayOffsets) {
        val sessionStart = now.toLocalDate().plusDays(dayOffset.toLong())

        if (schedule.repeatDays.isNotEmpty() && sessionStart.dayOfWeek !in schedule.repeatDays) continue
        if (sessionStart == schedule.skippedSessionStart) continue

        val count = occurrenceCount(schedule)
        for (index in 0 until count) {
            val candidate = occurrenceDateTime(schedule, sessionStart, index).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
        }
    }
    return null
}

/**
 * atで鳴っている回より後に残っている、そのセッション内の鳴動回数（現在鳴っている回は含めない）。
 * 例: 7:00〜9:00・5分間隔（全25回）で7:05（2回目）が鳴っている場合、7:10〜9:00の23回を返す。
 * 鳴動画面の「あと23回」表示と、「今日はもう止める」でキャンセルする対象回数に使う
 * （docs/SPEC.md「追記: 残り鳴動回数の数え方」）。atは実際の鳴動時刻と一致している前提。
 */
fun remainingOccurrenceCount(schedule: AlarmSchedule, at: ZonedDateTime): Int {
    val sessionStart = sessionStartDate(schedule, at)
    // セッション開始日の深夜0時からatまでの経過分。sessionStartDateの選び方によりstartMinutes以上になる
    val elapsedMinutes = Duration.between(LocalDateTime.of(sessionStart, LocalTime.MIDNIGHT), at.toLocalDateTime()).toMinutes()
    val index = ((elapsedMinutes - schedule.startMinutes) / schedule.intervalMinutes).toInt()
    val count = occurrenceCount(schedule)
    return (count - 1 - index).coerceAtLeast(0)
}

/**
 * 一覧画面に表示する要約文字列を組み立てる。間隔と1セッションあたりの鳴動回数を1行にまとめる。
 * 例: 「5分ごと · 25回」「1回のみ」（単発に退化する場合）。
 */
fun scheduleSummary(schedule: AlarmSchedule): String {
    val count = occurrenceCount(schedule)
    return if (count <= 1) "1回のみ" else "${schedule.intervalMinutes}分ごと · ${count}回"
}
