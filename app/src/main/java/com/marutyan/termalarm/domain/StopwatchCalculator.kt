package com.marutyan.termalarm.domain

/**
 * 現在の経過時間(ミリ秒)を返す。IDLE/PAUSEDはaccumulatedMillisがそのまま現在の経過時間なので、
 * 時間経過による再計算はしない。RUNNINGのときは経過時間の基準としてSystemClock.elapsedRealtime()を
 * 使うのが原則だが、端末再起動が起きるとelapsedRealtime()は0から数え直されるため、anchor記録時より
 * 小さい値になる。これを再起動の合図とみなし、そのときだけ壁時計(currentTimeMillis)側の差分で
 * 経過時間を計算し直す。これが「計測はelapsedRealtime基準、再起動をまたぐ復元だけ壁時計」を
 * 両立させる仕組み（docs/SPEC.md「計測の精度について」、タイマー機能のTimerCalculator.remainingMillisと同じ考え方）。
 */
fun elapsedMillis(state: StopwatchState, nowElapsedRealtime: Long, nowWallClockMillis: Long): Long {
    if (state.runState != StopwatchRunState.RUNNING) {
        return state.accumulatedMillis
    }
    val elapsedSinceAnchor = if (nowElapsedRealtime >= state.anchorElapsedRealtime) {
        nowElapsedRealtime - state.anchorElapsedRealtime
    } else {
        // elapsedRealtimeが逆行＝再起動が起きた合図。壁時計の差分にフォールバックする
        nowWallClockMillis - state.anchorWallClockMillis
    }
    return state.accumulatedMillis + elapsedSinceAnchor.coerceAtLeast(0L)
}

// 新規開始。IDLE/リセット直後の状態から呼ぶ想定で、経過時間0からRUNNINGにする
fun startStopwatch(nowElapsedRealtime: Long, nowWallClockMillis: Long): StopwatchState =
    StopwatchState(
        accumulatedMillis = 0L,
        anchorElapsedRealtime = nowElapsedRealtime,
        anchorWallClockMillis = nowWallClockMillis,
        runState = StopwatchRunState.RUNNING,
    )

// 一時停止。その瞬間の経過時間をaccumulatedMillisへ固定してPAUSEDにする
fun pauseStopwatch(state: StopwatchState, nowElapsedRealtime: Long, nowWallClockMillis: Long): StopwatchState {
    if (state.runState != StopwatchRunState.RUNNING) return state
    return state.copy(
        accumulatedMillis = elapsedMillis(state, nowElapsedRealtime, nowWallClockMillis),
        runState = StopwatchRunState.PAUSED,
    )
}

// 再開。anchorを現在時刻へ張り直し、経過時間(accumulatedMillis)はそのままRUNNINGに戻す
fun resumeStopwatch(state: StopwatchState, nowElapsedRealtime: Long, nowWallClockMillis: Long): StopwatchState {
    if (state.runState != StopwatchRunState.PAUSED) return state
    return state.copy(anchorElapsedRealtime = nowElapsedRealtime, anchorWallClockMillis = nowWallClockMillis, runState = StopwatchRunState.RUNNING)
}

// リセット。経過時間を0に戻し、動作は止めてIDLEにする(ラップの削除はrepository側の責務)
fun resetStopwatch(nowElapsedRealtime: Long, nowWallClockMillis: Long): StopwatchState =
    StopwatchState(
        accumulatedMillis = 0L,
        anchorElapsedRealtime = nowElapsedRealtime,
        anchorWallClockMillis = nowWallClockMillis,
        runState = StopwatchRunState.IDLE,
    )

/**
 * 端末再起動の直後、保存されていたRUNNINGの状態を新しいelapsedRealtimeへ再アンカーする。
 * elapsedMillis()自身も逆行を検知して壁時計へフォールバックするが、その結果を保存し直さないままだと
 * 「保存されていたanchorElapsedRealtimeの値に端末の稼働時間が追いつくまで」ずっと壁時計基準のままになってしまう
 * （起動直後はelapsedRealtimeが小さい値からやり直されるため）。起動のたびに一度だけ現在時刻へ張り直すことで、
 * 以降は再びelapsedRealtime基準の計測に戻す（タイマー機能のrebaseTimerAfterRebootと同じ考え方）。
 */
fun rebaseStopwatchAfterReboot(state: StopwatchState, nowElapsedRealtime: Long, nowWallClockMillis: Long): StopwatchState {
    if (state.runState != StopwatchRunState.RUNNING) return state
    return state.copy(
        accumulatedMillis = elapsedMillis(state, nowElapsedRealtime, nowWallClockMillis),
        anchorElapsedRealtime = nowElapsedRealtime,
        anchorWallClockMillis = nowWallClockMillis,
    )
}

/**
 * ラップを1件記録する。ラップ番号は既存ラップ数+1、ラップ時間は直前ラップの合計との差分。
 * 直前ラップが無ければ計測開始からの経過時間そのものがラップ時間になる
 * (docs/SPEC.md「各ラップの時間と、その時点の合計を並べる」)。
 */
fun recordLap(currentTotalMillis: Long, previousLaps: List<StopwatchLap>): StopwatchLap {
    val previousTotal = previousLaps.lastOrNull()?.totalMillis ?: 0L
    return StopwatchLap(
        lapNumber = previousLaps.size + 1,
        lapMillis = currentTotalMillis - previousTotal,
        totalMillis = currentTotalMillis,
    )
}
