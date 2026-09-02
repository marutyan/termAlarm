package com.marutyan.termalarm.domain

/**
 * ストップウォッチの実行状態。タイマー(TimerRunState)と違いカウントアップには「完了」が無いため、
 * まだ一度も開始していない/リセット直後を表すIDLEと、動作中RUNNING、一時停止中PAUSEDの3値のみ持つ。
 */
enum class StopwatchRunState { IDLE, RUNNING, PAUSED }

/**
 * ストップウォッチの状態。アプリ全体で1つだけ存在する(複数同時計測はSPEC対象外)ため、
 * TimerStateと違いidを持たない。
 *
 * 経過時間はSystemClock.elapsedRealtime()を基準に計算する。壁時計(System.currentTimeMillis())は
 * 時刻合わせやタイムゾーン変更で計測が飛ぶため、動作中の計算には使わない。ただしelapsedRealtime()は
 * 端末再起動でゼロに戻ってしまうため、再起動をまたいだ復元にだけ壁時計(anchorWallClockMillis)を使う
 * （docs/SPEC.md「計測の精度について」、両立の詳細はStopwatchCalculator.elapsedMillis参照。
 * タイマー機能のTimerState/TimerCalculatorと同じ設計）。
 */
data class StopwatchState(
    val accumulatedMillis: Long, // anchor時点までに確定した経過時間の合計。PAUSED/IDLEではそのまま現在の経過時間を表す
    val anchorElapsedRealtime: Long, // accumulatedMillisを記録した時点のSystemClock.elapsedRealtime()。RUNNINGのときだけ意味を持つ
    val anchorWallClockMillis: Long, // 同時刻のSystem.currentTimeMillis()。再起動でelapsedRealtimeがリセットされた場合の復元にだけ使う
    val runState: StopwatchRunState,
) {
    companion object {
        // まだ一度も開始していない/リセット直後の初期状態
        val INITIAL = StopwatchState(
            accumulatedMillis = 0L,
            anchorElapsedRealtime = 0L,
            anchorWallClockMillis = 0L,
            runState = StopwatchRunState.IDLE,
        )
    }
}

// ラップ1件。lapMillisは直前のラップからの所要時間、totalMillisは刻んだ時点の合計経過時間
// (docs/SPEC.md「ラップを刻む。各ラップの時間と、その時点の合計を並べる」)
data class StopwatchLap(
    val lapNumber: Int, // 1始まりの通し番号
    val lapMillis: Long,
    val totalMillis: Long,
)
