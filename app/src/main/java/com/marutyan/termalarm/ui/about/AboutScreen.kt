package com.marutyan.termalarm.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.customColors

/**
 * 依存ライブラリの名称と適用ライセンス名を保持するデータクラス。
 * 外部通信を行わずにライセンス情報画面で依存一覧を表示するために必要となる。
 */
private data class LibraryArtifact(
    val name: String,
    val license: String,
)

/**
 * Apache License 2.0 のライセンス表記文字列。
 * 一意のライセンス表記を一元管理するために定義する。
 */
private const val LICENSE_APACHE_2_0 = "Apache License 2.0"

/**
 * リリースAPKに含まれる依存ライブラリとそのライセンス名の一覧。
 * 外部通信を行わずにアプリ単体でライセンス情報を表示するために必要となる定数。
 *
 * この一覧は gradle/libs.versions.toml と app/build.gradle.kts の implementation 依存をもとに作成している。
 * testImplementation、androidTestImplementation、debugImplementation は配布物へ入らないため含めていない。
 * アプリに新しい依存ライブラリを追加した場合は、この一覧も更新する必要がある。
 */
private val DEPENDENCY_LIBRARIES: List<LibraryArtifact> = listOf(
    LibraryArtifact("androidx.activity:activity-compose", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.compose.foundation:foundation", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.compose.material:material-icons-core", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.compose.material3:material3", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.compose.ui:ui", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.compose.ui:ui-graphics", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.compose.ui:ui-tooling-preview", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.core:core-ktx", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.glance:glance-appwidget", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.lifecycle:lifecycle-runtime-compose", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.lifecycle:lifecycle-runtime-ktx", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.lifecycle:lifecycle-viewmodel-compose", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.navigation:navigation-compose", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.room:room-ktx", LICENSE_APACHE_2_0),
    LibraryArtifact("androidx.room:room-runtime", LICENSE_APACHE_2_0),
    LibraryArtifact("org.jetbrains.kotlinx:kotlinx-coroutines-android", LICENSE_APACHE_2_0),
    LibraryArtifact("org.jetbrains.kotlinx:kotlinx-serialization-core", LICENSE_APACHE_2_0),
)

/**
 * アプリ情報とオープンソースライセンス(IBM Plex MonoのSIL OFL、および依存ライブラリのApache License 2.0)を表示する画面。
 * 一覧画面右上のメニューから遷移する。スクロール可能な画面として依存一覧と各ライセンスの全文を表示する。
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // セクション1: このアプリが使っているもの
            AboutSectionTitle(
                text = stringResource(R.string.about_section_dependencies),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            DEPENDENCY_LIBRARIES.forEach { lib ->
                DependencyLibraryItem(
                    name = lib.name,
                    license = lib.license,
                )
            }

            // セクション2: Apache License 2.0
            AboutSectionTitle(
                text = stringResource(R.string.about_section_apache_license),
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            val apacheLicenseText = rememberRawResourceText(R.raw.apache_license_2_0)
            LicenseBodyText(text = apacheLicenseText)

            // セクション3: IBM Plex Mono
            AboutSectionTitle(
                text = stringResource(R.string.about_section_font_license),
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            val fontLicenseText = rememberRawResourceText(R.raw.font_license)
            LicenseBodyText(text = fontLicenseText)
        }
    }
}

/**
 * ライセンス画面のセクション見出しを表示するComposable。
 * アプリのタイポグラフィに合わせて見出しを太字で表示し、各ライセンス情報の区切りを明確にする役割を持つ。
 */
@Composable
private fun AboutSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * 依存ライブラリの名称とライセンス名を表示するComposable。
 * アプリが依存するオープンソースライブラリの識別子と適用ライセンスを1行ごとに分かりやすく提示するために用いる。
 */
@Composable
private fun DependencyLibraryItem(
    name: String,
    license: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = license,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.customColors.subtleText,
        )
    }
}

/**
 * ライセンスの条文テキストを表示するComposable。
 * 等幅フォント(IbmPlexMono)と小さめの文字サイズを適用して、長文のライセンス文書を整形された状態で表示するために用いる。
 */
@Composable
private fun LicenseBodyText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = IbmPlexMono),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * rawリソースからテキスト全文を読み込み、remember で保持するComposable関数。
 * 本文をコードへ埋め込まずリソースファイルを唯一の出どころとすることで、ライセンス文書の単一情報源を保つために用いる。
 */
@Composable
private fun rememberRawResourceText(resId: Int): String {
    val resources = LocalResources.current
    return remember(resources, resId) {
        resources.openRawResource(resId)
            .bufferedReader()
            .use { it.readText() }
    }
}
