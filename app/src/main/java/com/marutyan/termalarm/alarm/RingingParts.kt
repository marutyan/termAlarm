package com.marutyan.termalarm.alarm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.alarmedit.TermChevronRightIcon
import com.marutyan.termalarm.ui.alarmedit.TermEndIcon
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily

/**
 * 現在のアラーム音量を5段階の縦棒ゲージで表示するComposable。
 * design/Ringing.dc.htmlの右上に配置される音量表示を再現し、端末の鳴動音量レベルを視覚化するために用いる。
 */
@Composable
internal fun VolumeIndicator(
    volumeLevel: Int,
    modifier: Modifier = Modifier,
) {
    // 5本のバーそれぞれの高さを定義 (9dp, 13dp, 17dp, 21dp, 26dp)
    val barHeights = remember { listOf(9.dp, 13.dp, 17.dp, 21.dp, 26.dp) }
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier.padding(top = 8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 音量ラベル
        Text(
            text = stringResource(R.string.ringing_volume_label),
            style = TextStyle(
                fontFamily = ibmPlexMonoFontFamily(400),
                fontSize = 13.sp,
                letterSpacing = 0.1.em,
                color = MaterialTheme.customColors.subtleText,
            ),
        )

        // 5本の縦棒インジケーター（高さ26dpの領域で下揃え配置）
        Row(
            modifier = Modifier.height(26.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            barHeights.forEachIndexed { index, barHeight ->
                val isActive = index < volumeLevel
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(barHeight)
                        .background(if (isActive) activeColor else inactiveColor),
                )
            }
        }
    }
}

/**
 * ターム全体の鳴動回数と現在の進行状況を高さ12dpの横帯スロットで表示するComposable。
 * 過去・現在・未来の各スロットを色分けし、セッション内の進捗を一目で把握できるようにするために用いる。
 */
@Composable
internal fun OccurrenceScaleBar(
    totalCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    val pastColor = MaterialTheme.customColors.scalePast
    val currentColor = MaterialTheme.colorScheme.primary
    val upcomingColor = MaterialTheme.customColors.scaleUpcoming

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val safeTotal = totalCount.coerceAtLeast(1)
        for (i in 0 until safeTotal) {
            val slotColor = when {
                i < currentIndex -> pastColor
                i == currentIndex -> currentColor
                else -> upcomingColor
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .background(slotColor),
            )
        }
    }
}

/**
 * 鳴動停止カード内の左側に描画する縦2本線のストップアイコン。
 * 一時停止を意味する幾何学模様を描画するために用いる。
 */
@Composable
private fun RingingStopIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val scale = size.width / 24f
        val strokeWidth = 2.2f * scale
        // 左側の縦線 (M9 5v14)
        drawLine(
            color = color,
            start = Offset(9f * scale, 5f * scale),
            end = Offset(9f * scale, 19f * scale),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        // 右側の縦線 (M15 5v14)
        drawLine(
            color = color,
            start = Offset(15f * scale, 5f * scale),
            end = Offset(15f * scale, 19f * scale),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 鳴動を停止して次回鳴動を予約するための主役色カードComposable。
 * design/Ringing.dc.htmlに準拠し、アイコン、ストップ文字、次回鳴動予定時刻を並べて表示する。
 */
@Composable
internal fun RingingStopCard(
    nextOccurrenceText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RingingStopIcon(color = MaterialTheme.colorScheme.onPrimary)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(R.string.ringing_stop_action),
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                Text(
                    text = nextOccurrenceText,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }
}

/**
 * 当日の残りセッションを終了するための確認画面を開く枠線カードComposable。
 * 鳴動画面で直接終了せず、確認画面へ誘導する導線として配置するために用いる。
 */
@Composable
internal fun RingingEndTermCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = TermEndIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.customColors.subtleText,
            )

            Text(
                text = stringResource(R.string.ringing_end_term),
                style = TextStyle(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.weight(1f),
            )

            Icon(
                imageVector = TermChevronRightIcon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.customColors.subtleText,
            )
        }
    }
}
