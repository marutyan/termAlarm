package com.marutyan.termalarm.domain

/**
 * 現在の残り時間(ミリ秒)を返す。0..totalMillisへclampする。
 *
 * RUNNING以外(PAUSED/FINISHED)はremainingMillisAtAnchorがそのまま現在の残り時間なので、時間経過による
 * 再計算はしない。RUNNINGのときは経過時間の基準としてSystemClock.elapsedRealtime()を使うのが原則だが、
 * 端末再起動が起きるとelapsedRealtime()は0から数え直されるため、anchor記録時より小さい値になる。
 * これを再起動の合図とみなし、そのときだけ壁時計(currentTimeMillis)側の差分で残り時間を計算し直す。
 * これが「計測はelapsedRealtime基準、再起動をまたぐ復元だけ壁時計」を両立させる仕組み（docs/SPEC.md「計測の精度について」）。
 */
fun remainingMillis(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): Long {
    if (state.runState != TimerRunState.RUNNING) {
        return state.remainingMillisAtAnchor.coerceIn(0L, state.totalMillis)
    }
    val elapsedSinceAnchor = if (nowElapsedRealtime >= state.anchorElapsedRealtime) {
        nowElapsedRealtime - state.anchorElapsedRealtime
    } else {
        // elapsedRealtimeが逆行＝再起動が起きた合図。壁時計の差分にフォールバックする
        nowWallClockMillis - state.anchorWallClockMillis
    }
    return (state.remainingMillisAtAnchor - elapsedSinceAnchor).coerceIn(0L, state.totalMillis)
}

// RUNNING中に残り時間が尽きたかどうか。尽きていればサービス側はfinish()へ遷移させ鳴動を始める
fun isDue(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): Boolean =
    state.runState == TimerRunState.RUNNING && remainingMillis(state, nowElapsedRealtime, nowWallClockMillis) <= 0L

// 新しいタイマーを開始する。totalMillis/残り時間の両方をdurationMillisで初期化しRUNNINGにする
fun startTimer(id: Long, label: String, durationMillis: Long, nowElapsedRealtime: Long, nowWallClockMillis: Long): TimerState =
    TimerState(
        id = id,
        label = label,
        totalMillis = durationMillis,
        remainingMillisAtAnchor = durationMillis,
        anchorElapsedRealtime = nowElapsedRealtime,
        anchorWallClockMillis = nowWallClockMillis,
        runState = TimerRunState.RUNNING,
    )

// 一時停止。その瞬間の残り時間をremainingMillisAtAnchorへ固定してPAUSEDにする
fun pauseTimer(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): TimerState {
    if (state.runState != TimerRunState.RUNNING) return state
    return state.copy(
        remainingMillisAtAnchor = remainingMillis(state, nowElapsedRealtime, nowWallClockMillis),
        runState = TimerRunState.PAUSED,
    )
}

// 再開。anchorを現在時刻へ張り直し、残り時間(remainingMillisAtAnchor)はそのままRUNNINGに戻す
fun resumeTimer(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): TimerState {
    if (state.runState != TimerRunState.PAUSED) return state
    return state.copy(anchorElapsedRealtime = nowElapsedRealtime, anchorWallClockMillis = nowWallClockMillis, runState = TimerRunState.RUNNING)
}

// リセット。残り時間をtotalMillis(延長分を含む設定時間)まで戻し、動作は止めてPAUSEDにする
fun resetTimer(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): TimerState =
    state.copy(
        remainingMillisAtAnchor = state.totalMillis,
        anchorElapsedRealtime = nowElapsedRealtime,
        anchorWallClockMillis = nowWallClockMillis,
        runState = TimerRunState.PAUSED,
    )

/**
 * 動作中に1分単位などで延長する（docs/SPEC.md「動作中に1分単位で延長できる」）。
 * 合計時間(totalMillis)と現在の残り時間の両方へ同じだけ加える。FINISHED(鳴動中)は延長の対象外。
 */
fun extendTimer(state: TimerState, extraMillis: Long, nowElapsedRealtime: Long, nowWallClockMillis: Long): TimerState {
    if (state.runState == TimerRunState.FINISHED) return state
    val currentRemaining = remainingMillis(state, nowElapsedRealtime, nowWallClockMillis)
    return state.copy(
        totalMillis = state.totalMillis + extraMillis,
        remainingMillisAtAnchor = currentRemaining + extraMillis,
        anchorElapsedRealtime = nowElapsedRealtime,
        anchorWallClockMillis = nowWallClockMillis,
    )
}

// 残り時間が尽きたときの遷移。鳴動中(FINISHED)として残り0秒に固定する
fun finishTimer(state: TimerState): TimerState = state.copy(remainingMillisAtAnchor = 0L, runState = TimerRunState.FINISHED)

/**
 * 端末再起動の直後、保存されていたRUNNINGのタイマーを新しいelapsedRealtimeへ再アンカーする。
 * remainingMillis()自身も逆行を検知して壁時計へフォールバックするが、その結果を保存し直さないままだと
 * 「保存されていたanchorElapsedRealtimeの値に端末の稼働時間が追いつくまで」ずっと壁時計基準のままになってしまう
 * （起動直後はelapsedRealtimeが小さい値からやり直されるため）。起動のたびに一度だけ現在時刻へ張り直すことで、
 * 以降は再びelapsedRealtime基準の計測に戻す。
 */
fun rebaseTimerAfterReboot(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): TimerState {
    if (state.runState != TimerRunState.RUNNING) return state
    return state.copy(
        remainingMillisAtAnchor = remainingMillis(state, nowElapsedRealtime, nowWallClockMillis),
        anchorElapsedRealtime = nowElapsedRealtime,
        anchorWallClockMillis = nowWallClockMillis,
    )
}
