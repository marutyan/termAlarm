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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.ui.theme.subHeroClock
import com.marutyan.termalarm.ui.common.TermAlarmOverflowMenu
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.remainingMillis
import com.marutyan.termalarm.ui.theme.tabularNums
import kotlinx.coroutines.delay

// タイマーカードに描く円形リングの直径と線の太さ。純正の実測値(docs/OFFICIAL_UI.md「タイマー」)は
// 直径311dp/線11dpだが、リングの右側に2つの操作ボタンを横並びで収めるため、画面幅に合わせて直径を200dpへ縮小した。
private val RING_DIAMETER = 200.dp
private val RING_STROKE_WIDTH = 11.dp

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


/**
 * タイマータブの画面。動作中のタイマー一覧(複数同時表示)を円形リングのカードとして並べる
 * (docs/OFFICIAL_UI.md「タイマー」)。時分秒の入力は画面上から無くし、右下のFABから
 * 開くTimerAddScreenへ追い出した。Add画面はNavHostのルートではなくこの画面内のローカルな
 * 状態切り替えとして表示する(NavHostは変更禁止のため、経路を増やさずに完結させる)。
 * 残り時間の表示は1秒ごとに更新する。
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

    if (showAddScreen) {
        TimerAddScreen(
            onStart = { h, m, s -> viewModel.start(h, m, s); showAddScreen = false },
            onClose = { showAddScreen = false },
        )
        return
    }

    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val (nowElapsed, nowWall) = rememberTickingNow()
    val sortedTimers = remember(timers, nowElapsed, nowWall) {
        sortTimers(timers, nowElapsed, nowWall)
    }

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
            // アラーム一覧と同じく、純正に合わせて明るい色にする
            FloatingActionButton(
                onClick = { showAddScreen = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.timer_add))
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        if (sortedTimers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.timer_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
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

/**
 * 1秒ごとに更新される(elapsedRealtime, wallClock)のペア。domain.remainingMillis()の再計算だけに使い、
 * DBへは書き込まない(ui/alarmlist/AlarmListScreen.ktのrememberCurrentMinute()を分単位→秒単位に
 * 合わせて作り直したもの。実装は共有せずタイマー画面専用として持つ)。
 */
@Composable
private fun rememberTickingNow(): Pair<Long, Long> {
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var nowWall by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowElapsed = SystemClock.elapsedRealtime()
            nowWall = System.currentTimeMillis()
        }
    }
    return nowElapsed to nowWall
}

/**
 * タイマー1件のカード。純正の時計アプリに合わせて左右余白13dp・角丸28dpのカードに、
 * 円形リングと残り時間、その下にリセットアイコンを置き、右側に操作ボタンを縦並びで配置する。
 */
@Composable
private fun TimerCard(
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
    val progress = if (timer.totalMillis > 0) (remaining.toFloat() / timer.totalMillis.toFloat()).coerceIn(0f, 1f) else 0f

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
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    TimerRing(
                        progress = progress,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        progressColor = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = formatTimerRemaining(remaining),
                        style = MaterialTheme.typography.displayLarge.subHeroClock().tabularNums(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(RING_DIAMETER), contentAlignment = Alignment.Center) {
                        TimerRing(
                            progress = progress,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            progressColor = MaterialTheme.colorScheme.primary,
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = formatTimerRemaining(remaining),
                                style = (when {
                                    remaining >= 3600_000L -> MaterialTheme.typography.headlineLarge
                                    remaining < 60_000L -> MaterialTheme.typography.displayLarge
                                    else -> MaterialTheme.typography.displayMedium
                                }).tabularNums(),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            IconButton(onClick = onReset) {
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

/**
 * 円形の進捗リング。背景の全周弧と残り時間の弧を描き、先端に進捗を示す丸を配置する。
 */
@Composable
private fun TimerRing(progress: Float, trackColor: Color, progressColor: Color) {
    val strokeWidthPx = with(LocalDensity.current) { RING_STROKE_WIDTH.toPx() }
    Canvas(modifier = Modifier.size(RING_DIAMETER)) {
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
private fun ExtendChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(80.dp).height(56.dp),
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
private fun PlayPauseButton(isRunning: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (isRunning) R.string.timer_pause else R.string.timer_resume)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(80.dp).height(56.dp).semantics { contentDescription = description },
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

