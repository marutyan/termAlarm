package com.marutyan.termalarm.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.customColors

/**
 * 後続タスクで再構築される画面のためのプレースホルダComposable。
 * 開発中の画面へ遷移した際に「準備中」の文言を表示するために用いる。
 */
@Composable
fun PlaceholderScreen(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.placeholder_coming_soon),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.customColors.subtleText,
        )
    }
}
