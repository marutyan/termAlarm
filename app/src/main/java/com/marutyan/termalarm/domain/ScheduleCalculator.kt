package com.marutyan.termalarm.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.roundToInt

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
 * セッション全体の長さ span に対する経過分 t の進捗率 p (0.0..1.0) を求める。
 * span が 0 以下のときは 0.0 とする。
 */
internal fun calculateProgress(t: Int, span: Int): Double =
    if (span <= 0) 0.0 else t.toDouble() / span.toDouble()

/**
 * 開始時刻を0分としたセッション内の全鳴動時刻の経過分オフセット列を逐次生成する内部関数。
 * 可変間隔および等間隔のスケジュールから鳴動の列を算出し、回数算出や次回鳴動・残り回数の判定に共通で用いる。
 */
internal fun calculateOccurrenceOffsets(schedule: AlarmSchedule): List<Int> {
    val span = sessionSpanMinutes(schedule)
    if (span <= 0) return listOf(0)

    val isVariable = schedule.startIntervalMinutes != schedule.endIntervalMinutes
    val offsets = mutableListOf<Int>()
    var t = 0
    offsets.add(t)

    while (t < span) {
        val p = calculateProgress(t, span)
        val rawInterval = schedule.startIntervalMinutes + (schedule.endIntervalMinutes - schedule.startIntervalMinutes) * p
        val nextInterval = rawInterval.roundToInt().coerceAtLeast(1)
        val nextT = t + nextInterval

        if (nextT > span) {
            if (isVariable) {
                offsets.add(span)
            }
            break
        } else if (nextT == span) {
            offsets.add(span)
            break
        } else {
            offsets.add(nextT)
            t = nextT
        }
    }
    return offsets
}

/**
 * 1セッションで実際に鳴る回数（occurrence数）を返す。
 * 開始時刻から終了時刻までのオフセット列の件数をそのまま返す。
 */
fun occurrenceCount(schedule: AlarmSchedule): Int =
    calculateOccurrenceOffsets(schedule).size

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
 * repeatDaysが空なら「次の1回だけ」を意味する。今日のぶんがまだ来ていなければ今日、
 * 過ぎていれば翌日を返す（日またぎ考慮のため前日も調べる）。
 * 鳴り終わったあとに自分でオフにするかどうかは[isOneShotSessionFinished]が判断する。
 * repeatDaysが指定されていれば、該当曜日のセッション開始日を今日から最大14日先まで順に調べる。
 * タイムゾーン・DSTの解決はZonedDateTime.atZoneの既定動作に委ねる
 * （存在しない時刻はギャップ分繰り上げ、重複する時刻は繰り上げ前＝早い方のオフセットを採用する）。
 */
fun nextTrigger(schedule: AlarmSchedule, now: ZonedDateTime): ZonedDateTime? {
    if (!schedule.enabled) return null

    // 日またぎスケジュールは前日に始まったセッションがまだ終わっていない可能性があるため -1 日から調べる。
    // 日をまたがない場合、前日のセッションの鳴動は必ずnow以前になるため実害はない
    val dayOffsets = if (schedule.repeatDays.isEmpty()) -1..1 else -1..MAX_SEARCH_DAYS_AHEAD
    val offsets = calculateOccurrenceOffsets(schedule)

    for (dayOffset in dayOffsets) {
        val sessionStart = now.toLocalDate().plusDays(dayOffset.toLong())

        if (schedule.repeatDays.isNotEmpty() && sessionStart.dayOfWeek !in schedule.repeatDays) continue
        if (sessionStart == schedule.skippedSessionStart) continue

        val baseDateTime = LocalDateTime.of(sessionStart, LocalTime.MIDNIGHT)
            .plusMinutes(schedule.startMinutes.toLong())
        for (offset in offsets) {
            val candidate = baseDateTime.plusMinutes(offset.toLong()).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
        }
    }
    return null
}

/**
 * 曜日を指定していないターム（一回きり）の、そのぶんが鳴り終わったかを返す。
 *
 * 曜日を指定していないタームは、オンにした時点から見て「今日のその時刻、過ぎていれば翌日」に鳴り、
 * 鳴り終わったら自分でオフになる。この関数がtrueを返したら、呼び出し側がオフにする。
 * 曜日を指定しているタームはその曜日ごとに繰り返すため、常にfalseを返す。
 */
fun isOneShotSessionFinished(schedule: AlarmSchedule, now: ZonedDateTime): Boolean {
    if (schedule.repeatDays.isNotEmpty()) return false
    // 次に鳴る回が無い＝もう鳴るものが残っていない
    val next = nextTrigger(schedule, now) ?: return true
    // 次に鳴る回が別の日のセッションなら、今日のぶんは終わっている
    return sessionStartDate(schedule, next) != sessionStartDate(schedule, now)
}

