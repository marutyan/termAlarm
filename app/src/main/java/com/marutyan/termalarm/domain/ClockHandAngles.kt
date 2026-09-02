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
 * 時・分・秒から3本の針の角度を求める。時針は分の進みを、分針は秒の進みをなめらかに反映するため、
 * どちらも整数の時/分だけでなく下位の単位を按分して角度に混ぜ込む(実物のアナログ時計と同じ動き)。
 */
fun clockHandAngles(hour: Int, minute: Int, second: Int): ClockHandAngles = ClockHandAngles(
    hourDegrees = (hour % 12 + minute / 60f) * 30f,
    minuteDegrees = (minute + second / 60f) * 6f,
    secondDegrees = second * 6f,
)
