package com.marutyan.termalarm.ui.timer

import android.os.SystemClock
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
import com.marutyan.termalarm.timer.formatDuration
import com.marutyan.termalarm.ui.theme.tabularNums
import kotlinx.coroutines.delay

// タイマーカードに描く円形リングの直径と線の太さ。純正の実測値そのまま(docs/OFFICIAL_UI.md「タイマー」)
private val RING_DIAMETER = 311.dp
private val RING_STROKE_WIDTH = 11.dp

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
            FloatingActionButton(onClick = { showAddScreen = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.timer_add))
            }
        },
        bottomBar = bottomBar,
    ) { padding ->
        if (timers.isEmpty()) {
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
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(timers, key = { it.id }) { timer ->
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
 * タイマー1件のカード。design/tabs/Timer.dc.htmlを再現する。左右余白13dp・角丸28dpの大きなカードに、
 * 直径311dp/線11dpの円形リングと残り時間を中央に置き、操作を右寄せの行で並べる。
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
) {
    val remaining = remainingMillis(timer, nowElapsed, nowWall)
    val isFinished = timer.runState == TimerRunState.FINISHED
    val isRunning = timer.runState == TimerRunState.RUNNING
    // 残っている割合。リングの色付き部分の長さに使う(合計0はゼロ除算になるため0f扱い)
    val progress = if (timer.totalMillis > 0) (remaining.toFloat() / timer.totalMillis.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = stringResource(R.string.timer_card_title, timer.label),
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

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                TimerRing(
                    progress = progress,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    progressColor = if (isFinished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatDuration(remaining),
                    style = MaterialTheme.typography.displayLarge.subHeroClock(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (!isFinished) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // リセットは純正のモックには無いが、既存の「動作中に最初の時間へ戻す」機能を
                    // 消すわけにいかないため、+1分の隣に文字ボタンとして残す(仕様の穴。報告に記載)
                    TextButton(onClick = onReset) { Text(stringResource(R.string.timer_reset)) }
                    ExtendChip(onClick = onExtend)
                    PlayPauseButton(isRunning = isRunning, onClick = if (isRunning) onPause else onResume)
                }
            }
        }
    }
}

// 円形の進捗リング。Canvasへ背景の全周弧(track)と残り時間の弧(progress)を重ねて描くだけの
// シンプルな実装で、アニメーションや汎用化はしない(呼び出し箇所がTimerCard内の1箇所のみのため)。
@Composable
private fun TimerRing(progress: Float, trackColor: Color, progressColor: Color) {
    val strokeWidthPx = with(LocalDensity.current) { RING_STROKE_WIDTH.toPx() }
    Canvas(modifier = Modifier.size(RING_DIAMETER)) {
        val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        // ストロークが円の外へはみ出さないよう、半径分だけ内側に収めたサイズで弧を描く
        val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
        val topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
        drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        drawArc(color = progressColor, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
    }
}

// 「+1分」チップ。カード地より少し明るいsurfaceContainerHighを使い、円形リング下の操作行に置く
@Composable
private fun ExtendChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(modifier = Modifier.height(56.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.timer_extend_one_minute),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// 一時停止/再開を切り替える横長の丸ボタン。「主要な操作ボタン」としてprimaryContainerを使う
// (docs/OFFICIAL_UI.md「共通」の色対応表)。Pauseアイコンはmaterial-icons-coreに無いため、
// 2本の縦棒をBoxで手描きする(mockのSVGと同じ表現)。
@Composable
private fun PlayPauseButton(isRunning: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (isRunning) R.string.timer_pause else R.string.timer_resume)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(112.dp).height(56.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isRunning) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(width = 5.dp, height = 20.dp)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
