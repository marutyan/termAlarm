package com.marutyan.termalarm.stopwatch

/**
 * 経過時間(ミリ秒)を表示用文字列へ変換する。Android非依存の純粋な整形処理で、
 * フォアグラウンド通知(StopwatchForegroundService)とストップウォッチ画面(ui/stopwatch)の
 * 両方から使う共通の書式（値を複数箇所に持たないための1箇所化。timer/TimerFormat.ktと同じ方針）。
 *
 * includeCentiseconds=trueで"1:02:34.56"のように1/100秒まで、falseで"1:02:34"のように秒までを出す。
 * 画面表示は1/100秒まで、通知は秒までにする理由はStopwatchScreen.ktのコメントを参照。
 */
fun formatElapsed(millis: Long, includeCentiseconds: Boolean): String {
    val clamped = millis.coerceAtLeast(0L)
    val hours = clamped / 3_600_000L
    val minutes = (clamped % 3_600_000L) / 60_000L
    val seconds = (clamped % 60_000L) / 1000L
    val time = if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
    if (!includeCentiseconds) return time
    val centiseconds = (clamped % 1000L) / 10L
    return "$time.%02d".format(centiseconds)
}
