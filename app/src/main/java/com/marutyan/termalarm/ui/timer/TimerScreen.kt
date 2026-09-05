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

/**
 * タイマータブの画面。動作中のタイマー一覧(複数同時表示)を円形リングのカードとして並べる
 * (docs/OFFICIAL_UI.md「タイマー」)。時分秒の入力は画面上から無くし、右下のFABから
 * 開くTimerAddScreenへ追い出した。Add画面はNavHostのルートではなくこの画面内のローカルな
 * 状態切り替えとして表示する(NavHostは変更禁止のため、経路を増やさずに完結させる)。
 * 残り時間の表示は1秒ごとに更新する。
 */
// 右下の追加ボタンの大きさ。純正の実測値に合わせている
private val FAB_SIZE = 65.dp

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
    // 通知に出る秒と画面の秒を合わせる。詳しくはrememberTickingNowを参照
    val tickingNow = rememberTickingNow(timers)
    val (nowElapsed, nowWall) = tickingNow
    val sortedTimers = remember(timers, nowElapsed, nowWall) {
        sortTimers(timers, nowElapsed, nowWall)
    }
    // 純正はタイマーが1件も無いとき、案内文ではなくテンキーをそのまま出す。
    // 追加ボタンを押したときも同じテンキーなので、どちらの理由でも同じ画面を使う
    val showKeypad = showAddScreen || sortedTimers.isEmpty()

    // 枠は1つだけ持ち、中身を入れ替える。追加画面のときも下部ナビを見せたままにするため
    // (純正も同じで、テンキーを出している間ナビは消えない)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_timer), style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    TermAlarmOverflowMenu(
                        onOpenSettings = onOpenSettings,
                        onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                        onOpenAbout = onOpenAbout,
                    )
                },
            )
        },
        floatingActionButton = {
            // テンキーを出している間は、追加ボタンを隠す
            if (!showKeypad) {
                FloatingActionButton(
                    onClick = { showAddScreen = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // 純正を実測すると65dp。既定のままでは45dpしかなく、押す場所として小さい
                    modifier = Modifier.size(FAB_SIZE),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.timer_add))
                }
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
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
            modifier = Modifier.padding(padding),
        ) { isKeypad ->
            if (isKeypad) {
                TimerAddScreen(
                    onStart = { h, m, s -> viewModel.start(h, m, s); showAddScreen = false },
                    // 1件も無いときはテンキーがタブそのものの中身なので、戻る先が無い
                    onClose = if (sortedTimers.isEmpty()) null else ({ showAddScreen = false }),
                )
            } else {
                LazyColumn(
                    // 下を厚くしておかないと、最後のカードが右下の追加ボタンに隠れる
                    contentPadding = PaddingValues(start = 13.dp, end = 13.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                            // 残り時間が縮んで並び順が変わったとき、その場で飛ばず動いて入れ替わるようにする
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 残り時間の表示に使う「今」。画面が見えている間だけ進める。
 * 再計算に使うだけで、データベースへは書き込まない。
 *
 * ただ1秒ごとに数えると、通知に出る秒と画面の秒が最大1秒ずれる。
 * 通知は「残り時間が尽きる時刻」から逆算して数えるため、こちらの起動時刻とは関係がない。
 * 残り時間が次の秒へ変わる瞬間に合わせて描き直すことで、通知と同じ数字を出す。
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
 * タイマー一覧を「次に鳴る順（残り時間が短い順）」に並べ替える。
 * 鳴動中(FINISHED)を最優先、次に動作中(RUNNING)を残り時間の昇順、一時停止中(PAUSED)は
 * 計測が止まっており動作中タイマーの視認性を邪魔しないよう末尾にまとめて残り時間の昇順で並べる。
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

