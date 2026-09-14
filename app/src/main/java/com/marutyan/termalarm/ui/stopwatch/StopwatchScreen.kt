package com.marutyan.termalarm.ui.stopwatch

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.marutyan.termalarm.ui.common.TOP_BAR_CONTENT_GAP
import com.marutyan.termalarm.ui.common.TOP_BAR_TOP_INSET
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.StopwatchLap
import com.marutyan.termalarm.domain.StopwatchRunState
import com.marutyan.termalarm.domain.elapsedMillis
import com.marutyan.termalarm.ui.common.TermAlarmTopBar
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.pressScaleEffect
import com.marutyan.termalarm.ui.theme.tabularNums
import kotlinx.coroutines.delay

/**
 * 動作中(RUNNING)の経過時間更新間隔(100ms = 10Hz)。
 * 1/100秒表示の滑らかさを保ちつつ描画負荷を抑える。
 */
private const val TICK_INTERVAL_RUNNING_MILLIS = 100L

/**
 * ストップウォッチタブの画面。design/Stopwatch.dc.html を再現する。
 * 見出し「ストップウォッチ」(28sp、太さ300)、分秒(66sp)と小数以下(34sp主役色)の経過時間、
 * 3つの等幅操作ボタン(リセット、ラップ、停止/開始: 高さ56dp、角丸28dp)、
 * ラップ一覧カード(1行52dp、最速ラップに主役色)を配置する。
 */
