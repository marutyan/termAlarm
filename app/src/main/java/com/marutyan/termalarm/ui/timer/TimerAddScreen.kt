package com.marutyan.termalarm.ui.timer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.tabularNums

/**
 * タイマー新規追加画面。純正の時計アプリと同じく、時分秒の入力はタイマー一覧の画面から追い出し、
 * FAB経由で開くこの専用画面だけに置く(docs/OFFICIAL_UI.md「タイマー」)。
 * NavHostのルートではなくTimerScreen内のローカルな状態切り替えとして表示するため、
 * システムの戻る操作にはBackHandlerで対応する。
 */
@Composable
fun TimerAddScreen(onStart: (hours: Int, minutes: Int, seconds: Int) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)

    var hours by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(5) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timer_add), style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NumberStepper(stringResource(R.string.timer_input_hours), hours, 0..23) { hours = it }
                    NumberStepper(stringResource(R.string.timer_input_minutes), minutes, 0..59) { minutes = it }
                    NumberStepper(stringResource(R.string.timer_input_seconds), seconds, 0..59) { seconds = it }
                }
            }
            Button(
                onClick = { onStart(hours, minutes, seconds) },
                enabled = hours > 0 || minutes > 0 || seconds > 0,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.timer_start), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

// 時/分/秒それぞれの入力用ステッパー。IMEを出さずタップだけで完結させる
// (旧TimerScreenの同名関数をこの追加専用画面へ移した)
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
