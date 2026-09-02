package com.marutyan.termalarm.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// 文字盤の目盛りを描く円の半径に対する比率。値が小さいほど文字盤の外周に近づく
private const val TICK_OUTER_RATIO = 0.94f
private const val TICK_INNER_RATIO_HOUR = 0.82f
private const val TICK_INNER_RATIO_MINUTE = 0.90f

/**
 * ライブラリを使わず`Canvas`で描くアナログ時計。針は秒単位で動けば十分(docs/SPEC.md「アナログ時計」)。
 * timeに渡した時刻の時・分・秒だけを見て針の角度を決めるため、どのタイムゾーンの時刻を渡しても
 * そのまま「その場所の今の時刻」を表す文字盤になる。
 */
@Composable
fun AnalogClockFace(time: ZonedDateTime, modifier: Modifier = Modifier) {
    val faceColor = MaterialTheme.colorScheme.outline
    val hourHandColor = MaterialTheme.colorScheme.onSurface
    val minuteHandColor = MaterialTheme.colorScheme.onSurface
    val secondHandColor = MaterialTheme.colorScheme.primary
    val centerColor = MaterialTheme.colorScheme.primary

    // 12時間・60分・60秒の一周(360度)に対する現在位置の角度。分・秒の進みを次の針へ滑らかに反映する
    val hourAngle = (time.hour % 12 + time.minute / 60f) * 30f
    val minuteAngle = (time.minute + time.second / 60f) * 6f
    val secondAngle = time.second * 6f

    Canvas(modifier = modifier) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = faceColor, radius = radius * TICK_OUTER_RATIO, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.02f))

        // 12方向の時マーカーと60方向の分マーカー(5分刻みの位置は太く長い時マーカーと重なるため60本で足りる)
        for (i in 0 until 60) {
            val isHourMark = i % 5 == 0
            val angle = i * 6f
            val innerRatio = if (isHourMark) TICK_INNER_RATIO_HOUR else TICK_INNER_RATIO_MINUTE
            drawTick(center, radius, angle, innerRatio, TICK_OUTER_RATIO, faceColor, if (isHourMark) radius * 0.02f else radius * 0.01f)
        }

        drawHand(center, radius * 0.5f, hourAngle, hourHandColor, radius * 0.045f)
        drawHand(center, radius * 0.72f, minuteAngle, minuteHandColor, radius * 0.03f)
        drawHand(center, radius * 0.82f, secondAngle, secondHandColor, radius * 0.012f)
        drawCircle(color = centerColor, radius = radius * 0.04f, center = center)
    }
}

// 中心からangle度(12時方向を0度とする時計回り)、長さlengthの針を描く
private fun DrawScope.drawHand(center: Offset, length: Float, angleDegrees: Float, color: Color, strokeWidth: Float) {
    val radians = Math.toRadians((angleDegrees - 90).toDouble())
    val end = Offset(center.x + length * cos(radians).toFloat(), center.y + length * sin(radians).toFloat())
    drawLine(color = color, start = center, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
}

// 文字盤の目盛りを1本描く。innerRatio〜outerRatioの間を結ぶ短い線分になる
private fun DrawScope.drawTick(
    center: Offset,
    radius: Float,
    angleDegrees: Float,
    innerRatio: Float,
    outerRatio: Float,
    color: Color,
    strokeWidth: Float,
) {
    val radians = Math.toRadians((angleDegrees - 90).toDouble())
    val cosA = cos(radians).toFloat()
    val sinA = sin(radians).toFloat()
    val start = Offset(center.x + radius * innerRatio * cosA, center.y + radius * innerRatio * sinA)
    val end = Offset(center.x + radius * outerRatio * cosA, center.y + radius * outerRatio * sinA)
    drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
}
