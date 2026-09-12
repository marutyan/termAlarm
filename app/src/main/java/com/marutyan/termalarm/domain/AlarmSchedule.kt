package com.marutyan.termalarm.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * アラーム1件を表すデータモデル。時刻の範囲（startMinutes〜endMinutes）と間隔（startIntervalMinutes〜endIntervalMinutes）を持ち、
 * 1件の設定から複数回の鳴動（occurrence）が生成される。
 * domain/data/alarm/ui の各担当が前提とする共通契約のため、フィールド名・型・順序を変更しない（docs/SPEC.md参照）。
 */
data class AlarmSchedule(
    val id: Long,
    val startMinutes: Int, // 0..1439。深夜0時からの経過分
    val endMinutes: Int, // 0..1439。startMinutes と同値なら単発
    val startIntervalMinutes: Int, // 1以上。範囲の始めの間隔
    val endIntervalMinutes: Int, // 1以上。範囲の終わりの間隔。startと同値なら等間隔
    val repeatDays: Set<DayOfWeek>, // 空集合なら「次の1回だけ」
    val label: String,
    val soundUri: String?, // null ならシステム既定のアラーム音
    val vibrate: Boolean,
    val enabled: Boolean,
    val skippedSessionStart: LocalDate?, // 「今日はもう止める」で終了させたセッションの開始日
    val skipRequiresApp: Boolean = true, // 当日終了をアプリからのみ許すか。falseなら鳴動画面にも導線を出す（docs/SPEC.md「誤操作の防止と当日終了」）
    val skipGame: Boolean = false, // 当日終了の前にゲームを1問挟むか。skipRequiresAppがfalseのときは無視する
    val snoozeMinutes: Int? = null, // スヌーズの分数。nullならスヌーズ無効（既定オフ、docs/SPEC.md「スヌーズ」）
    val challenge: ChallengeLevel = ChallengeLevel.NONE, // 解除チャレンジの強さ
    val startVolumePercent: Int = 100, // 範囲の始めの音量上限。1..100（端末のアラーム音量に対する割合）
    val endVolumePercent: Int = 100, // 範囲の終わりの音量上限。1..100。startと同値なら一定
    val wakeCheckMinutes: Int? = null, // 範囲終了後に起床確認を行うまでの分数。null なら確認しない
)