/**
 * atで鳴っている回より後に残っている、そのセッション内の鳴動回数（現在鳴っている回は含めない）。
 * 例: 7:00〜9:00・5分間隔（全25回）で7:05（2回目）が鳴っている場合、7:10〜9:00の23回を返す。
 * 鳴動画面の「あと23回」表示と、「今日はもう止める」でキャンセルする対象回数に使う
 * （docs/SPEC.md「追記: 残り鳴動回数の数え方」）。atは実際の鳴動時刻と一致している前提。
 */
fun remainingOccurrenceCount(schedule: AlarmSchedule, at: ZonedDateTime): Int {
    val sessionStart = sessionStartDate(schedule, at)
    val sessionStartDateTime = LocalDateTime.of(sessionStart, LocalTime.MIDNIGHT)
        .plusMinutes(schedule.startMinutes.toLong())
    val elapsedMinutes = Duration.between(sessionStartDateTime, at.toLocalDateTime()).toMinutes().toInt()
    val offsets = calculateOccurrenceOffsets(schedule)
    return offsets.count { it > elapsedMinutes }
}

/**
 * 一覧画面の「あと8時間30分」表示用に、次の鳴動までの残り時間を粒度別に分類した値。
 * 文字列化(strings.xml)はUI側の責務とし、ここでは表示に必要な数値だけを持つ。
 */
sealed class RemainingTime {
    /** 1分未満。端数切り捨てで0分になる場合を含み「あと1分未満」に対応する */
    data object LessThanOneMinute : RemainingTime()

    /** 1分以上60分未満。分の値だけを表示する */
    data class Minutes(val minutes: Long) : RemainingTime()

    /** 1時間以上24時間未満。時と分の組で表示する */
    data class HoursAndMinutes(val hours: Long, val minutes: Long) : RemainingTime()

    /** 24時間以上。遠い予定ほど分単位の細かさは重要でないため、日数のみ切り捨てで表示する */
    data class Days(val days: Long) : RemainingTime()
}

/**
 * 次の鳴動(nextTrigger)までの残り時間を一覧画面表示用の粒度に変換する。
 * 無効なアラームや予定が無い場合(nextTriggerがnull)はnullを返し、一覧側は何も表示しない。
 */
fun remainingTimeUntilNextTrigger(schedule: AlarmSchedule, now: ZonedDateTime): RemainingTime? {
    val next = nextTrigger(schedule, now) ?: return null
    val totalSeconds = Duration.between(now, next).seconds
    val totalMinutes = totalSeconds / 60
    return when {
        totalSeconds < 60 -> RemainingTime.LessThanOneMinute
        totalMinutes < 60 -> RemainingTime.Minutes(totalMinutes)
        totalMinutes < 24 * 60 -> RemainingTime.HoursAndMinutes(totalMinutes / 60, totalMinutes % 60)
        else -> RemainingTime.Days(totalMinutes / (24 * 60))
    }
}

/**
 * 間隔の言い方。例:「5分ごと」。終わりへ向けて間隔を詰めるタームは「5〜2分ごと」。
 *
 * 一覧・鳴動画面・ゲーム画面で同じ言い方をするため、ここだけで作る。
 * それぞれで組み立てていたときは、鳴動画面だけ「加速 5→2分」と別の書き方になっていた。
 */
fun intervalSummary(schedule: AlarmSchedule): String =
    if (schedule.startIntervalMinutes == schedule.endIntervalMinutes) {
        "${schedule.startIntervalMinutes}分ごと"
    } else {
        "${schedule.startIntervalMinutes}〜${schedule.endIntervalMinutes}分ごと"
    }

/**
 * 一覧画面に表示する要約文字列を組み立てる。間隔と1セッションあたりの鳴動回数を1行にまとめる。
 * 例: 「5分ごと · 25回」「1回のみ」（単発に退化する場合）。
 */
fun scheduleSummary(schedule: AlarmSchedule): String {
    val count = occurrenceCount(schedule)
    if (count <= 1) return "1回のみ"
    return "${intervalSummary(schedule)} · ${count}回"
}

/**
 * 「今日はもう止める」を実行する意味があるかを返す。
 * 有効なアラームで、これから鳴る回が残っている場合だけtrue。
 *
 * 一覧画面はこの判定で導線の表示を切り替える。押しても何も起きない状態で
 * 導線が並ぶと、アラーム自体を無効にするトグルとの違いが分かりにくくなるため。
 */
fun canEndTodaySession(schedule: AlarmSchedule, now: ZonedDateTime): Boolean {
    if (!schedule.enabled) return false
    // まだ始まっていないタームは終了できない。
    // 7:00〜9:00のアラームなら、6:00の時点で終わらせるものが無い。
    // 鳴らしたくないだけならスイッチで切ればよく、ここを出すと役目が紛らわしくなる
    if (!hasSessionStarted(schedule, now)) return false
    val next = nextTrigger(schedule, now) ?: return false
    // 次に鳴るのが別のセッションなら、今日の分はもう残っていない
    return sessionStartDate(schedule, next) == sessionStartDate(schedule, now)
}

/**
 * 今日のタームがもう始まっているか。
 * 日をまたぐターム(22:00〜翌2:00など)では、開始時刻を過ぎた日と、
 * 日付が変わった後の終了時刻までの両方を「始まっている」とみなす。
 */
