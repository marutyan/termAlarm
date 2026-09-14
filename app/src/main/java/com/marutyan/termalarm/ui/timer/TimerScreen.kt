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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.millisUntilNextSecondBoundary
import com.marutyan.termalarm.domain.remainingMillis
import com.marutyan.termalarm.ui.common.TermAlarmTopBar
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.pressScaleEffect
import com.marutyan.termalarm.ui.theme.timerAddFadeSpec
import com.marutyan.termalarm.ui.theme.timerAddSlideSpec
import kotlinx.coroutines.delay

/** 右下に追加ボタンを浮かせる際のサイズ(dp)。設計図 design/Timer.dc.html に合わせるために定義する。 */
val TIMER_ADD_BUTTON_SIZE = 60.dp

/** 右下に追加ボタンを浮かせる際の角丸(dp)。設計図の形状を再現するために定義する。 */
val TIMER_ADD_BUTTON_CORNER_RADIUS = 20.dp

/** 追加ボタン内部のプラスアイコンのサイズ(dp)。視認性を保つために定義する。 */
val TIMER_ADD_ICON_SIZE = 26.dp

/**
 * タイマータブの画面。design/Timer.dc.html を再現する。
 * 見出し「タイマー」(28sp、太さ300)、動作中タイマーの円形リングカード一覧(角丸20dp)、
 * 画面右下に浮かせた「タイマーを追加」ボタン(60dp角、角丸20dp、主役の色)を配置する。
 * 追加ボタンを押したときだけテンキーによる追加画面(TimerAddScreen)を表示する。0件のときは中央に案内を表示する。
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

    val showKeypad = showAddScreen
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
                    onClose = { showAddScreen = false },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = statusBarTop + 74.dp, start = 20.dp, end = 20.dp, bottom = 24.dp),
                    ) {
                        // 画面上部の帯: アプリ名と三点メニュー
                        TermAlarmTopBar(
                            onOpenSettings = onOpenSettings,
                            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                            onOpenAbout = onOpenAbout,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 見出し「タイマー」 (28sp、太さ300)
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

                        Spacer(modifier = Modifier.height(16.dp))

                        if (sortedTimers.isEmpty()) {
                            // タイマーが1件も無いときの案内メッセージ
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.timer_empty_hint),
                                    style = TextStyle(fontSize = 15.sp),
                                    color = MaterialTheme.customColors.subtleText,
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 96.dp),
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
                            }
                        }
                    }

                    // 右下に浮かせた「タイマーを追加」ボタン (60dp角、角丸20dp、主役の色)
                    val addInteraction = remember { MutableInteractionSource() }
                    val addDesc = stringResource(R.string.timer_add)
                    Surface(
                        onClick = { showAddScreen = true },
                        interactionSource = addInteraction,
                        shape = RoundedCornerShape(TIMER_ADD_BUTTON_CORNER_RADIUS),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 26.dp)
                            .size(TIMER_ADD_BUTTON_SIZE)
                            .semantics { contentDescription = addDesc }
                            .pressScaleEffect(addInteraction),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(TIMER_ADD_ICON_SIZE),
                            )
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
