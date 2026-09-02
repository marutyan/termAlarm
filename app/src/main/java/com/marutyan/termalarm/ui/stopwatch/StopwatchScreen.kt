package com.marutyan.termalarm.ui.stopwatch

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// 動作中(RUNNING)の画面表示を更新する間隔。ストップウォッチは1/100秒まで表示するのが一般的だが、
// 実際に100Hzで再描画すると電池を消費するだけで人の目には差が分からない。10Hz(100ms)なら
// 1/100秒表示の見た目上の滑らかさを保ちつつ再描画回数を1/10に抑えられるため、この値を採用した。
private const val TICK_INTERVAL_RUNNING_MILLIS = 100L

/**
 * ストップウォッチタブの画面。経過時間の大表示、開始・一時停止・再開・ラップ・リセットの操作、
 * ラップ一覧(各ラップの時間とその時点の合計)を持つ(docs/SPEC.md「ストップウォッチタブ」)。
 * 経過時間の表示は動作中(RUNNING)のときだけ100msごとに更新し、一時停止中/未開始は再描画しない。
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
            StopwatchControls(
                runState = state.runState,
                onStart = viewModel::start,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onReset = viewModel::reset,
                onLap = viewModel::lap,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LapList(laps = laps, modifier = Modifier.fillMaxSize())
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

@Composable
private fun StopwatchControls(
    runState: StopwatchRunState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onLap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (runState) {
            StopwatchRunState.IDLE -> {
                Button(onClick = onStart, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.stopwatch_start))
                }
            }
            StopwatchRunState.RUNNING -> {
                OutlinedButton(onClick = onLap, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.stopwatch_lap))
                }
                Button(onClick = onPause, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.stopwatch_pause))
                }
            }
            StopwatchRunState.PAUSED -> {
                OutlinedButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.stopwatch_reset))
                }
                Button(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.stopwatch_resume))
                }
            }
        }
    }
}

// ラップ一覧。新しいラップほど上に出す(docs/SPEC.md「各ラップの時間と、その時点の合計を並べる」)
@Composable
private fun LapList(laps: List<StopwatchLap>, modifier: Modifier = Modifier) {
    if (laps.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.stopwatch_laps_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(laps.asReversed(), key = { it.lapNumber }) { lap ->
            LapRow(lap)
            HorizontalDivider()
        }
    }
}

@Composable
private fun LapRow(lap: StopwatchLap) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.stopwatch_lap_number, lap.lapNumber),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = formatElapsed(lap.lapMillis, includeCentiseconds = true),
            style = MaterialTheme.typography.bodyLarge.tabularNums(),
        )
        Text(
            text = stringResource(R.string.stopwatch_lap_total, formatElapsed(lap.totalMillis, includeCentiseconds = true)),
            style = MaterialTheme.typography.bodyLarge.tabularNums(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
