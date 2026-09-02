package com.marutyan.termalarm.domain

/**
 * タイマー1件の実行状態。RUNNING/PAUSEDはユーザー操作で行き来し、残り時間が0になるとFINISHEDへ移る。
 * FINISHEDは鳴動中（停止するまで）を表し、停止するとタイマー自体を削除する想定（ui/timer側の契約）。
 */
enum class TimerRunState { RUNNING, PAUSED, FINISHED }

/**
 * タイマー1件の状態。複数のタイマーを同時に動かせるようにするため、idごとに独立してこの状態を持つ
 * （docs/SPEC.md「タイマータブ」）。
 *
 * 経過時間はSystemClock.elapsedRealtime()を基準に計算する。壁時計(System.currentTimeMillis())は
 * 時刻合わせやタイムゾーン変更で計測が飛ぶため、動作中の計算には使わない。ただしelapsedRealtime()は
 * 端末再起動でゼロに戻ってしまうため、再起動をまたいだ復元にだけ壁時計(anchorWallClockMillis)を使う
 * （docs/SPEC.md「計測の精度について」、両立の詳細はTimerCalculator.remainingMillis参照）。
 */
data class TimerState(
    val id: Long,
    val label: String,
    val totalMillis: Long, // 開始時に指定した時間+延長分の合計。リセットするとこの値に戻る
    val remainingMillisAtAnchor: Long, // anchor時点での残り時間(ミリ秒)。PAUSED/FINISHEDではそのまま現在の残り時間を表す
    val anchorElapsedRealtime: Long, // remainingMillisAtAnchorを記録した時点のSystemClock.elapsedRealtime()。RUNNINGのときだけ意味を持つ
    val anchorWallClockMillis: Long, // 同時刻のSystem.currentTimeMillis()。再起動でelapsedRealtimeがリセットされた場合の復元にだけ使う
    val runState: TimerRunState,
)
