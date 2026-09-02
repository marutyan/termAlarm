package com.marutyan.termalarm.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.marutyan.termalarm.R

/**
 * 時計・タイマー・ストップウォッチタブの空画面。今回はアラームタブのみ実装するため、
 * タブ枠だけ置いて「今後対応予定」とだけ分かる最小の表示にする(docs/SPEC.md「画面範囲」)。
 */
@Composable
fun PlaceholderTabScreen(title: String, bottomBar: @Composable () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title, style = MaterialTheme.typography.headlineMedium) }) },
        bottomBar = bottomBar,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.tab_coming_soon),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
