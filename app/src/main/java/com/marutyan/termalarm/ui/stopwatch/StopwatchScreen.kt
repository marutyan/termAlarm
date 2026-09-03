package com.marutyan.termalarm.ui.stopwatch

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.StopwatchLap
import com.marutyan.termalarm.domain.StopwatchRunState
import com.marutyan.termalarm.domain.elapsedMillis
import com.marutyan.termalarm.stopwatch.formatElapsed
import com.marutyan.termalarm.ui.theme.tabularNums
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 動作中(RUNNING)の画面表示を更新する間隔。ストップウォッチは1/100秒まで表示するのが一般的だが、
// 実際に100Hzで再描画すると電池を消費するだけで人の目には差が分からない。10Hz(100ms)なら
// 1/100秒表示の見た目上の滑らかさを保ちつつ再描画回数を1/10に抑えられるため、この値を採用した。
private const val TICK_INTERVAL_RUNNING_MILLIS = 100L

// 操作ボタンの高さと左右余白。純正の実測値そのまま(docs/OFFICIAL_UI.md「ストップウォッチ」)
private val CONTROL_BUTTON_HEIGHT = 104.dp
private val SCREEN_HORIZONTAL_PADDING = 13.dp

/**
 * ストップウォッチタブの画面。design/tabs/Stopwatch.dc.htmlを再現する。画面上部に特大の経過時間、
 * その下に横並びのラップカード、残りの領域に縦3つの巨大な操作ボタンを置く
 * (docs/OFFICIAL_UI.md「ストップウォッチ」)。経過時間の表示は動作中(RUNNING)のときだけ100msごとに
 * 更新し、一時停止中/未開始は再描画しない。
 */
@Composable
fun StopwatchScreen(viewModel: StopwatchViewModel, bottomBar: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val laps by viewModel.laps.collectAsStateWithLifecycle()
    val isRunning = state.runState == StopwatchRunState.RUNNING
    val (nowElapsed, nowWall) = rememberTickingNow(isRunning)
    val elapsed = elapsedMillis(state, nowElapsed, nowWall)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_stopwatch), style = MaterialTheme.typography.headlineMedium) }) },
        bottomBar = bottomBar,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = formatElapsed(elapsed, includeCentiseconds = true),
                style = MaterialTheme.typography.displayLarge.tabularNums(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                textAlign = TextAlign.Center,
            )
            LapRow(laps = laps, modifier = Modifier.padding(horizontal = SCREEN_HORIZONTAL_PADDING))
            StopwatchControls(
                runState = state.runState,
                onStart = viewModel::start,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onReset = viewModel::reset,
                onLap = viewModel::lap,
                modifier = Modifier.padding(top = 32.dp, start = SCREEN_HORIZONTAL_PADDING, end = SCREEN_HORIZONTAL_PADDING),
            )
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
 * 縦に並ぶ3つの巨大な操作ボタン(高さ104dp)。上段はIDLE/RUNNING/PAUSEDに応じて
 * 開始・一時停止・再開のいずれかへ切り替わる「主役」のボタンで、動作中(RUNNING)の
 * 一時停止だけerror色で目立たせる(docs/OFFICIAL_UI.md「ストップウォッチ」)。
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
    modifier: Modifier = Modifier,
) {
    val isRunning = runState == StopwatchRunState.RUNNING
    val (primaryLabel, primaryAction) = when (runState) {
        StopwatchRunState.IDLE -> R.string.stopwatch_start to onStart
        StopwatchRunState.RUNNING -> R.string.stopwatch_pause to onPause
        StopwatchRunState.PAUSED -> R.string.stopwatch_resume to onResume
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 一時停止(=停止)のときだけerror色。開始・再開は「主要な操作ボタン」としてprimaryContainerを使う
        // (docs/OFFICIAL_UI.md「共通」の色対応表)
        ControlButton(
            label = stringResource(primaryLabel),
            onClick = primaryAction,
            containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (isRunning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ControlButton(
            label = stringResource(R.string.stopwatch_reset),
            onClick = onReset,
            enabled = runState == StopwatchRunState.PAUSED,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        ControlButton(
            label = stringResource(R.string.stopwatch_lap),
            onClick = onLap,
            enabled = isRunning,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// 3つの操作ボタンに共通する見た目(高さ104dp・完全な丸角)だけをまとめた小さな部品。
// 呼び出し箇所がStopwatchControls内の3箇所のみのため、汎用コンポーネント化はしない
@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.fillMaxWidth().height(CONTROL_BUTTON_HEIGHT),
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
    if (laps.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.stopwatch_laps_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

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
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(laps, key = { it.lapNumber }) { lap -> LapCard(lap) }
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
@Composable
private fun LapCard(lap: StopwatchLap) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.width(96.dp).padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.stopwatch_lap_number, lap.lapNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatElapsed(lap.lapMillis, includeCentiseconds = true),
                style = MaterialTheme.typography.bodyMedium.tabularNums(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.stopwatch_lap_total, formatElapsed(lap.totalMillis, includeCentiseconds = true)),
                style = MaterialTheme.typography.bodySmall.tabularNums(),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
