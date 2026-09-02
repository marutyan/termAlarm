package com.marutyan.termalarm.ui.timer

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.remainingMillis
import com.marutyan.termalarm.timer.formatDuration
import com.marutyan.termalarm.ui.theme.tabularNums
import kotlinx.coroutines.delay

/**
 * タイマータブの画面。時分秒を指定して開始する入力と、動作中のタイマー一覧(複数同時表示)を持つ
 * (docs/SPEC.md「タイマータブ」)。残り時間の表示は1秒ごとに更新する。
 */
@Composable
fun TimerScreen(viewModel: TimerViewModel, bottomBar: @Composable () -> Unit) {
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val (nowElapsed, nowWall) = rememberTickingNow()

    var hours by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(5) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_timer), style = MaterialTheme.typography.headlineMedium) }) },
        bottomBar = bottomBar,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TimerInputRow(
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                onHoursChange = { hours = it },
                onMinutesChange = { minutes = it },
                onSecondsChange = { seconds = it },
                onStart = { viewModel.start(hours, minutes, seconds) },
            )
            if (timers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.timer_list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
private fun TimerInputRow(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NumberStepper(stringResource(R.string.timer_input_hours), hours, 0..23, onHoursChange)
            NumberStepper(stringResource(R.string.timer_input_minutes), minutes, 0..59, onMinutesChange)
            NumberStepper(stringResource(R.string.timer_input_seconds), seconds, 0..59, onSecondsChange)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onStart,
            enabled = hours > 0 || minutes > 0 || seconds > 0,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.timer_start))
        }
    }
}

// 時/分/秒それぞれの入力用ステッパー。IMEを出さずタップだけで完結させる
@Composable
private fun NumberStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val decreaseDescription = stringResource(R.string.timer_decrease)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > range.first) onChange(value - 1) }, modifier = Modifier.size(48.dp)) {
                // Icons.Filled.Removeはmaterial-icons-coreに含まれないため、Textでマイナス記号を出す
                Text(
                    text = "－",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { contentDescription = decreaseDescription },
                )
            }
            Text(
                text = value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.headlineSmall.tabularNums(),
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { if (value < range.last) onChange(value + 1) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.timer_increase))
            }
        }
    }
}

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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFinished) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = timer.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(remaining),
                style = MaterialTheme.typography.displaySmall.tabularNums(),
            )
            if (!isFinished) {
                LinearProgressIndicator(
                    progress = { if (timer.totalMillis > 0) remaining / timer.totalMillis.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isFinished) {
                    // 完了(鳴動中)は「停止」だけを出す。停止=タイマー自体の削除(domain/TimerState.ktの契約)
                    Button(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.timer_stop))
                    }
                } else {
                    if (timer.runState == TimerRunState.RUNNING) {
                        OutlinedButton(onClick = onPause, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.timer_pause))
                        }
                    } else {
                        OutlinedButton(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.timer_resume))
                        }
                    }
                    OutlinedButton(onClick = onExtend, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.timer_extend_one_minute))
                    }
                    OutlinedButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.timer_reset))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.timer_delete))
                    }
                }
            }
        }
    }
}
