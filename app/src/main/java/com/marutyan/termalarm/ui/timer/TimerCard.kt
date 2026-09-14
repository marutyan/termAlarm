package com.marutyan.termalarm.ui.timer

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import com.marutyan.termalarm.ui.theme.timerColorAnimationSpec

/** 円形リングの直径(dp)。純正時計アプリの実測値304.7dpに基づく。 */
val TIMER_RING_SIZE = 304.7.dp

/** 円形リングの線の太さ(dp)。純正時計アプリの実測値11.2dpに基づく。 */
val TIMER_RING_STROKE_WIDTH = 11.2.dp

/** カードの角丸の半径(dp)。純正時計アプリの実測値38dpに基づく。 */
val TIMER_CARD_CORNER_RADIUS = 38.dp

/** カード下部の「＋1:00」ボタンの幅(dp)。純正時計アプリの実測値176.2dpに基づく。 */
val TIMER_EXTEND_BUTTON_WIDTH = 176.2.dp

/** カード下部のボタンの高さ(dp)。純正時計アプリの実測値91.2dpに基づく。 */
val TIMER_BUTTON_ROW_HEIGHT = 91.2.dp

/** カード下部のリセットボタンの直径(dp)。純正時計アプリの実測値85.4dpに基づく。 */
val TIMER_RESET_BUTTON_SIZE = 85.4.dp

/** カード下部のボタン間の間隔(dp)。純正時計アプリの実測値11.2dpに基づく。 */
val TIMER_BUTTON_SPACING = 11.2.dp

/** カード下部の余白(dp)。純正時計アプリの実測値26.1dpに基づく。 */
val TIMER_CARD_BOTTOM_PADDING = 26.1.dp

/** カード右上の閉じる「×」アイコンサイズ(dp)。純正時計アプリの実測値24dpに基づく。 */
val TIMER_CARD_CLOSE_ICON_SIZE = 24.dp

/** カード右上の閉じる「×」ボタンのタップ領域サイズ(dp)。アクセシビリティ基準を満たすため48dpとする。 */
val TIMER_CARD_CLOSE_BUTTON_SIZE = 48.dp

/** カード中央の一時停止・再開の印のサイズ(dp)。純正時計アプリの実測値27dpに基づく。 */
val TIMER_ACTION_ICON_SIZE = 27.dp

/**
 * 一時停止・再開の印を、円の中心からどれだけ下へずらすか。
 * 残り時間を中心に置いたうえで、その下へ重ならずに収まる位置とする。
 */
val TIMER_ACTION_ICON_CENTER_OFFSET = 44.dp

/**
 * 端末の「アニメーションを減らす」または「アニメーションの無効化」が有効になっているかを判定する。
 * 動きに弱い利用者に配慮し、アニメーションの抑制設定を検知するために用いる。
 */
fun isReduceMotionEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f,
        ) == 0f
    } catch (_: Exception) {
        false
    }
}

/**
 * タイマーの円形プログレスの進捗割合(0f..1f)を計算・提供する。
 * 通常時は毎フレームなめらかに減らし、端末の「アニメーションを減らす」設定時は1秒ごとの更新にとどめる。
 * 呼び出し元全体の再構成を防ぐためStateを返し、Canvasの描画処理内でのみ値を読み出す。
 */
@Composable
fun rememberTimerProgress(
    timer: TimerState,
    nowElapsed: Long,
    nowWall: Long,
    reduceMotion: Boolean,
): State<Float> {
    if (timer.totalMillis <= 0L) {
        return rememberUpdatedState(0f)
    }
    if (timer.runState != TimerRunState.RUNNING || reduceMotion) {
        val remaining = remainingMillis(timer, nowElapsed, nowWall)
        val progress = (remaining.toFloat() / timer.totalMillis.toFloat()).coerceIn(0f, 1f)
        return rememberUpdatedState(progress)
    }

    val progressState = remember(timer.id, timer.anchorElapsedRealtime, timer.runState) {
        val initialRemaining = remainingMillis(timer, nowElapsed, nowWall)
        mutableFloatStateOf((initialRemaining.toFloat() / timer.totalMillis.toFloat()).coerceIn(0f, 1f))
    }

    LaunchedEffect(timer.id, timer.anchorElapsedRealtime, timer.runState, timer.totalMillis) {
        while (true) {
            var reachedZero = false
            withFrameMillis {
                val currentElapsed = SystemClock.elapsedRealtime()
                val currentWall = System.currentTimeMillis()
                val remaining = remainingMillis(timer, currentElapsed, currentWall)
                val currentProgress = (remaining.toFloat() / timer.totalMillis.toFloat()).coerceIn(0f, 1f)
                progressState.floatValue = currentProgress
                if (remaining <= 0L) {
                    reachedZero = true
                }
            }
            if (reachedZero) {
                break
            }
        }
    }

    return progressState
}