private fun hasSessionStarted(schedule: AlarmSchedule, now: ZonedDateTime): Boolean {
    val minuteOfDay = now.hour * 60 + now.minute
    return if (crossesMidnight(schedule)) {
        // 日をまたぐ場合、開始以降か、終了までの間なら始まっている
        minuteOfDay >= schedule.startMinutes || minuteOfDay <= schedule.endMinutes
    } else {
        minuteOfDay >= schedule.startMinutes
    }
}

/**
 * そのセッションの指定回（occurrenceIndex: 0始まり）の鳴動における進捗率 p (0.0..1.0) を求める。
 * calculateOccurrenceOffsets で算出したオフセット列から経過分 t を取得し、セッション長に対する進捗率を算出する。
 */
fun occurrenceProgress(schedule: AlarmSchedule, occurrenceIndex: Int): Double {
    val span = sessionSpanMinutes(schedule)
    if (span <= 0) return 0.0
    val offsets = calculateOccurrenceOffsets(schedule)
    val t = offsets.getOrElse(occurrenceIndex) {
        if (occurrenceIndex < 0) 0 else offsets.last()
    }
    return calculateProgress(t, span)
}

/**
 * 解除チャレンジの難易度と進捗率 progress (0.0..1.0) から、出題時の問題数を返す。
 * 朝の二度寝を防ぐため、HARD では進捗に応じて 1〜3 問を出題し、境界値（1/3, 2/3）はその値を含む側が大きい方の問題数となる。
 */
fun challengeQuestionCount(challenge: ChallengeLevel, progress: Double): Int =
    when (challenge) {
        ChallengeLevel.EASY -> 1
        ChallengeLevel.HARD -> when {
            progress < 1.0 / 3.0 -> 1
            progress < 2.0 / 3.0 -> 2
            else -> 3
        }
    }

/**
 * スケジュールと進捗率 progress (0.0..1.0) から、その回に出題する解除チャレンジの問題数を返す。
 * 出題タイミング設定と難易度設定に応じて問題数を導出する。
 */
fun challengeQuestionCount(schedule: AlarmSchedule, progress: Double): Int =
    when (schedule.challengeTiming) {
        ChallengeTiming.NEVER -> 0
        ChallengeTiming.END_ONLY -> if (progress >= 1.0) challengeQuestionCount(schedule.challenge, progress) else 0
        ChallengeTiming.EVERY_TIME -> challengeQuestionCount(schedule.challenge, progress)
    }

/**
 * スケジュールと指定した鳴動回（occurrenceIndex: 0始まり）から、その回に出題する解除チャレンジの問題数を返す。
 * 出題タイミングの設定（NEVER / END_ONLY / EVERY_TIME）とセッション終了判定および難易度設定から出題数を決定する。
 */
fun challengeQuestionCount(schedule: AlarmSchedule, occurrenceIndex: Int): Int {
    val totalCount = occurrenceCount(schedule)
    val isLast = occurrenceIndex >= totalCount - 1
    return when (schedule.challengeTiming) {
        ChallengeTiming.NEVER -> 0
        ChallengeTiming.END_ONLY -> {
            if (isLast) {
                val p = occurrenceProgress(schedule, occurrenceIndex)
                challengeQuestionCount(schedule.challenge, p)
            } else {
                0
            }
        }
        ChallengeTiming.EVERY_TIME -> {
            val p = occurrenceProgress(schedule, occurrenceIndex)
            challengeQuestionCount(schedule.challenge, p)
        }
    }
}

/**
 * 範囲の最後の鳴動を停止した後に、本当に起きたかを確認する起床確認の時刻を求める。
 * wakeCheck が false の場合は確認を行わないため null を返す。
 * 有効な場合は、実際に停止した時刻 lastDismissedAt に wakeCheckMinutes 分を足した時刻を返す。
 */
fun wakeCheckTime(schedule: AlarmSchedule, lastDismissedAt: ZonedDateTime, wakeCheckMinutes: Int): ZonedDateTime? {
    if (!schedule.wakeCheck) return null
    return lastDismissedAt.plusMinutes(wakeCheckMinutes.toLong())
}

/**
 * そのセッションにおいて起床確認を行うべきかを判定する。
 * wakeCheck が false の場合、または「タームを終了」が実行され
 * skippedSessionStart がセッション開始日と一致する場合は確認を行わないため false を返す。
 */
fun shouldPerformWakeCheck(schedule: AlarmSchedule, sessionStart: LocalDate): Boolean {
    if (!schedule.wakeCheck) return false
    if (schedule.skippedSessionStart == sessionStart) return false
    return true
}

/**
 * 停止時刻などの瞬間 at が属するセッションにおいて、起床確認を行うべきかを判定する。
 * セッションの開始日を自動導出して起床確認要否を判断する。
 */
fun shouldPerformWakeCheck(schedule: AlarmSchedule, at: ZonedDateTime): Boolean =
    shouldPerformWakeCheck(schedule, sessionStartDate(schedule, at))


