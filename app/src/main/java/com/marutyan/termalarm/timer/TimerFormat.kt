package com.marutyan.termalarm.timer

/**
 * 残り時間(ミリ秒)を "5:00" / "1:05:00" のような表示用文字列へ変換する。Android非依存の純粋な整形処理で、
 * フォアグラウンド通知(timer/TimerForegroundService)とタイマー画面(ui/timer)の両方から使う
 * 共通の書式（値を複数箇所に持たないための1箇所化）。
 */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
