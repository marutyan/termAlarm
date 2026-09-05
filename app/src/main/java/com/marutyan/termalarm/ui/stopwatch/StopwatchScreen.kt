package com.marutyan.termalarm.ui.stopwatch

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.ui.theme.COMPACT_SCREEN_HEIGHT_THRESHOLD
import com.marutyan.termalarm.ui.theme.heroClock
import com.marutyan.termalarm.ui.common.TermAlarmOverflowMenu
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.StopwatchLap
import com.marutyan.termalarm.domain.StopwatchRunState
import com.marutyan.termalarm.domain.elapsedMillis
import com.marutyan.termalarm.stopwatch.formatElapsed
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.marutyan.termalarm.ui.theme.pressScaleEffect
import com.marutyan.termalarm.ui.theme.tabularNums
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 動作中(RUNNING)の画面表示を更新する間隔。ストップウォッチは1/100秒まで表示するのが一般的だが、
// 実際に100Hzで再描画すると電池を消費するだけで人の目には差が分からない。10Hz(100ms)なら
// 1/100秒表示の見た目上の滑らかさを保ちつつ再描画回数を1/10に抑えられるため、この値を採用した。
private const val TICK_INTERVAL_RUNNING_MILLIS = 100L

// 操作ボタンの高さと左右余白。純正の実測値そのまま(docs/OFFICIAL_UI.md「ストップウォッチ」)
private val CONTROL_BUTTON_HEIGHT = 104.dp

// 画面の高さが狭いときに使う操作ボタンの高さ。分割画面でも3つのボタンが収まるように小さくする
private val COMPACT_CONTROL_BUTTON_HEIGHT = 56.dp
private val SCREEN_HORIZONTAL_PADDING = 13.dp

/**
 * ストップウォッチタブの画面。design/tabs/Stopwatch.dc.htmlを再現する。画面上部に余白を詰めた特大の経過時間、
 * その下に横並びのラップカード、最下部に固定された縦3つの巨大な操作ボタンを置く
 * (docs/OFFICIAL_UI.md「ストップウォッチ」)。ラップを刻んでもボタンが押し下げられないよう画面下部に固定する。
 * 経過時間の表示は動作中(RUNNING)のときだけ100msごとに更新し、一時停止中/未開始は再描画しない。
 */
