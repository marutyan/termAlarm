package com.marutyan.termalarm.ui.timer

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.marutyan.termalarm.ui.theme.SlideAnimatedDigits
import com.marutyan.termalarm.ui.theme.pressScaleEffect
import com.marutyan.termalarm.ui.theme.subHeroClock
import com.marutyan.termalarm.ui.theme.timerAddFadeSpec
import com.marutyan.termalarm.ui.theme.timerAddSlideSpec
import com.marutyan.termalarm.ui.theme.timerProgressAnimationSpec
import com.marutyan.termalarm.ui.common.TermAlarmOverflowMenu
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.millisUntilNextSecondBoundary
import com.marutyan.termalarm.domain.overdueMillis
import com.marutyan.termalarm.domain.remainingMillis
import com.marutyan.termalarm.ui.theme.tabularNums
import kotlinx.coroutines.delay

// タイマーカードに描く円形リングの直径と線の太さ。純正の実測値(docs/OFFICIAL_UI.md「タイマー」)は
// 直径311dp/線11dpだが、リングの右側に2つの操作ボタンを横並びで収めるため、画面幅に合わせて直径を200dpへ縮小した。
private val RING_STROKE_WIDTH = 11.dp
// 輪の右へ置くボタンの幅と、輪との間隔。輪の大きさをここから逆算する
private val SIDE_BUTTON_WIDTH = 80.dp
private val SIDE_BUTTON_GAP = 12.dp
/**
 * 純正のタイマーは輪の直径311dp、中の数字の高さ40dpだった。
 * 画面の幅は端末によって違うので、輪は使える幅いっぱいまで広げ、
 * 数字は純正と同じ見え方になるよう、その比のまま拡げ縮めする。
 */
private const val OFFICIAL_RING_DIAMETER_DP = 311f
private const val OFFICIAL_CLOCK_FONT_SIZE_SP = 79f

/**
 * タイマー1件のカードと、その中で使う部品。
 * 一覧の画面が長くなりすぎたため、1件の見た目に関わる部分をこちらへ分けている。
 */

/**
 * タイマー1件のカード。純正の時計アプリに合わせて左右余白13dp・角丸28dpのカードに、
 * 円形リングと残り時間、その下にリセットアイコンを置き、右側に操作ボタンを縦並びで配置する。
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
    // 残っている割合。リングの色付き部分の長さに使う(合計0はゼロ除算になるため0f扱い)
    val rawProgress = if (timer.totalMillis > 0) (remaining.toFloat() / timer.totalMillis.toFloat()).coerceIn(0f, 1f) else 0f
    // 1秒かけて次の角度まで直線的に進むよう補間する
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = timerProgressAnimationSpec(),
        label = "TimerProgress",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFinished) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // 名前を付けていないタイマーは、括弧だけが残らないよう「タイマー」と出す
                    text = if (timer.label.isBlank()) {
                        stringResource(R.string.timer_card_title_unnamed)
                    } else {
                        stringResource(R.string.timer_card_title, timer.label)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // ×(close)は削除に直結する。domain/TimerState.ktの契約通り「停止=削除」のため、
                // 完了(鳴動中)のときはtimer_stop、それ以外はtimer_delete を説明に使い分ける
                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(if (isFinished) R.string.timer_stop else R.string.timer_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isFinished) {
                // 鳴動中はボタンを出さず、リングと残り時間を中央に表示する
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    // 鳴動中はボタンを出さないので、幅いっぱいを輪に使える
                    val ringDiameter = maxWidth
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimerRing(
                            diameter = ringDiameter,
                            progress = progress,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            progressColor = MaterialTheme.colorScheme.error,
                        )
                        // 純正はタイムアップの後、0で止めずにマイナスへ数え続ける
                        val overdue = overdueMillis(timer, nowElapsed, nowWall)
                        SlideAnimatedDigits(
                            text = "\u2212" + formatTimerRemaining(overdue),
                            style = MaterialTheme.typography.displayLarge
                                .clockSizeFor(ringDiameter, overdue)
                                .tabularNums(),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    // 右のボタンを引いた残りが輪に使える幅。端末の幅に関わらず目一杯まで広げる
                    val ringDiameter = maxWidth - SIDE_BUTTON_WIDTH - SIDE_BUTTON_GAP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    Box(modifier = Modifier.size(ringDiameter), contentAlignment = Alignment.Center) {
                        TimerRing(
                            diameter = ringDiameter,
                            progress = progress,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            progressColor = MaterialTheme.colorScheme.primary,
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            SlideAnimatedDigits(
                                text = formatTimerRemaining(remaining),
                                style = MaterialTheme.typography.displayLarge
                                    .clockSizeFor(ringDiameter, remaining)
                                    .tabularNums(),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val resetInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = onReset,
                                interactionSource = resetInteractionSource,
                                modifier = Modifier.pressScaleEffect(resetInteractionSource),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_reset),
                                    contentDescription = stringResource(R.string.timer_reset),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ExtendChip(onClick = onExtend)
                        PlayPauseButton(isRunning = isRunning, onClick = if (isRunning) onPause else onResume)
                    }
                    }
                }
            }
        }
    }
}

/**
 * 円形の進捗リング。背景の全周弧と残り時間の弧を描き、先端に進捗を示す丸を配置する。
 */
