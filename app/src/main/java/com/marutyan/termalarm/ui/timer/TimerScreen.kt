package com.marutyan.termalarm.ui.timer

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.millisUntilNextSecondBoundary
import com.marutyan.termalarm.domain.remainingMillis
import com.marutyan.termalarm.ui.common.TermAlarmOverflowMenu
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.pressScaleEffect
import com.marutyan.termalarm.ui.theme.timerAddFadeSpec
import com.marutyan.termalarm.ui.theme.timerAddSlideSpec
import kotlinx.coroutines.delay

/**
 * 破線枠を描画するModifier拡張関数。
 * タイマー追加ボタンなどの破線輪郭線をデザイン通りに描画するために用いる。
 */
private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp,
): Modifier = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashLength.toPx(), gapLength.toPx()),
            0f,
        ),
    )
    val halfWidth = width.toPx() / 2f
    val r = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(halfWidth, halfWidth),
        size = Size(size.width - width.toPx(), size.height - width.toPx()),
        cornerRadius = CornerRadius(r, r),
        style = stroke,
    )
}

/**
 * タイマータブの画面。design/Timer.dc.html を再現する。
 * 見出し「タイマー」(28sp、太さ300)、動作中タイマーのカード一覧(角丸16dp)、
 * 一覧下部の「タイマーを追加」破線ボタン(56dp)を配置する。
 * タイマーが0件のとき、または追加ボタン押下時はテンキーによる追加画面(TimerAddScreen)を表示する。
 */
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    bottomBar: @Composable () -> Unit,
) {
    var showAddScreen by rememberSaveable { mutableStateOf(false) }
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val tickingNow = rememberTickingNow(timers)
    val (nowElapsed, nowWall) = tickingNow
    val sortedTimers = remember(timers, nowElapsed, nowWall) {
        sortTimers(timers, nowElapsed, nowWall)
    }

    // タイマーが1件も無いときは直接テンキー入力画面を表示する
    val showKeypad = showAddScreen || sortedTimers.isEmpty()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = showKeypad,
            transitionSpec = {
                if (targetState) {
                    (slideInVertically(animationSpec = timerAddSlideSpec()) { it } + fadeIn(animationSpec = timerAddFadeSpec()))
                        .togetherWith(fadeOut(animationSpec = timerAddFadeSpec()))
                } else {
                    fadeIn(animationSpec = timerAddFadeSpec())
                        .togetherWith(slideOutVertically(animationSpec = timerAddSlideSpec()) { -it } + fadeOut(animationSpec = timerAddFadeSpec()))
                }
            },
            label = "TimerAddScreenTransition",
        ) { isKeypad ->
            if (isKeypad) {
                TimerAddScreen(
                    onStart = { h, m, s ->
                        viewModel.start(h, m, s)
                        showAddScreen = false
                    },
                    onClose = if (sortedTimers.isEmpty()) null else ({ showAddScreen = false }),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusBarTop + 74.dp, start = 18.dp, end = 18.dp),
                ) {
                    // 見出し「タイマー」 (28sp、太さ300) と オーバーフローメニュー
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.tab_timer),
                            style = TextStyle(
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.W300,
                                fontSize = 28.sp,
                                lineHeight = 36.sp,
                                letterSpacing = (-0.01).em,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TermAlarmOverflowMenu(
                            onOpenSettings = onOpenSettings,
                            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                            onOpenAbout = onOpenAbout,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 26.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(sortedTimers, key = { it.id }) { timer ->
                            TimerCard(
                                timer = timer,
                                nowElapsed = nowElapsed,
                                nowWall = nowWall,
                                onPause = { viewModel.pause(timer.id) },
                                onResume = { viewModel.resume(timer.id) },
                                onReset = { viewModel.reset(timer.id) },
                                onExtend = { viewModel.extendOneMinute(timer.id) },
                                onDelete = { viewModel.delete(timer.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }

                        // 「タイマーを追加」破線枠ボタン (高さ56dp、角丸14dp)
                        item {
                            val addInteraction = remember { MutableInteractionSource() }
                            val addDesc = stringResource(R.string.timer_add)
                            Surface(
                                onClick = { showAddScreen = true },
                                interactionSource = addInteraction,
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                                    .dashedBorder(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        cornerRadius = 14.dp,
                                    )
                                    .semantics { contentDescription = addDesc }
                                    .pressScaleEffect(addInteraction),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.customColors.subtleText,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = addDesc,
                                        style = TextStyle(fontSize = 14.sp),
                                        color = MaterialTheme.customColors.subtleText,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 残り時間の表示に使う現在時刻ペア(elapsedRealtime, wallClock)。
 * 次の秒境界に合わせて更新する。
 */
@Composable
private fun rememberTickingNow(timers: List<TimerState>): Pair<Long, Long> {
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var nowWall by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timers) {
        while (true) {
            delay(millisUntilNextSecondBoundary(timers, SystemClock.elapsedRealtime(), System.currentTimeMillis()))
            nowElapsed = SystemClock.elapsedRealtime()
            nowWall = System.currentTimeMillis()
        }
    }
    return nowElapsed to nowWall
}

/**
 * タイマー一覧を「鳴動中(FINISHED)」「動作中(RUNNING)」「一時停止中(PAUSED)」の順、
 * 同一状態内は残り時間昇順に並べ替える。
 */
private fun sortTimers(
    timers: List<TimerState>,
    nowElapsed: Long,
    nowWall: Long,
): List<TimerState> = timers.sortedWith(
    compareBy<TimerState> { timer ->
        when (timer.runState) {
            TimerRunState.FINISHED -> 0
            TimerRunState.RUNNING -> 1
            TimerRunState.PAUSED -> 2
        }
    }.thenBy { timer ->
        remainingMillis(timer, nowElapsed, nowWall)
    }.thenBy { timer ->
        timer.id
    },
)