/**
 * 動作中タイマー1件を表示するカードComposable。
 * design/Timer.dc.html の設計に基づき、角丸20dpのカードにラベル、円形リング、中央の残り時間・印、
 * 「＋1:00」ボタンおよびリセットボタンを配置する。
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
    val context = LocalContext.current
    val reduceMotion = remember(context) { isReduceMotionEnabled(context) }
    val isFinished = timer.runState == TimerRunState.FINISHED
    val isRunning = timer.runState == TimerRunState.RUNNING

    val remaining = remainingMillis(timer, nowElapsed, nowWall)
    val progressState = rememberTimerProgress(
        timer = timer,
        nowElapsed = nowElapsed,
        nowWall = nowWall,
        reduceMotion = reduceMotion,
    )

    // カード背景色。完了時は主役の色に近い primaryContainer へ移り変わる
    val targetContainerColor = if (isFinished) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = timerColorAnimationSpec(),
        label = "TimerContainerColor",
    )

    // 円形リングの弧の色。一時停止時は沈んだ色 subtleText へ移り変わる
    val targetArcColor = when (timer.runState) {
        TimerRunState.RUNNING -> MaterialTheme.colorScheme.primary
        TimerRunState.PAUSED -> MaterialTheme.customColors.subtleText
        TimerRunState.FINISHED -> MaterialTheme.colorScheme.primary
    }
    val arcColor by animateColorAsState(
        targetValue = targetArcColor,
        animationSpec = timerColorAnimationSpec(),
        label = "TimerArcColor",
    )

    // 残り時間および印の色。完了時は onPrimary、一時停止時は subtleText へ移り変わる
    val targetTextColor = when (timer.runState) {
        TimerRunState.FINISHED -> MaterialTheme.colorScheme.onPrimary
        TimerRunState.RUNNING -> MaterialTheme.colorScheme.onSurface
        TimerRunState.PAUSED -> MaterialTheme.customColors.subtleText
    }
    val textColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = timerColorAnimationSpec(),
        label = "TimerTextColor",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TIMER_CARD_CORNER_RADIUS),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 35.5.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. 上の行: 左にラベル(16sp、薄い色)、右に閉じる「×」(24dp、タップ領域48dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
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
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = TIMER_CARD_CLOSE_BUTTON_SIZE),
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(TIMER_CARD_CLOSE_BUTTON_SIZE),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(if (isFinished) R.string.timer_stop else R.string.timer_delete),
                        tint = MaterialTheme.customColors.subtleText,
                        modifier = Modifier.size(TIMER_CARD_CLOSE_ICON_SIZE),
                    )
                }
            }

            // ラベル行からリング上端(77.8dp)までの間隔
            Spacer(modifier = Modifier.height(18.3.dp))

            // 2. 円形のリング(直径304.7dp、線幅11.2dp) と 3. 中央の残り時間・印
            Box(
                modifier = Modifier.size(TIMER_RING_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                val outlineColor = MaterialTheme.colorScheme.outline
                val primaryColor = MaterialTheme.colorScheme.primary

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = TIMER_RING_STROKE_WIDTH.toPx()
                    val radius = (size.minDimension - strokePx) / 2f
                    val arcTopLeft = Offset(strokePx / 2f, strokePx / 2f)
                    val arcSize = Size(radius * 2f, radius * 2f)

                    if (isFinished) {
                        // 完了時はリングの内側を主役の色で塗りつぶす
                        drawCircle(
                            color = primaryColor,
                            radius = radius + strokePx / 2f,
                            center = center,
                        )
                    } else {
                        // 下地の円: 地とはっきり見分けがつく輪郭の色(outline)
                        drawCircle(
                            color = outlineColor,
                            radius = radius,
                            center = center,
                            style = Stroke(width = strokePx),
                        )

                        // 残りぶんの弧: 主役の色。12時の位置(-90度)から時計回りに描き、減っていく
                        val progress = progressState.value
                        if (progress > 0.001f) {
                            val sweepAngle = 360f * progress
                            drawArc(
                                color = arcColor,
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                            )
                        }
                    }
                }

                // リング中央: 残り時間 (54sp, 太さ200, 等幅数字, 28spまで自動縮小) と 一時停止/再開/停止の印 (27dp)
                val actionDesc = stringResource(
                    when {
                        isFinished -> R.string.timer_stop
                        isRunning -> R.string.timer_pause
                        else -> R.string.timer_resume
                    }
                )
                val toggleAction = {
                    when {
                        isFinished -> onDelete()
                        isRunning -> onPause()
                        else -> onResume()
                    }
                }
                val centerInteraction = remember { MutableInteractionSource() }

                // 残り時間を円のちょうど中心へ置く。印はその下へ重ねて配置する
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(TIMER_RING_SIZE)
                        .clickable(
                            interactionSource = centerInteraction,
                            indication = ripple(bounded = false, radius = 75.dp),
                            onClick = toggleAction,
                        )
                        .semantics { contentDescription = actionDesc },
                ) {
                    val remainingDisplay = if (isFinished) {
                        val overdue = overdueMillis(timer, nowElapsed, nowWall)
                        "−" + formatTimerElapsed(overdue)
                    } else {
                        formatTimerRemaining(remaining)
                    }
                    Text(
                        text = remainingDisplay,
                        style = TextStyle(
                            fontFamily = IbmPlexMono,
                            fontWeight = FontWeight.W200,
                            fontSize = 54.sp,
                            lineHeight = 54.sp,
                            letterSpacing = (-0.03).em,
                            fontFeatureSettings = "tnum",
                        ),
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 28.sp,
                            maxFontSize = 54.sp,
                            stepSize = 1.sp,
                        ),
                        maxLines = 1,
                        softWrap = false,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = TIMER_RING_STROKE_WIDTH * 2),
                    )

                    TimerActionIcon(
                        runState = timer.runState,
                        color = textColor,
                        modifier = Modifier.offset(y = TIMER_ACTION_ICON_CENTER_OFFSET),
                    )
                }
            }

            // リングからボタン行(上端417.1dp)までの間隔
            Spacer(modifier = Modifier.height(34.6.dp))

            // 4. 下に2つのボタン。「＋1:00」（幅176.2dp、高さ91.2dp、角丸45.6dp）と、リセット（直径85.4dpの円）。間隔11.2dp、中央揃え
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TIMER_BUTTON_ROW_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(TIMER_BUTTON_SPACING, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ボタン1: ＋1:00 (幅176.2dp、高さ91.2dp、角丸45.6dp)
                val extendInteraction = remember { MutableInteractionSource() }
                val extendTextColor = if (isRunning || isFinished) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val animatedExtendTextColor by animateColorAsState(
                    targetValue = extendTextColor,
                    animationSpec = timerColorAnimationSpec(),
                    label = "TimerExtendTextColor",
                )

                Surface(
                    onClick = onExtend,
                    interactionSource = extendInteraction,
                    shape = RoundedCornerShape(45.6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .width(TIMER_EXTEND_BUTTON_WIDTH)
                        .height(TIMER_BUTTON_ROW_HEIGHT)
                        .pressScaleEffect(extendInteraction),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = stringResource(R.string.timer_extend_one_minute_button),
                            style = TextStyle(
                                fontFamily = IbmPlexMono,
                                fontSize = 19.5.sp,
                            ),
                            color = animatedExtendTextColor,
                        )
                    }
                }

                // ボタン2: リセット (直径85.4dpの円)。完了時は出さない
                if (!isFinished) {
                    val resetInteraction = remember { MutableInteractionSource() }
                    val resetDesc = stringResource(R.string.timer_reset)
                    val resetIconColor = if (isRunning) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val animatedResetIconColor by animateColorAsState(
                        targetValue = resetIconColor,
                        animationSpec = timerColorAnimationSpec(),
                        label = "TimerResetIconColor",
                    )

                    Surface(
                        onClick = onReset,
                        interactionSource = resetInteraction,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .size(TIMER_RESET_BUTTON_SIZE)
                            .semantics { contentDescription = resetDesc }
                            .pressScaleEffect(resetInteraction),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            TimerResetIcon(color = animatedResetIconColor, modifier = Modifier.size(32.4.dp))
                        }
                    }
                }
            }

            // カードの下の余白（実測値26.1dp）
            Spacer(modifier = Modifier.height(TIMER_CARD_BOTTOM_PADDING))
        }
    }
}

/**
 * 一時停止・再開・停止の状態に応じた27dpの印を描画するComposable。
 * 動作中は一時停止(縦2本線)、一時停止中は再開(三角)、完了時は停止(四角)を表示する。
 * 純正時計アプリの実測値27dpに基づく。
 */
