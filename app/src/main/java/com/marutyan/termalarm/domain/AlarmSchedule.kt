package com.marutyan.termalarm.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * アラーム1件を表すデータモデル。時刻の範囲（startMinutes〜endMinutes）と間隔（startIntervalMinutes〜endIntervalMinutes）を持ち、
 * 1件の設定から複数回の鳴動（occurrence）が生成される。
 */
data class AlarmSchedule(
    val id: Long,
    val startMinutes: Int, // 0..1439。深夜0時からの経過分
    val endMinutes: Int, // 0..1439。startMinutes と同値なら単発
    val startIntervalMinutes: Int, // 1以上。範囲の始めの間隔
    val endIntervalMinutes: Int, // 1以上。範囲の終わりの間隔。startと同値なら等間隔
    val repeatDays: Set<DayOfWeek>, // 空集合なら「次の1回だけ」
    val label: String,
    val enabled: Boolean,
    val challengeTiming: ChallengeTiming = ChallengeTiming.NEVER, // 解除チャレンジの出題タイミング
    val challenge: ChallengeLevel = ChallengeLevel.EASY, // 解除チャレンジの強さ
    val wakeCheck: Boolean = false, // ターム終了後に二度寝チェックを行うか。既定false
    val skippedSessionStart: LocalDate? = null, // 「タームを終了」で終了させたセッションの開始日
)

