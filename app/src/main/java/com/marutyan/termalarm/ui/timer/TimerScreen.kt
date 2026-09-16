package com.marutyan.termalarm.ui.timer

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
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
import com.marutyan.termalarm.ui.common.SCREEN_HORIZONTAL_PADDING
import com.marutyan.termalarm.ui.common.TOP_BAR_CONTENT_GAP
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.millisUntilNextSecondBoundary
import com.marutyan.termalarm.domain.remainingMillis
import com.marutyan.termalarm.ui.common.TermAlarmTopBar
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

/** カード一覧の左右の余白(dp)。純正時計アプリの実測値16.2dpに基づく。 */
val TIMER_CARD_HORIZONTAL_PADDING = 16.2.dp

/**
 * タイマー画面で、数字を入れる画面(新規作成)を出すかどうかを決める。
 *
 * [timers]がnullのときは読み込みが終わっておらず、0件かどうかが分からないので出さない。
 * 読み込み前の空リストを「0件」と受け取ると、保存済みのタイマーがあっても
 * 画面を開いた直後だけ新規作成の画面が見えてしまう。
 *
 * @param timers 読み込み済みの一覧。読み込み前はnull
 * @param isAddRequested 右下の追加ボタンが押されているか
 */
internal fun shouldShowTimerKeypad(timers: List<TimerState>?, isAddRequested: Boolean): Boolean =
    when {
        timers == null -> false
        isAddRequested -> true
        else -> timers.isEmpty()
    }

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
) {
    var showAddScreen by rememberSaveable { mutableStateOf(false) }
    // 読み込みが終わるまではnull。0件かどうかがまだ決まっていないことを表す
    val loadedTimers by viewModel.timers.collectAsStateWithLifecycle()
    val timers = loadedTimers.orEmpty()
    val tickingNow = rememberTickingNow(timers)
    val (nowElapsed, nowWall) = tickingNow
    val sortedTimers = remember(timers, nowElapsed, nowWall) {
        sortTimers(timers, nowElapsed, nowWall)
    }

    // タイマーが1件も無いときは、案内を出さずに数字を入れる画面をそのまま見せる。
    // 押す先が同じ画面になるため、そのときは右下の追加ボタンも出さない
    val showKeypad = shouldShowTimerKeypad(loadedTimers, showAddScreen)

    Box(modifier = Modifier.fillMaxSize()) {
        // 読み込みが終わるまでは一覧も数字を入れる画面も出さない。
        // どちらを出しても、読み込み後に入れ替わってちらついて見える
        if (loadedTimers == null) return@Box

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
                    // 1件も無いときは戻る先が無いので、閉じる操作を用意しない
                    onClose = if (sortedTimers.isEmpty()) null else ({ showAddScreen = false }),
                    onOpenSettings = onOpenSettings,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                    onOpenAbout = onOpenAbout,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 画面上部の帯。位置は帯の側が持つので、ここでは余白を足さない
                        TermAlarmTopBar(
                            onOpenSettings = onOpenSettings,
                            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                            onOpenAbout = onOpenAbout,
                        )

                        Spacer(modifier = Modifier.height(TOP_BAR_CONTENT_GAP))

                        // 見出し「タイマー」 (28sp、太さ300)
                        Box(modifier = Modifier.padding(horizontal = SCREEN_HORIZONTAL_PADDING)) {
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
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 1件も無いときはこの枝に来ない（数字を入れる画面をそのまま出すため）
                        // 1件のときは今までの大きなカードを画面幅いっぱいに、2件以上のときは2列グリッドで小さく並べる。
                        // カードの幅は利用可能な幅から動的に計算し、件数の切り替えや増減時は滑らかなアニメーションを適用する。
                        val context = LocalContext.current
                        val reduceMotion = remember(context) { isReduceMotionEnabled(context) }
                        val isSingle = sortedTimers.size == 1

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val availableWidth = (maxWidth - (COMPACT_GRID_HORIZONTAL_PADDING * 2)).coerceAtLeast(0.dp)
                            val compactCardWidth = calculateCompactCardWidth(availableWidth)
                            val compactCardHeight = calculateCompactCardHeight(compactCardWidth)

                            val gridHorizontalPadding = if (isSingle) TIMER_CARD_HORIZONTAL_PADDING else COMPACT_GRID_HORIZONTAL_PADDING
                            val animatedGridPadding by animateDpAsState(
                                targetValue = gridHorizontalPadding,
                                animationSpec = if (reduceMotion) snap() else tween(durationMillis = TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
                                label = "GridHorizontalPadding",
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(
                                    start = animatedGridPadding,
                                    end = animatedGridPadding,
                                    bottom = 96.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(COMPACT_GRID_COLUMN_SPACING),
                                verticalArrangement = Arrangement.spacedBy(COMPACT_GRID_ROW_SPACING),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(
                                    items = sortedTimers,
                                    key = { it.id },
                                    span = {
                                        if (isSingle) GridItemSpan(2) else GridItemSpan(1)
                                    },
                                ) { timer ->
                                    val itemModifier = if (reduceMotion) {
                                        Modifier
                                    } else {
                                        Modifier.animateItem(
                                            fadeInSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
                                            fadeOutSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
                                            placementSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
                                        )
                                    }

                                    AnimatedContent(
                                        targetState = isSingle,
                                        transitionSpec = {
                                            if (reduceMotion) {
                                                EnterTransition.None.togetherWith(ExitTransition.None).using(SizeTransform { _, _ -> snap() })
                                            } else if (targetState) {
                                                // 2件以上から1件へ: 小さいカードから大きいカードへ広がりながらフェード
                                                (fadeIn(animationSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)) +
                                                    scaleIn(initialScale = 0.85f, animationSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)))
                                                    .togetherWith(
                                                        fadeOut(animationSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)) +
                                                            scaleOut(targetScale = 1.15f, animationSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing))
                                                    )
                                            } else {
                                                // 1件から2件以上へ: 大きいカードから小さいカードへ縮みながらフェード
                                                (fadeIn(animationSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)) +
                                                    scaleIn(initialScale = 1.15f, animationSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)))
                                                    .togetherWith(
                                                        fadeOut(animationSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)) +
                                                            scaleOut(targetScale = 0.85f, animationSpec = tween(TIMER_ITEM_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing))
                                                    )
                                            }
                                        },
                                        label = "TimerCardContentTransition",
                                        modifier = itemModifier,
                                    ) { single ->
                                        if (single) {
                                            TimerCard(
                                                timer = timer,
                                                nowElapsed = nowElapsed,
                                                nowWall = nowWall,
                                                onPause = { viewModel.pause(timer.id) },
                                                onResume = { viewModel.resume(timer.id) },
                                                onReset = { viewModel.reset(timer.id) },
                                                onExtend = { viewModel.extendOneMinute(timer.id) },
                                                onDelete = { viewModel.delete(timer.id) },
                                            )
                                        } else {
                                            CompactTimerCard(
                                                timer = timer,
                                                cardWidth = compactCardWidth,
                                                cardHeight = compactCardHeight,
                                                nowElapsed = nowElapsed,
                                                nowWall = nowWall,
                                                onPause = { viewModel.pause(timer.id) },
                                                onResume = { viewModel.resume(timer.id) },
                                                onReset = { viewModel.reset(timer.id) },
                                                onExtend = { viewModel.extendOneMinute(timer.id) },
                                                onDelete = { viewModel.delete(timer.id) },
                                            )
                                        }
                                    }
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