@Composable
fun StopwatchScreen(
    viewModel: StopwatchViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    bottomBar: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val laps by viewModel.laps.collectAsStateWithLifecycle()
    val isRunning = state.runState == StopwatchRunState.RUNNING
    val (nowElapsed, nowWall) = rememberTickingNow(isRunning)
    val elapsed = elapsedMillis(state, nowElapsed, nowWall)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_stopwatch), style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    TermAlarmOverflowMenu(
                        onOpenSettings = onOpenSettings,
                        onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                        onOpenAbout = onOpenAbout,
                    )
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val screenMaxHeight = maxHeight
            val isCompact = screenMaxHeight < COMPACT_SCREEN_HEIGHT_THRESHOLD
            val buttonHeight = if (isCompact) COMPACT_CONTROL_BUTTON_HEIGHT else CONTROL_BUTTON_HEIGHT
            val controlsBottomPadding = if (isCompact) 16.dp else 96.dp
            val timeVerticalPadding = if (isCompact) 4.dp else 8.dp
            val buttonSpacing = if (isCompact) 8.dp else 12.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = screenMaxHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val formatted = formatElapsed(elapsed, includeCentiseconds = true)
                        val dotIndex = formatted.indexOf('.')
                        val (mainPart, centisPart) = if (dotIndex >= 0) {
                            formatted.substring(0, dotIndex) to formatted.substring(dotIndex)
                        } else {
                            formatted to ""
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = timeVerticalPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 数字は動かさない。純正も経過時間の数字は動かさず、静かに入れ替える
                            Text(
                                text = mainPart + centisPart,
                                style = if (isCompact) {
                                    MaterialTheme.typography.displayMedium.tabularNums()
                                } else {
                                    MaterialTheme.typography.displayLarge.heroClock()
                                },
                                // まだ計測していないときは地に近い色にして、動いていないことを見て分かるようにする
                                color = if (state.runState == StopwatchRunState.IDLE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        LapRow(
                            laps = laps,
                            modifier = Modifier.padding(horizontal = SCREEN_HORIZONTAL_PADDING),
                        )
                    }

                    StopwatchControls(
                        runState = state.runState,
                        onStart = viewModel::start,
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onReset = viewModel::reset,
                        onLap = viewModel::lap,
                        buttonHeight = buttonHeight,
                        buttonSpacing = buttonSpacing,
                        // 純正の開始ボタンは画面の下端に張り付かず、少し上に浮いている
                        modifier = Modifier.padding(
                            start = SCREEN_HORIZONTAL_PADDING,
                            end = SCREEN_HORIZONTAL_PADDING,
                            bottom = controlsBottomPadding,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 100ms(RUNNING中)または1s(それ以外の遷移時に1回だけ最新化する)ごとに更新される
 * (elapsedRealtime, wallClock)のペア。domain.elapsedMillis()の再計算だけに使い、DBへは書き込まない
 * (timer機能のTimerScreen.rememberTickingNowを100ms間隔に作り直したもの。実装は共有せず
 * ストップウォッチ画面専用として持つ)。isRunning=falseの間はループを止めて無駄な再描画をしない。
 */
@Composable
private fun rememberTickingNow(isRunning: Boolean): Pair<Long, Long> {
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var nowWall by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        while (true) {
            delay(TICK_INTERVAL_RUNNING_MILLIS)
            nowElapsed = SystemClock.elapsedRealtime()
            nowWall = System.currentTimeMillis()
        }
    }
    return nowElapsed to nowWall
}

/**
 * 縦に並ぶ3つの操作ボタン。上段はIDLE/RUNNING/PAUSEDに応じて開始・一時停止・再開のいずれかへ切り替わる
 * 「主役」のボタンで、動作中(RUNNING)の一時停止だけerror色で目立たせる(docs/OFFICIAL_UI.md「ストップウォッチ」)。
 * 狭い画面ではbuttonHeightとbuttonSpacingを縮めて全体が収まりやすくする。
 * リセットはPAUSEDのときだけ、ラップはRUNNINGのときだけ押せる(元の実装の状態遷移をそのまま維持)。
 */
@Composable
private fun StopwatchControls(
    runState: StopwatchRunState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onLap: () -> Unit,
    buttonHeight: Dp = CONTROL_BUTTON_HEIGHT,
    buttonSpacing: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    val isRunning = runState == StopwatchRunState.RUNNING
    val (primaryLabel, primaryAction) = when (runState) {
        StopwatchRunState.IDLE -> R.string.stopwatch_start to onStart
        StopwatchRunState.RUNNING -> R.string.stopwatch_pause to onPause
        StopwatchRunState.PAUSED -> R.string.stopwatch_resume to onResume
    }

    // 開始・停止の切り替え時にボタンの色を滑らかに移行させるアニメーション値(0f: 通常, 1f: 停止操作)。
    // 状態変化の瞬間だけ200msで補間し、常時動き続けず電池を消費しないようにする。
    val transitionProgress by animateFloatAsState(
        targetValue = if (isRunning) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "StopwatchButtonTransition",
    )
    val primaryContainerColor = lerp(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.error,
        transitionProgress,
    )
    val primaryContentColor = lerp(
        MaterialTheme.colorScheme.onPrimary,
        MaterialTheme.colorScheme.onError,
        transitionProgress,
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(buttonSpacing)) {
        // 一時停止(=停止)のときだけerror色。開始・再開は主要な操作なのでprimaryを使う
        // (docs/OFFICIAL_UI.md「共通」の色対応表)。切り替え時はanimateFloatAsStateで滑らかに遷移する
        ControlButton(
            label = stringResource(primaryLabel),
            onClick = primaryAction,
            containerColor = primaryContainerColor,
            contentColor = primaryContentColor,
            buttonHeight = buttonHeight,
        )
        // まだ計測していないときは「開始」だけを出す。純正も同じで、押せないボタンを並べない。
        // 一度でも動かした後は、止める・戻す・刻むの3つが要る
        if (runState != StopwatchRunState.IDLE) {
            ControlButton(
                label = stringResource(R.string.stopwatch_reset),
                onClick = onReset,
                enabled = runState == StopwatchRunState.PAUSED,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                buttonHeight = buttonHeight,
            )
            ControlButton(
                label = stringResource(R.string.stopwatch_lap),
                onClick = onLap,
                enabled = isRunning,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                buttonHeight = buttonHeight,
            )
        }
    }
}

// 3つの操作ボタンに共通する見た目(完全な丸角)だけをまとめた小さな部品。
// buttonHeightにより通常時と画面高不足時のサイズ切り替えに対応する。
@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    buttonHeight: Dp = CONTROL_BUTTON_HEIGHT,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .pressScaleEffect(interactionSource),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * ラップ一覧。design/tabs/Stopwatch.dc.htmlと同じく横並びのカードにし、右上の矢印ボタンで送る
 * (docs/OFFICIAL_UI.md「ストップウォッチ」)。ラップが無い間はStopwatchScreenTestが検証する
 * 案内文だけを表示する。
 */
@Composable
private fun LapRow(laps: List<StopwatchLap>, modifier: Modifier = Modifier) {
    // 純正はラップが無いとき何も出さない。案内文を置くと、押せない操作があるように見える
    if (laps.isEmpty()) return

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 新しいラップが増えるたびに最新(末尾)が見えるよう自動でスクロールする
    LaunchedEffect(laps.size) {
        if (laps.isNotEmpty()) listState.animateScrollToItem(laps.lastIndex)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            LapScrollButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.stopwatch_lap_scroll_previous),
                onClick = { scope.launch { listState.animateScrollToItem(maxOf(0, listState.firstVisibleItemIndex - 1)) } },
            )
            LapScrollButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.stopwatch_lap_scroll_next),
                onClick = { scope.launch { listState.animateScrollToItem(minOf(laps.lastIndex, listState.firstVisibleItemIndex + 1)) } },
            )
        }
        // 端まで詰めると、スクロールした時に隣のカードが切れて見える。左右へ余白を置く
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            itemsIndexed(laps, key = { _, lap -> lap.lapNumber }) { index, lap ->
                LapCard(lap, isLatest = index == laps.lastIndex)
            }
        }
    }
}

@Composable
private fun LapScrollButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ラップ1件のカード。周回数・そのラップの時間・その時点の合計を縦3行で並べる(docs/OFFICIAL_UI.md)
// isLatestは直前に刻んだラップ。純正と同じく色を変えて、どれが今のものか一目で分かるようにする
@Composable
private fun LapCard(lap: StopwatchLap, isLatest: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isLatest) 2.dp else 1.dp,
            color = if (isLatest) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.width(84.dp).padding(vertical = 16.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.stopwatch_lap_number, lap.lapNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLatest) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatElapsed(lap.lapMillis, includeCentiseconds = true),
                style = MaterialTheme.typography.bodyMedium.tabularNums(),
                color = if (isLatest) accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.stopwatch_lap_total, formatElapsed(lap.totalMillis, includeCentiseconds = true)),
                style = MaterialTheme.typography.bodySmall.tabularNums(),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