@Composable
fun TimerActionIcon(
    runState: TimerRunState,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(TIMER_ACTION_ICON_SIZE)) {
        when (runState) {
            TimerRunState.RUNNING -> {
                // 一時停止の印: 縦2本線
                val strokeWidth = 3.24.dp.toPx()
                val x1 = size.width * (9f / 24f)
                val x2 = size.width * (15f / 24f)
                val y1 = size.height * (5f / 24f)
                val y2 = size.height * (19f / 24f)
                drawLine(
                    color = color,
                    start = Offset(x1, y1),
                    end = Offset(x1, y2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(x2, y1),
                    end = Offset(x2, y2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            TimerRunState.PAUSED -> {
                // 再開の印: 右向き三角
                val path = Path().apply {
                    moveTo(size.width * (7f / 24f), size.height * (4f / 24f))
                    lineTo(size.width * (19f / 24f), size.height * (12f / 24f))
                    lineTo(size.width * (7f / 24f), size.height * (20f / 24f))
                    close()
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = 2.7.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            TimerRunState.FINISHED -> {
                // 停止の印: 四角 (■)
                val squareSize = size.width * (12f / 24f)
                val left = (size.width - squareSize) / 2f
                val top = (size.height - squareSize) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(squareSize, squareSize),
                    cornerRadius = CornerRadius(2.7.dp.toPx(), 2.7.dp.toPx()),
                )
            }
        }
    }
}

/**
 * リセット操作を表す円形矢印アイコンを描画するComposable。
 * 経過時間を最初の設定時間へ巻き戻す手応えを伝えるために用いる。
 * 純正時計アプリの実測値に基づくリセットボタン内に適した大きさで描画する。
 */
@Composable
fun TimerResetIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(26.dp)) {
        val scale = size.width / 24f
        // 24の座標系で2.0の太さ。dpへ直してから掛けると二重に拡大され、線が潰れる
        val strokeWidth = 2.0f * scale

        // 円弧: 中心(12, 11)、半径9の円弧を時計回りに描画
        val arcRadius = 9f * scale
        val arcCenter = Offset(12f * scale, 11f * scale)
        val arcTopLeft = Offset(arcCenter.x - arcRadius, arcCenter.y - arcRadius)
        val arcSize = Size(arcRadius * 2f, arcRadius * 2f)

        drawArc(
            color = color,
            startAngle = 175f,
            sweepAngle = 285f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // 矢印の先端: カギ型の折れ線 (3, 5) -> (3, 10) -> (8, 10)
        val arrowPath = Path().apply {
            moveTo(3f * scale, 5f * scale)
            lineTo(3f * scale, 10f * scale)
            lineTo(8f * scale, 10f * scale)
        }
        drawPath(
            path = arrowPath,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * 動作中タイマーの残り時間表示用文字列を生成する。
 * design/Timer.dc.html に合わせ、1時間未満は「1:47」のようにM:SS、1時間以上は「H:MM:SS」とする。
 */
internal fun formatTimerRemaining(millis: Long): String {
    // 切り上げる。切り捨てると、まだ1秒近く残っているのに0:00と出て、
    // リングの残りや実際に鳴る時刻とずれて見える
    val totalSeconds = ((millis + 999) / 1000).coerceAtLeast(0L)
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
 * 鳴っている間に数え上げる経過時間の文字列を作る。
 * 残り時間とは逆に切り捨てる。切り上げると、0を過ぎた直後に「1秒」と出てしまうため。
 */
internal fun formatTimerElapsed(millis: Long): String {
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
