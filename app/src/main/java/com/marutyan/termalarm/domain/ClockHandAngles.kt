package com.marutyan.termalarm.domain

/**
 * アナログ時計の時針・分針・秒針の向き(度、12時方向を0度とした時計回り)。
 * Canvas描画(ui/clock/AnalogClockFace.kt)から角度計算だけを切り離し、JVM単体テストで検証できるようにする。
 */
data class ClockHandAngles(
    val hourDegrees: Float,
    val minuteDegrees: Float,
    val secondDegrees: Float,
)

/**
 * 時・分・秒から3本の針の角度を求める。
 *
 * 時針は分の進みを混ぜてなめらかに動かすが、分針は秒を混ぜず1分ごとに進める。
 * 純正の時計アプリも同じで、時針は`(時 + 分/60) * 30`、分針は`(分/60) * 360`としている
 * (com/android/deskclock/widget/AnalogClock.java の onDraw)。
 */
fun clockHandAngles(hour: Int, minute: Int, second: Int): ClockHandAngles = ClockHandAngles(
    hourDegrees = (hour % 12 + minute / 60f) * 30f,
    minuteDegrees = minute * 6f,
    secondDegrees = second * 6f,
)
