package com.marutyan.termalarm.ui.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.ui.theme.fittingClock
import com.marutyan.termalarm.ui.theme.heroClock
import com.marutyan.termalarm.ui.common.TermAlarmOverflowMenu
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.ClockDisplayMode
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import com.marutyan.termalarm.ui.theme.SlideAnimatedDigits
import com.marutyan.termalarm.ui.theme.clockModeAnimationSpec
import com.marutyan.termalarm.ui.theme.tabularNums
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// 日付表示のフォーマット。「9月3日（木）」の形にする(RingingActivity.ktの終了時刻表示と同じ書式)
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)

// デジタル表示の時:分部分のフォーマット。秒はSECOND_FORMATTERで別に小さく添える
private val HOUR_MINUTE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val SECOND_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("ss")

/**
 * 時計タブの画面。世界時計をやめ、端末の現在時刻をアナログ/デジタルで大きく表示する
 * 1つの時計に作り直した。表示モードの切り替えは画面下部のトグルで行い、設定として永続化する。
 */
@Composable
fun ClockScreen(
    viewModel: ClockViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    bottomBar: @Composable () -> Unit,
) {
    val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()
    // アナログの秒針・デジタルの秒表示のどちらも1秒ごとに動かす(要件「1秒ごとの更新」)
    val now = rememberCurrentSecond()

    // 設定「時刻に秒を表示」。画面が自分でDBを開くと、テストが差し替えた先と食い違って
    // 実行のたびに結果が変わるため、ViewModelから受け取る
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_clock), style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    TermAlarmOverflowMenu(
                        onOpenSettings = onOpenSettings,
                        onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                        onOpenAbout = onOpenAbout,
                    )
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        // 純正は時刻を画面の上の方へ置く(実測で上端が画面の16%の位置)。
        // 中央へ置くと、上に広い空きができて時刻が沈んで見える
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            MainClock(mode = displayMode, time = now, showSeconds = appSettings.showClockSeconds)
            Text(
                text = now.format(DATE_FORMATTER),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
            )
            DisplayModeToggle(mode = displayMode, onModeChange = viewModel::setDisplayMode)
        }
    }
}

// 端末の現在時刻を、設定どおりアナログ(Canvas描画)またはデジタル(等幅数字)で大きく表示する。
// showSecondsは設定「時刻に秒を表示」(デジタルは秒の文字、アナログは秒針の表示可否に反映する)
@Composable
private fun MainClock(mode: ClockDisplayMode, time: ZonedDateTime, showSeconds: Boolean) {
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            (scaleIn(initialScale = 0.8f, animationSpec = clockModeAnimationSpec()) + fadeIn(animationSpec = clockModeAnimationSpec()))
                .togetherWith(scaleOut(targetScale = 0.8f, animationSpec = clockModeAnimationSpec()) + fadeOut(animationSpec = clockModeAnimationSpec()))
        },
        label = "ClockModeTransition",
    ) { currentMode ->
        when (currentMode) {
            ClockDisplayMode.ANALOG -> AnalogClockFace(time = time, showSeconds = showSeconds, modifier = Modifier.size(320.dp))
            ClockDisplayMode.DIGITAL -> DigitalClockFace(time = time, showSeconds = showSeconds)
        }
    }
}

/**
 * デジタル時計。
 * 純正は「6:31:20」のように秒まで同じ大きさで1行に並べる。秒だけ小さくすると別物に見える。
 * 文字の大きさは画面の幅から決める。桁数は時刻帯や12/24時制で変わるため、固定値だと
 * ある時間帯だけはみ出したり、逆に小さすぎたりする。
 */
@Composable
private fun DigitalClockFace(time: ZonedDateTime, showSeconds: Boolean) {
    val text = if (showSeconds) {
        time.format(HOUR_MINUTE_FORMATTER) + ":" + time.format(SECOND_FORMATTER)
    } else {
        time.format(HOUR_MINUTE_FORMATTER)
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        SlideAnimatedDigits(
            text = text,
            style = MaterialTheme.typography.displayLarge.fittingClock(maxWidth, text.length),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// 「アナログ」「デジタル」が折り返さずに収まる幅
private val SEGMENT_WIDTH = 132.dp

// 表示モード(アナログ/デジタル)を選ぶ2択のセグメントボタン
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayModeToggle(mode: ClockDisplayMode, onModeChange: (ClockDisplayMode) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = mode == ClockDisplayMode.ANALOG,
            onClick = { onModeChange(ClockDisplayMode.ANALOG) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.width(SEGMENT_WIDTH),
        ) {
            // 幅が足りないと「アナ/ログ」のように途中で折り返してしまう
            Text(stringResource(R.string.clock_display_mode_analog), maxLines = 1, softWrap = false)
        }
        SegmentedButton(
            selected = mode == ClockDisplayMode.DIGITAL,
            onClick = { onModeChange(ClockDisplayMode.DIGITAL) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.width(SEGMENT_WIDTH),
        ) {
            Text(stringResource(R.string.clock_display_mode_digital), maxLines = 1, softWrap = false)
        }
    }
}
