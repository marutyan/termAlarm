package com.marutyan.termalarm.alarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.alarmedit.TermChevronRightIcon
import com.marutyan.termalarm.ui.alarmedit.TermEndIcon
import com.marutyan.termalarm.ui.theme.TEXT_MIN_SIZE
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
    // 従来の大きさ(22dp)の1.4倍(30.8dp)で描画する
    Canvas(modifier = modifier.size(30.8.dp)) {
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
            .padding(28.dp),
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
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                Text(
                    text = nextOccurrenceText,
                    style = TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }
}

/**
 * ターム終了カードの長押し判定にかける時間（ミリ秒）。
 * 朝の誤操作によるターム全体の取り消しを防ぎ、意図した長押しのみを受け付けるために用いる。
 * 鳴動画面の「タームを終了」操作において、ゲージが満タンになり終了処理が実行されるまでの時間を規定する。
 */
private const val END_TERM_HOLD_DURATION_MS = 800

/**
 * ターム終了カードの長押しを途中で離したときにゲージがゼロへ戻るアニメーション時間（ミリ秒）。
 * 指を離した際に即座に消さず、キャンセルされたことを視覚的にフィードバックするために用いる。
 * 鳴動画面の「タームを終了」操作において、長押し中断時のゲージ巻き戻し時間を規定する。
 */
private const val END_TERM_RELEASE_DURATION_MS = 150

/**
 * ターム終了カードの長押しゲージを描画する際の不透明度。
 * 暗い画面でもゲージの進捗を視認可能にしつつ、重なる文字の可読性を損なわないために用いる。
 * 鳴動画面の「タームを終了」操作において、主役色で塗られる長押し進捗ゲージの透明度を規定する。
 */
private const val END_TERM_GAUGE_ALPHA = 0.30f

/**
 * 当日の残りセッションを終了するための確認画面を開く枠線カードComposable。
 * 誤操作を防ぐため800msの長押しでのみ動作し、長押し中はゲージアニメーションと触覚フィードバックを提供する。
 */
@Composable
internal fun RingingEndTermCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)
    val progress = remember { Animatable(0f) }
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "end_term_card_scale",
    )

    val outlineColor = MaterialTheme.colorScheme.outline
    val gaugeColor = MaterialTheme.colorScheme.primary.copy(alpha = END_TERM_GAUGE_ALPHA)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                if (progress.value > 0f) {
                    drawRect(
                        color = gaugeColor,
                        size = Size(size.width * progress.value, size.height),
                    )
                }
            }
            .border(
                width = 1.dp,
                color = outlineColor,
                shape = RoundedCornerShape(16.dp),
            )
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var completed = false
                        var holdJob: Job? = null
                        try {
                            isPressed = true
                            // 長押し判定とゲージアニメーションは、rememberCoroutineScopeではなくpointerInput側のスコープ（awaitEachGesture配下）で動かす。
                            // 画面側のスコープで起動すると、通知シェード引き下げや画面消灯などの操作取り消し時にもジョブが生き残り、誤って終了処理が実行されてしまうため。
                            holdJob = launch {
                                progress.snapTo(0f)
                                val result = progress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = END_TERM_HOLD_DURATION_MS,
                                        easing = LinearEasing,
                                    ),
                                )
                                if (result.endReason == AnimationEndReason.Finished) {
                                    completed = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnClick()
                                }
                            }

                            waitForUpOrCancellation()
                        } finally {
                            holdJob?.cancel()
                            isPressed = false
                            launch {
                                withContext(NonCancellable) {
                                    try {
                                        if (!completed) {
                                            progress.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(
                                                    durationMillis = END_TERM_RELEASE_DURATION_MS,
                                                    easing = LinearEasing,
                                                ),
                                            )
                                        } else {
                                            progress.snapTo(0f)
                                        }
                                    } finally {
                                        progress.snapTo(0f)
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

            Text(
                text = stringResource(R.string.ringing_end_term_hint),
                style = TextStyle(
                    fontSize = TEXT_MIN_SIZE,
                    color = MaterialTheme.customColors.subtleText,
                ),
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