@Composable
fun StopwatchScreen(
    viewModel: StopwatchViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val laps by viewModel.laps.collectAsStateWithLifecycle()
    val isRunning = state.runState == StopwatchRunState.RUNNING
    val (nowElapsed, nowWall) = rememberTickingNow(isRunning)
    val elapsed = elapsedMillis(state, nowElapsed, nowWall)

    // ステータスバーの下端から74dp空けるため、WindowInsets.statusBarsの高さを足す
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = statusBarTop + TOP_BAR_TOP_INSET, start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        // 画面上部の帯: アプリ名と三点メニュー
        TermAlarmTopBar(
            onOpenSettings = onOpenSettings,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            onOpenAbout = onOpenAbout,
        )

        Spacer(modifier = Modifier.height(TOP_BAR_CONTENT_GAP))

        // 1. 見出し「ストップウォッチ」 (28sp、太さ300)
        Text(
            text = stringResource(R.string.tab_stopwatch),
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.W300,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.01).em,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 2. 経過時間: 分と秒を66sp・太さ200・等幅数字、小数以下を34spで主役の色
        val (mainPart, centisPart) = formatStopwatchMain(elapsed)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = mainPart,
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontWeight = FontWeight.W200,
                    fontSize = 66.sp,
                    lineHeight = 70.sp,
                    letterSpacing = (-0.05).em,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = centisPart,
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontWeight = FontWeight.W200,
                    fontSize = 34.sp,
                    lineHeight = 44.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. ボタン3つを同じ幅で。「リセット」「ラップ」「停止」(または「開始」)。高さ56dp、角丸28dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ボタン1: リセット
            val resetInteraction = remember { MutableInteractionSource() }
            val canReset = state.runState != StopwatchRunState.IDLE || elapsed > 0L || laps.isNotEmpty()
            Surface(
                onClick = { if (canReset) viewModel.reset() },
                interactionSource = resetInteraction,
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .pressScaleEffect(resetInteraction),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.stopwatch_reset),
                        style = TextStyle(fontSize = 15.sp),
                        color = if (canReset) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.customColors.subtleText.copy(alpha = 0.5f)
                        },
                    )
                }
            }

            // ボタン2: ラップ
            val lapInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = { if (isRunning) viewModel.lap() },
                interactionSource = lapInteraction,
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .pressScaleEffect(lapInteraction),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.stopwatch_lap),
                        style = TextStyle(fontSize = 15.sp),
                        color = if (isRunning) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.customColors.subtleText.copy(alpha = 0.5f)
                        },
                    )
                }
            }

            // ボタン3: 停止 または 開始
            val actionInteraction = remember { MutableInteractionSource() }
            val actionDesc = stringResource(if (isRunning) R.string.stopwatch_pause else R.string.stopwatch_start)
            Surface(
                onClick = {
                    when (state.runState) {
                        StopwatchRunState.RUNNING -> viewModel.pause()
                        StopwatchRunState.IDLE -> viewModel.start()
                        StopwatchRunState.PAUSED -> viewModel.resume()
                    }
                },
                interactionSource = actionInteraction,
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .semantics { contentDescription = actionDesc }
                    .pressScaleEffect(actionInteraction),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    if (isRunning) {
                        // 停止アイコン (縦2本線)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(width = 3.dp, height = 14.dp)
                                    .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(1.dp)),
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 3.dp, height = 14.dp)
                                    .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(1.dp)),
                            )
                        }
                        Text(
                            text = stringResource(R.string.stopwatch_pause),
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    } else {
                        // 開始アイコン (三角)
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.stopwatch_start),
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. ラップの一覧。カードにまとめ、1行52dp。番号、そのラップの時間16sp、その時点の合計13sp。一番速いラップに主役の色を付ける
        if (laps.isNotEmpty()) {
            val fastestLapMillis = laps.minOf { it.lapMillis }
            val reversedLaps = remember(laps) { laps.reversed() }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    reversedLaps.forEachIndexed { index, lap ->
                        val isFastest = lap.lapMillis == fastestLapMillis
                        val isLastItem = index == reversedLaps.lastIndex

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        ) {
                            // ラップ番号 (幅34dp、13sp、薄い文字色)
                            Text(
                                text = lap.lapNumber.toString(),
                                style = TextStyle(
                                    fontFamily = IbmPlexMono,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.customColors.subtleText,
                                    fontFeatureSettings = "tnum",
                                ),
                                modifier = Modifier.width(34.dp),
                            )

                            // そのラップの時間 (16sp、一番速いラップに主役の色を付ける)
                            Text(
                                text = formatStopwatchLap(lap.lapMillis),
                                style = TextStyle(
                                    fontFamily = IbmPlexMono,
                                    fontSize = 16.sp,
                                    fontFeatureSettings = "tnum",
                                    color = if (isFastest) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                ),
                                modifier = Modifier.weight(1f),
                            )

                            // その時点の合計 (13sp、薄い文字色)
                            Text(
                                text = formatStopwatchLap(lap.totalMillis),
                                style = TextStyle(
                                    fontFamily = IbmPlexMono,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.customColors.subtleText,
                                    fontFeatureSettings = "tnum",
                                ),
                            )
                        }

                        // 行の区切り線 (最後の行以外)
                        if (!isLastItem) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.surface),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 動作中(RUNNING)のときに100msごとに現在時刻を更新するComposable。
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
 * メインの経過時間文字列を分秒と小数以下に分割して整形する。
 * design/Stopwatch.dc.html に合わせ、1時間未満は「0:42」と「.18」、1時間以上は「1:23:45」と「.18」とする。
 */
private fun formatStopwatchMain(millis: Long): Pair<String, String> {
    val clamped = millis.coerceAtLeast(0L)
    val hours = clamped / 3_600_000L
    val minutes = (clamped % 3_600_000L) / 60_000L
    val seconds = (clamped % 60_000L) / 1000L
    val centis = (clamped % 1000L) / 10L
    val mainPart = if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
    val centisPart = ".%02d".format(centis)
    return mainPart to centisPart
}

/**
 * ラップ一覧の各行に表示する時間を整形する。
 * design/Stopwatch.dc.html に合わせ、1時間未満は「0:09.42」、1時間以上は「1:23:45.67」とする。
 */
private fun formatStopwatchLap(millis: Long): String {
    val clamped = millis.coerceAtLeast(0L)
    val hours = clamped / 3_600_000L
    val minutes = (clamped % 3_600_000L) / 60_000L
    val seconds = (clamped % 60_000L) / 1000L
    val centis = (clamped % 1000L) / 10L
    return if (hours > 0) {
        "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centis)
    } else {
        "%d:%02d.%02d".format(minutes, seconds, centis)
    }
}
