package com.marutyan.termalarm.alarm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R

/**
 * 鳴っている画面のボタンと絵。
 * 画面本体が長くなりすぎたため、押す部品と描く部品をこちらへ分けている。
 */

@Composable
internal fun StopButton(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(48.dp),
        modifier = Modifier.fillMaxWidth().height(96.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.ringing_stop),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// 上部の丸いアイコン内に描く簡易な時計の図柄(文字盤の輪+2本の針)。design/Ringing.dc.htmlのSVGを模す
@Composable
internal fun AlarmGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.1f
        drawCircle(color = tint, radius = size.minDimension / 2 - strokeWidth, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        val center = this.center
        drawLine(
            color = tint,
            start = center,
            end = Offset(center.x, center.y - size.minDimension * 0.28f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = center,
            end = Offset(center.x + size.minDimension * 0.22f, center.y + size.minDimension * 0.06f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

