package com.marutyan.termalarm.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.marutyan.termalarm.R

/**
 * アプリ情報とオープンソースライセンス(Google Sans FlexのSIL OFL)を表示する画面。
 * 一覧画面右上のメニューから遷移する。最小限の実装として、スクロール可能なテキスト表示のみ持つ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            val licenseText = rememberFontLicenseText()
            Text(text = licenseText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// 同梱しているフォントのライセンス本文を資源から読む。
// 本文をコードへ写すと、フォントを差し替えたときに古い表示が残るため、資源を唯一の出どころにする。
@Composable
private fun rememberFontLicenseText(): String {
    val resources = LocalResources.current
    return remember(resources) {
        resources.openRawResource(R.raw.font_license)
            .bufferedReader()
            .use { it.readText() }
    }
}
