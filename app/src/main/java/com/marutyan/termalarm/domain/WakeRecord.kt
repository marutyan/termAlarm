package com.marutyan.termalarm.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * 鳴動の停止方法を表す列挙型。
 * チャレンジ正解、押して停止、放置による自動消音の別を記録し、起床傾向の分析に用いる。
 */
enum class StopMethod {
    /** 問題に正解して停止した */
    CHALLENGE,

    /** 問題を出さない設定で、押して停止した */
    TAP,

    /** 放置され、一定時間後に自動で鳴り止んだ */
    AUTO_SILENCED,
}

/**
 * 鳴動1回ごとの記録を表すモデル。
 * 予定時刻や実際の停止時刻、停止方法、セッション内の回数を保持し、実績の集計に用いる。
 */
data class RingRecord(
    val scheduledAt: ZonedDateTime, // 鳴るはずだった時刻
    val stoppedAt: ZonedDateTime?, // 実際に止めた時刻。放置して自動消音されたなら null
    val stopMethod: StopMethod, // 止め方
    val occurrenceIndex: Int, // そのセッションの何回目か。0始まり
)

/**
 * 1つのセッション全体の記録を表すモデル。
 * セッション開始日、範囲開始時刻、各鳴動の記録列を保持し、セッション単位の起床実績集計に用いる。
 */
data class SessionRecord(
    val sessionStart: LocalDate, // セッションの開始日
    val rangeStartAt: ZonedDateTime, // 範囲の開始時刻
    val rings: List<RingRecord>, // そのセッションの鳴動の記録。occurrenceIndex の昇順
)

/**
 * 複数セッションにわたる起床実績の平均値を表すモデル。
 * 起床とみなした回の平均と所要分数の平均を保持する。
 */
data class WakeAverages(
    val averageOccurrence: Double,
    val averageDurationMinutes: Double,
)

/**
 * そのセッションで「起床とみなした回」が何回目か（1始まり）を求める。
 * 最後に stoppedAt が入っている記録の occurrenceIndex に 1 を足した値を返す。1度も止めていない（全部 AUTO_SILENCED）なら null を返す。
 */
fun wakeOccurrence(session: SessionRecord): Int? {
    val lastDismissed = session.rings.lastOrNull { it.stoppedAt != null } ?: return null
    return lastDismissed.occurrenceIndex + 1
}

/**
 * 範囲の開始から起床までの所要分数（分単位）を求める。
 * rangeStartAt から「起床とみなした回」の stoppedAt までの分数を計算する。起床とみなした回が無ければ null を返す。
 */
fun wakeDurationMinutes(session: SessionRecord): Long? {
    val lastDismissed = session.rings.lastOrNull { it.stoppedAt != null } ?: return null
    val stoppedAt = lastDismissed.stoppedAt ?: return null
    return Duration.between(session.rangeStartAt, stoppedAt).toMinutes()
}

/**
 * 複数セッションにおける自動消音（放置）の割合を求める。
 * 二度寝の傾向を把握するために用い、全セッションの全鳴動のうち AUTO_SILENCED が占める割合を 0.0〜1.0 の Double で返す。鳴動が1件も無ければ 0.0 を返す。
 */
fun autoSilencedRatio(sessions: List<SessionRecord>): Double {
    val allRings = sessions.flatMap { it.rings }
    if (allRings.isEmpty()) return 0.0
    val autoSilencedCount = allRings.count { it.stopMethod == StopMethod.AUTO_SILENCED }
    return autoSilencedCount.toDouble() / allRings.size.toDouble()
}

/**
 * 複数セッションについて「起床とみなした回」の平均と「所要分数」の平均を求める。
 * 起床とみなした回が無いセッション（全部 AUTO_SILENCED など）は平均から除く。対象が1件も無ければ null を返す。
 */
fun averageWakeMetrics(sessions: List<SessionRecord>): WakeAverages? {
    val occurrences = mutableListOf<Int>()
    val durations = mutableListOf<Long>()

    for (session in sessions) {
        val occ = wakeOccurrence(session) ?: continue
        val dur = wakeDurationMinutes(session) ?: continue
        occurrences.add(occ)
        durations.add(dur)
    }

    if (occurrences.isEmpty()) return null
    return WakeAverages(
        averageOccurrence = occurrences.average(),
        averageDurationMinutes = durations.average(),
    )
}

/**
 * 複数セッションにおける「起床とみなした回」の平均を求める。
 * 起床とみなした回が無いセッションは平均から除く。対象が1件も無ければ null を返す。
 */
fun averageWakeOccurrence(sessions: List<SessionRecord>): Double? {
    val occurrences = sessions.mapNotNull { wakeOccurrence(it) }
    if (occurrences.isEmpty()) return null
    return occurrences.average()
}

/**
 * 複数セッションにおける「所要分数」の平均を求める。
 * 起床とみなした回が無いセッションは平均から除く。対象が1件も無ければ null を返す。
 */
fun averageWakeDurationMinutes(sessions: List<SessionRecord>): Double? {
    val durations = sessions.mapNotNull { wakeDurationMinutes(it) }
    if (durations.isEmpty()) return null
    return durations.average()
}

