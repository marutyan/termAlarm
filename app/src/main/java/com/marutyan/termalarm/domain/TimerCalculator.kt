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
 * 動作中または鳴動中に指定時間(extraMillis)延長する（docs/SPEC.md「動作中に1分単位で延長できる」）。
 * 動作中・一時停止中は合計時間(totalMillis)と現在の残り時間の両方へ加算する。
 * 鳴動中(FINISHED)の場合は指定時間でタイマーを再開し、合計時間と残り時間をその時間に合わせてRUNNINGへ戻す。
 */
fun extendTimer(state: TimerState, extraMillis: Long, nowElapsedRealtime: Long, nowWallClockMillis: Long): TimerState {
    if (state.runState == TimerRunState.FINISHED) {
        return state.copy(
            totalMillis = extraMillis,
            remainingMillisAtAnchor = extraMillis,
            anchorElapsedRealtime = nowElapsedRealtime,
            anchorWallClockMillis = nowWallClockMillis,
            runState = TimerRunState.RUNNING,
        )
    }
    val currentRemaining = remainingMillis(state, nowElapsedRealtime, nowWallClockMillis)
    return state.copy(
        totalMillis = state.totalMillis + extraMillis,
        remainingMillisAtAnchor = currentRemaining + extraMillis,
        anchorElapsedRealtime = nowElapsedRealtime,
        anchorWallClockMillis = nowWallClockMillis,
    )
}

// 残り時間が尽きたときの遷移。鳴動中(FINISHED)として残り0秒に固定する
fun finishTimer(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): TimerState {
    // 実際に0になった時刻を起点にする。サービスが気づくのは数秒遅れることがあり、
    // 気づいた時刻を起点にすると、画面の数え上げがそのぶん巻き戻って見える
    val overshoot = if (nowElapsedRealtime >= state.anchorElapsedRealtime) {
        (nowElapsedRealtime - state.anchorElapsedRealtime - state.remainingMillisAtAnchor)
            .coerceAtLeast(0L)
    } else {
        // elapsedRealtimeの逆行は再起動の合図。起点を今にする（遅れは測れない）
        0L
    }
    return state.copy(
        remainingMillisAtAnchor = 0L,
        // 鳴り始めた時刻をここへ残す。純正はタイムアップの後も経過をマイナスで数え続けるため、
        // その起点が要る。サービス側に覚えさせると、再起動やサービスの停止で失われる
        anchorElapsedRealtime = nowElapsedRealtime - overshoot,
        anchorWallClockMillis = nowWallClockMillis - overshoot,
        runState = TimerRunState.FINISHED,
    )
}

/**
 * 0になってから経過したミリ秒。まだ0になっていなければ0。
 * 純正のタイマーはタイムアップの後、残り時間の代わりに経過時間をマイナスで出し続ける。
 *
 * 動作中(RUNNING)でも、残りが尽きていればその経過を返す。
 * 鳴動中(FINISHED)へ切り替わるのはサービスが気づいた後になるため、
 * 切り替えを待つと画面が数秒固まって見えるためである。
 */
fun overdueMillis(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): Long {
    if (state.runState == TimerRunState.PAUSED) return 0L
    val elapsed = if (nowElapsedRealtime >= state.anchorElapsedRealtime) {
        nowElapsedRealtime - state.anchorElapsedRealtime
    } else {
        // elapsedRealtimeの逆行は再起動の合図。壁時計へ切り替える(remainingMillisと同じ考え方)
        nowWallClockMillis - state.anchorWallClockMillis
    }
    // FINISHEDは基準時刻が0になった瞬間なので、そのまま経過になる。
    // RUNNINGは残りを使い切ってからの超過ぶんを取り出す
    return (elapsed - state.remainingMillisAtAnchor).coerceAtLeast(0L)
}

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

/**
 * 表示の秒が次に変わるまでのミリ秒。
 *
 * ただ1秒ごとに描き直すと、秒が切り替わる位置が通知とずれて見える。
 * 動いているタイマーのうち、いちばん早く変わるものに合わせて描き直せば、
 * 画面・通知・ステータスバーのチップが同じ瞬間に同じ数字へ変わる。
 *
 * 残り時間は切り上げ、0を過ぎてからの数え上げは切り捨てなので、変わる位置が違う。
 * 変わるものが1つも無ければ1秒を返す。
 */
fun millisUntilNextSecondBoundary(
    timers: List<TimerState>,
    nowElapsedRealtime: Long,
    nowWallClockMillis: Long,
): Long {
    val next = timers.mapNotNull { state ->
        when {
            isTimerOverdue(state, nowElapsedRealtime, nowWallClockMillis) -> {
                // 数え上げは切り捨てなので、1000の倍数を越えた瞬間に1つ増える
                1000L - overdueMillis(state, nowElapsedRealtime, nowWallClockMillis) % 1000L
            }
            state.runState == TimerRunState.RUNNING -> {
                // 残りは切り上げなので、1000の倍数を割った瞬間に1つ減る
                val remainder = remainingMillis(state, nowElapsedRealtime, nowWallClockMillis) % 1000L
                if (remainder <= 0L) 1000L else remainder
            }
            else -> null
        }
    }.minOrNull() ?: 1000L
    return next.coerceIn(1L, 1000L)
}

/**
 * 次に見回るまでのミリ秒。
 *
 * 見たいものが2つある。
 * 1つは「表示の秒が変わる瞬間」。通知は届くまでに少し遅れるため、[displayLeadMillis]だけ早く起きる。
 * もう1つは「残りが0になる瞬間」。鳴らすかどうかは実際の時刻で決めるので、先取りした時刻では判定できない。
 *
 * 表示の区切りだけで待っていたときは、先取りのぶん0の手前で起きてしまい、
 * まだ0ではないので鳴らさず、次に起きるのが1秒後になっていた。
 * 「0になってから1秒ほど待って鳴り始める」ように見えたのはこれが原因。
 */
fun millisUntilNextTimerEvent(
    timers: List<TimerState>,
    nowElapsedRealtime: Long,
    nowWallClockMillis: Long,
    displayLeadMillis: Long,
): Long {
    val untilDisplayChange = millisUntilNextSecondBoundary(
        timers,
        nowElapsedRealtime + displayLeadMillis,
        nowWallClockMillis + displayLeadMillis,
    )
    // まだ残っているものだけを見る。すでに0のものはこの見回りで鳴動中へ移るため、
    // 待ち時間を0にすると、移すまでの間ずっと短い間隔で回り続けてしまう
    val untilDue = timers
        .filter { it.runState == TimerRunState.RUNNING }
        .map { remainingMillis(it, nowElapsedRealtime, nowWallClockMillis) }
        .filter { it > 0L }
        .minOrNull()
        ?: Long.MAX_VALUE
    return minOf(untilDisplayChange, untilDue).coerceAtLeast(0L)
}
