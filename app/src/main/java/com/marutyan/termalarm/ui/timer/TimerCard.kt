package com.marutyan.termalarm.ui.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.overdueMillis
import com.marutyan.termalarm.domain.remainingMillis
import com.marutyan.termalarm.timer.formatDuration
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.pressScaleEffect
import com.marutyan.termalarm.ui.theme.tabularNums
import com.marutyan.termalarm.ui.theme.timerProgressAnimationSpec

/**
 * 動作中タイマー1件を表示するカードComposable。
 * design/Timer.dc.html の設計に基づき、角丸16dpのカードにラベル、残り時間(56sp)、進捗バー(4dp)、
 * 3つの等幅操作ボタン(＋1分、リセット、停止/再開)を縦に並べる。
 * 文字拡大(最大200%)時もレイアウトが崩れないよう、固定高さではなく最小高さを指定する。
 */
@Composable
fun TimerCard(
    timer: TimerState,
    nowElapsed: Long,
    nowWall: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onExtend: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining = remainingMillis(timer, nowElapsed, nowWall)
    val isFinished = timer.runState == TimerRunState.FINISHED
    val isRunning = timer.runState == TimerRunState.RUNNING

    // 残っている割合。進捗バーの長さに使用する(合計0はゼロ除算防止のため0f)
    val rawProgress = if (timer.totalMillis > 0) {
        (remaining.toFloat() / timer.totalMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = timerProgressAnimationSpec(),
        label = "TimerProgress",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(top = 18.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. ラベル(13sp) と 右上の閉じる「×」ボタン(タップ領域44dp以上)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val formattedDuration = formatTimerDuration(timer.totalMillis)
                val labelText = if (timer.label.isNotBlank() && timer.label != formatDuration(timer.totalMillis)) {
                    "${timer.label} · $formattedDuration"
                } else {
                    formattedDuration
                }
                Text(
                    text = labelText,
                    style = TextStyle(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(if (isFinished) R.string.timer_stop else R.string.timer_delete),
                        tint = MaterialTheme.customColors.subtleText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // 2. 残り時間: 56sp, 太さ200, 等幅数字。一時停止中は薄い文字色
            val remainingTextColor = when {
                isFinished -> MaterialTheme.colorScheme.error
                isRunning -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.customColors.subtleText
            }
            val remainingDisplay = if (isFinished) {
                val overdue = overdueMillis(timer, nowElapsed, nowWall)
                "\u2212" + formatTimerRemaining(overdue)
            } else {
                formatTimerRemaining(remaining)
            }
            Text(
                text = remainingDisplay,
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontWeight = FontWeight.W200,
                    fontSize = 56.sp,
                    lineHeight = 60.sp,
                    letterSpacing = (-0.04).em,
                    fontFeatureSettings = "tnum",
                ),
                color = remainingTextColor,
            )

            // 3. 進み具合の横棒。高さ4dp
            val progressTrackColor = MaterialTheme.colorScheme.outlineVariant
            val progressBarColor = when {
                isFinished -> MaterialTheme.colorScheme.error
                isRunning -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(progressTrackColor),
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(2.dp))
                            .background(progressBarColor),
                    )
                }
            }

            // 4. ボタン3つを同じ幅で。「＋1分」「リセット」「停止」または「再開」。高さ46dp、角丸23dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ボタン1: ＋1分
                val extendInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = onExtend,
                    interactionSource = extendInteraction,
                    shape = RoundedCornerShape(23.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 46.dp)
                        .pressScaleEffect(extendInteraction),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.timer_extend_one_minute_button),
                            style = TextStyle(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ボタン2: リセット (動作中も一時停止中も利用可能)
                val resetInteraction = remember { MutableInteractionSource() }
                val resetDesc = stringResource(R.string.timer_reset)
                Surface(
                    onClick = onReset,
                    interactionSource = resetInteraction,
                    shape = RoundedCornerShape(23.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 46.dp)
                        .semantics { contentDescription = resetDesc }
                        .pressScaleEffect(resetInteraction),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = resetDesc,
                            style = TextStyle(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ボタン3: 停止 または 再開
                val toggleInteraction = remember { MutableInteractionSource() }
                val isStopAction = isFinished || isRunning
                val actionDesc = stringResource(if (isStopAction) R.string.timer_pause else R.string.timer_resume)
                Surface(
                    onClick = {
                        when {
                            isFinished -> onDelete()
                            isRunning -> onPause()
                            else -> onResume()
                        }
                    },
                    interactionSource = toggleInteraction,
                    shape = RoundedCornerShape(23.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 46.dp)
                        .semantics { contentDescription = actionDesc }
                        .pressScaleEffect(toggleInteraction),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        if (isRunning) {
                            // 停止アイコン (縦2本線)
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 3.dp, height = 12.dp)
                                        .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(1.dp)),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(width = 3.dp, height = 12.dp)
                                        .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(1.dp)),
                                )
                            }
                            Text(
                                text = stringResource(R.string.timer_stop),
                                style = TextStyle(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        } else {
                            // 再開アイコン (三角)
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.timer_resume),
                                style = TextStyle(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 動作中タイマーの残り時間表示用文字列を生成する。
 * design/Timer.dc.html に合わせ、1時間未満は「1:47」のようにM:SS、1時間以上は「H:MM:SS」とする。
 */
private fun formatTimerRemaining(millis: Long): String {
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

/**
 * タイマーの設定時間(ミリ秒)を「3分」「8分」「1時間」のような日本語表記に整形する。
 * カード上部の時間ラベル表示に用いる。
 */
@Composable
private fun formatTimerDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) {
            append(stringResource(R.string.timer_duration_hours, hours))
        }
        if (minutes > 0) {
            append(stringResource(R.string.timer_duration_minutes, minutes))
        }
        if (seconds > 0 || (hours == 0L && minutes == 0L)) {
            append(stringResource(R.string.timer_duration_seconds, seconds))
        }
    }
}
