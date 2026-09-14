package com.marutyan.termalarm.domain

/**
 * タイマーの残り時間・経過時間を文字にする処理と、進み具合を出す処理。
 *
 * 画面の数字、通知の数字、ステータスバーのチップは必ず同じ文字でなければならない。
 * 以前は画面が切り上げ、通知が切り捨て、チップがまた別、と3通りの作り方があり、
 * 同じ瞬間に別の秒数が出ていた。作り方をこのファイルだけに置いて、ずれる余地を無くす。
 */

/**
 * 残り時間の秒を出すときの繰り上げ幅(ミリ秒)。
 *
 * 残りは切り上げて出す。切り捨てると、まだ1秒近く残っているのに「0:00」と見えて、
 * リングの残りや実際に鳴る時刻とずれてしまうため。
 */
const val REMAINING_DISPLAY_ROUND_UP_MILLIS = 999L

/**
 * 時間(ミリ秒)を "5:00" / "1:05:00" の形へ変える。秒は切り捨てる。
 * 設定した長さのラベルと、0を過ぎてからの数え上げに使う。
 * 数え上げで切り上げると、0を過ぎた直後に「1秒」と出てしまう。
 */
fun formatDuration(millis: Long): String = formatSeconds((millis / 1000).coerceAtLeast(0L))

/**
 * 残り時間(ミリ秒)を "5:00" / "1:05:00" の形へ変える。秒は切り上げる。
 * 切り上げる理由は[REMAINING_DISPLAY_ROUND_UP_MILLIS]にある。
 */
fun formatRemainingDuration(millis: Long): String =
    formatSeconds(((millis + REMAINING_DISPLAY_ROUND_UP_MILLIS) / 1000).coerceAtLeast(0L))

// 秒数を時計の形へ。1時間以上のときだけ時を足す
private fun formatSeconds(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * 0を過ぎているか。
 *
 * 鳴動中(FINISHED)へ移すのはサービスが気づいた後になるため、
 * 動作中でも残りを使い切っていれば、もう過ぎているものとして扱う。
 * 待ってから切り替えると、0のところで数字が数秒止まって見える。
 */
fun isTimerOverdue(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): Boolean =
    state.runState == TimerRunState.FINISHED ||
        (state.runState == TimerRunState.RUNNING &&
            remainingMillis(state, nowElapsedRealtime, nowWallClockMillis) <= 0L)

/**
 * タイマー1件に出す文字。0を過ぎたらマイナスを付けて数え上げる。
 *
 * 画面の中央、通知、ステータスバーのチップは、いずれもこの文字をそのまま出すこと。
 * 別々に作ると、同じ瞬間に違う秒数が出てしまう。
 */
fun timerDisplayText(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): String =
    if (isTimerOverdue(state, nowElapsedRealtime, nowWallClockMillis)) {
        "−" + formatDuration(overdueMillis(state, nowElapsedRealtime, nowWallClockMillis))
    } else {
        formatRemainingDuration(remainingMillis(state, nowElapsedRealtime, nowWallClockMillis))
    }

/**
 * 残りの割合(0.0〜1.0)。画面のリングと通知のバーで同じ値を使う。
 * 設定した長さが0のときは0を返す(0除算を避ける)。
 */
fun timerRemainingFraction(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): Float {
    if (state.totalMillis <= 0L) return 0f
    val remaining = remainingMillis(state, nowElapsedRealtime, nowWallClockMillis)
    return (remaining.toFloat() / state.totalMillis.toFloat()).coerceIn(0f, 1f)
}

// 昔、開始時に名前へ自動で入れていた「0:05」のような時刻の形。
// 延長すると設定時間だけが変わるため、いまの設定時間と比べる方法では見分けられない
private val AUTO_LABEL_PATTERN = Regex("""\d{1,2}:\d{2}(:\d{2})?""")

/**
 * 利用者が付けた名前。付けていなければnull。
 *
 * 昔は開始時に設定時間の文字列を名前へ自動で入れていた。それは名前ではないので、
 * 画面でも通知でも名前としては出さない。利用者が付ける名前が時刻の形になることはまずない。
 */
fun TimerState.userLabelOrNull(): String? =
    label.takeIf { it.isNotBlank() && !AUTO_LABEL_PATTERN.matches(it) }

/**
 * まだ動いているタイマーか。通知へ出すかどうかの判断に使う。
 *
 * 「停止」を押すと設定した長さへ戻って一覧に残るが、それはもう動いていない。
 * 動いていないものを通知へ出すと、止めたはずのタイマーが「一時停止中」として
 * 残り続け、通知そのものも消えない。
 * 途中で一時停止しただけのものは、続きがあるので動いている扱いにする。
 */
fun isTimerActive(state: TimerState, nowElapsedRealtime: Long, nowWallClockMillis: Long): Boolean =
    when (state.runState) {
        TimerRunState.RUNNING, TimerRunState.FINISHED -> true
        TimerRunState.PAUSED ->
            remainingMillis(state, nowElapsedRealtime, nowWallClockMillis) < state.totalMillis
    }