@Composable
fun TimerRing(diameter: Dp, progress: Float, trackColor: Color, progressColor: Color) {
    val strokeWidthPx = with(LocalDensity.current) { RING_STROKE_WIDTH.toPx() }
    Canvas(modifier = Modifier.size(diameter)) {
        val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        val headRadius = strokeWidthPx
        // 先端の丸(直径22dp相当)がCanvas境界からはみ出さないよう、headRadius分だけ内側に収める
        val arcRadius = (size.width - 2 * headRadius) / 2f
        val topLeft = Offset(headRadius, headRadius)
        val arcSize = Size(arcRadius * 2f, arcRadius * 2f)

        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        if (progress > 0f) {
            val sweepAngle = 360f * progress
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )

            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val angleDegrees = -90f + sweepAngle
            val angleRadians = Math.toRadians(angleDegrees.toDouble())
            val headCenter = Offset(
                x = centerOffset.x + (arcRadius * Math.cos(angleRadians)).toFloat(),
                y = centerOffset.y + (arcRadius * Math.sin(angleRadians)).toFloat(),
            )
            drawCircle(
                color = progressColor,
                radius = headRadius,
                center = headCenter,
            )
        }
    }
}

/**
 * タイマーを1分延長するボタン。
 * 純正の仕様に合わせて枠線のみのピル型とし、リングの右側上部に配置する。
 */
@Composable
fun ExtendChip(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .width(80.dp)
            .height(56.dp)
            .pressScaleEffect(interactionSource),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.timer_extend_one_minute_button),
                style = MaterialTheme.typography.labelLarge.tabularNums(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * タイマーの一時停止と再開を切り替える塗りつぶしボタン。
 * 主要操作ボタンとしてprimaryContainerを使い、ExtendChipの下に配置する。
 */
@Composable
fun PlayPauseButton(isRunning: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (isRunning) R.string.timer_pause else R.string.timer_resume)
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .width(80.dp)
            .height(56.dp)
            .semantics { contentDescription = description }
            .pressScaleEffect(interactionSource),
        shape = RoundedCornerShape(28.dp),
        // 暗い画面ではprimaryが明るい側の色になる。ここは主要な操作なので目立たせる
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isRunning) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(width = 5.dp, height = 20.dp)
                                .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/**
 * 輪の中に収める残り時間の文字サイズ。
 * 純正と同じ見え方にするため、輪の大きさに対する比を保つ。
 * 桁が多いときは輪からはみ出すので、その分だけ縮める。
 */
private fun TextStyle.clockSizeFor(ringDiameter: Dp, remainingMillis: Long): TextStyle {
    val base = OFFICIAL_CLOCK_FONT_SIZE_SP * (ringDiameter.value / OFFICIAL_RING_DIAMETER_DP)
    // 「1:23:45」は7文字あり、「12:34」の5文字より横に広い。収まるよう先に細くしておく
    val shrink = if (remainingMillis >= 3600_000L) 0.62f else 1f
    val size = (base * shrink).sp
    return copy(fontSize = size, lineHeight = size * 1.1f)
}

/**
 * 動作中タイマーの残り時間表示用文字列を生成する。
 * 純正の時計アプリの仕様に合わせ、1時間以上は「1:23:45」、1分以上は「12:34」、1分未満は秒のみ「57」とする。
 */
private fun formatTimerRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        minutes > 0 -> "%d:%02d".format(minutes, seconds)
        else -> "%d".format(seconds)
    }
}
