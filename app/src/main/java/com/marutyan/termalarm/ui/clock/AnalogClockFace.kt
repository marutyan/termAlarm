package com.marutyan.termalarm.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.marutyan.termalarm.domain.clockHandAngles
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// 文字盤の目盛りを描く円の半径に対する比率。値が小さいほど文字盤の外周に近づく
private const val TICK_OUTER_RATIO = 0.94f
private const val TICK_INNER_RATIO_HOUR = 0.82f
private const val TICK_INNER_RATIO_MINUTE = 0.90f

// 12/3/6/9の数字を置く位置の半径比率。時マーカーより内側、針の付け根よりは外側に置く
private const val NUMERAL_RADIUS_RATIO = 0.68f

// 数字を置く4方向(12時=0度起点、時計回り)。全時刻を入れると小さい画面で潰れるため代表の4つだけにする
private val NUMERAL_HOURS = listOf(12, 3, 6, 9)

/**
 * ライブラリを使わず`Canvas`で描くアナログ時計。文字盤・60本の目盛り(5分ごとは太く長い)・
 * 12/3/6/9の数字・時分秒針・中心の丸を描画する。timeに渡した時刻の時・分・秒だけを見て
 * 針の角度を決めるため、どのタイムゾーンの時刻を渡してもそのまま「その場所の今の時刻」を表す文字盤になる。
 */
@Composable
fun AnalogClockFace(time: ZonedDateTime, showSeconds: Boolean = true, modifier: Modifier = Modifier) {
    val faceColor = MaterialTheme.colorScheme.outline
    val numeralColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hourHandColor = MaterialTheme.colorScheme.onSurface
    val minuteHandColor = MaterialTheme.colorScheme.onSurface
    val secondHandColor = MaterialTheme.colorScheme.primary
    val centerColor = MaterialTheme.colorScheme.primary

    val angles = clockHandAngles(hour = time.hour, minute = time.minute, second = time.second)

    val textMeasurer = rememberTextMeasurer()
    val numeralStyle = MaterialTheme.typography.titleMedium.copy(color = numeralColor, textAlign = TextAlign.Center)

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

        for (hourNumber in NUMERAL_HOURS) {
            drawNumeral(textMeasurer, hourNumber.toString(), numeralStyle, center, (hourNumber % 12) * 30f, radius * NUMERAL_RADIUS_RATIO)
        }

        drawHand(center, radius * 0.5f, angles.hourDegrees, hourHandColor, radius * 0.045f)
        drawHand(center, radius * 0.72f, angles.minuteDegrees, minuteHandColor, radius * 0.03f)
        if (showSeconds) {
            drawHand(center, radius * 0.82f, angles.secondDegrees, secondHandColor, radius * 0.012f)
        }
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

// 中心からangle度・距離radiusの位置へ、テキストの中心が来るように数字を描く
private fun DrawScope.drawNumeral(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    center: Offset,
    angleDegrees: Float,
    radius: Float,
) {
    val layout = textMeasurer.measure(text, style)
    val radians = Math.toRadians((angleDegrees - 90).toDouble())
    val topLeft = Offset(
        x = center.x + radius * cos(radians).toFloat() - layout.size.width / 2f,
        y = center.y + radius * sin(radians).toFloat() - layout.size.height / 2f,
    )
    drawText(layout, topLeft = topLeft)
}
