package com.marutyan.termalarm.ui.timer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.ui.common.TOP_BAR_TOP_INSET
import com.marutyan.termalarm.ui.common.TOP_BAR_CONTENT_GAP
import com.marutyan.termalarm.ui.common.TermAlarmTopBar
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.pressScaleEffect

/** 入力画面の中央コンテンツ最大幅(dp)。純正時計アプリの実測値380.7dpに基づく。 */
private val CONTENT_MAX_WIDTH = 380.7.dp

/** すぐ選べる長さのボタンの幅(dp)。純正時計アプリの実測値92.1dpに基づく。 */
private val QUICK_PRESET_BUTTON_WIDTH = 92.1.dp

/** すぐ選べる長さのボタンの高さ(dp)。純正時計アプリの実測値56.6dpに基づく。 */
private val QUICK_PRESET_BUTTON_HEIGHT = 56.6.dp

/** すぐ選べる長さのボタン間の間隔(dp)。純正時計アプリの実測値4.5dpに基づく。 */
private val QUICK_PRESET_SPACING = 4.5.dp

/** 開始ボタンの最小高さ(dp)。純正時計アプリの実測値89.9dpに基づく。 */
private val START_BUTTON_MIN_HEIGHT = 89.9.dp

/** テンキーの各行の最小高さ(dp)。純正時計アプリの行間隔の実測値67.9dpに基づく。 */
private val KEYPAD_ROW_MIN_HEIGHT = 67.9.dp

/**
 * タイマー新規追加画面。Android純正時計アプリのレイアウトに基づき、
 * 中央揃えの幅282.7dpの列内に大きな数字表示、プリセットボタン、幅広の開始ボタン、
 * 地のないテンキーを上から順に配置する。
 *
 * @param onStart 設定した時分秒でタイマーを開始するコールバック。
 * @param onClose 入力を中断して前画面へ戻るコールバック。nullの場合は戻る操作を提供しない。
 */
@Composable
fun TimerAddScreen(
    onStart: (hours: Int, minutes: Int, seconds: Int) -> Unit,
    onClose: (() -> Unit)?,
    onOpenSettings: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (onClose != null) BackHandler(onBack = onClose)

    // 入力された数字列（最大6桁）。電子レンジと同様に右から順に詰まる。
    var inputDigits by rememberSaveable { mutableStateOf("") }

    // 6桁にゼロパディングして時・分・秒を右から2桁ずつ解釈する。
    val padded = inputDigits.padStart(6, '0')
    val hours = padded.substring(0, 2).toInt()
    val minutes = padded.substring(2, 4).toInt()
    val seconds = padded.substring(4, 6).toInt()

    val isStartEnabled = inputDigits.isNotEmpty() && (hours > 0 || minutes > 0 || seconds > 0)

    Column(modifier = modifier.fillMaxSize()) {
        // 他の画面と同じ帯。ここだけ無いと、タイマーが0件のときアプリ名が消えてしまう
        TermAlarmTopBar(
            onOpenSettings = onOpenSettings,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            onOpenAbout = onOpenAbout,
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(TOP_BAR_CONTENT_GAP))

            // 1. 大きな数字の行
            TimerBigDigitsRow(
                padded = padded,
                inputLength = inputDigits.length,
                hours = hours,
                minutes = minutes,
                seconds = seconds,
            )

            // 数字と単位ラベルの間隔（純正時計アプリの実測値23.8dpに基づく）
            Spacer(modifier = Modifier.height(23.8.dp))

            // 単位ラベルの行（時間、分、秒、16sp、薄い色）
            TimerUnitLabelsRow()

            // 単位ラベルからすぐ選べる長さのボタンまでの間隔（チップ上端の実測値327.6dpに合わせる）
            Spacer(modifier = Modifier.height(51.6.dp))

            // 2. すぐ選べる長さのボタン（1分、5分、10分、15分）
            TimerQuickPresetsRow(
                onSelectMinutes = { presetMinutes ->
                    onStart(0, presetMinutes, 0)
                },
            )

            // クイックプリセットから開始ボタンまでの間隔（開始ボタン上端の実測値441.3dpに合わせる）
            Spacer(modifier = Modifier.height(57.1.dp))

            // 3. 開始のボタン（中身幅いっぱい、高さ89.9dp、角丸）
            TimerStartButton(
                isStartEnabled = isStartEnabled,
                onClick = { onStart(hours, minutes, seconds) },
            )

            // 開始ボタンからテンキーまでの間隔（キー1行目の中心の実測値635.5dpに合わせる）
            Spacer(modifier = Modifier.height(70.4.dp))

            // 4. 数字キー（地のない数字だけの並び、行間隔50dp）
            TimerKeypad(
                onDigit = { digit ->
                    if (inputDigits.length < 6) {
                        inputDigits = if (inputDigits == "0") digit.toString() else inputDigits + digit
                    }
                },
                onZero = {
                    if (inputDigits.isNotEmpty() && inputDigits != "0" && inputDigits.length < 6) {
                        inputDigits += '0'
                    }
                },
                onDoubleZero = {
                    repeat(2) {
                        if (inputDigits.isNotEmpty() && inputDigits != "0" && inputDigits.length < 6) {
                            inputDigits += '0'
                        }
                    }
                },
                onBackspace = {
                    if (inputDigits.isNotEmpty()) {
                        inputDigits = inputDigits.dropLast(1)
                    }
                },
                onClearAll = { inputDigits = "" },
            )
        }
        }
    }
}

/**
 * 大きな数字の行。3つの等しい幅の列に時・分・秒を中央揃えで配置する。
 * 入力された桁は通常色、未入力の桁は薄い色で描画する。
 */
@Composable
private fun TimerBigDigitsRow(
    padded: String,
    inputLength: Int,
    hours: Int,
    minutes: Int,
    seconds: Int,
) {
    val description = stringResource(R.string.timer_time_description, hours, minutes, seconds)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.2.dp)
            .clearAndSetSemantics {
                contentDescription = description
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 時間の2桁
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            DigitsPair(
                digit1 = padded[0],
                isDigit1Entered = 0 >= (6 - inputLength),
                digit2 = padded[1],
                isDigit2Entered = 1 >= (6 - inputLength),
            )
        }
        // 分の2桁
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            DigitsPair(
                digit1 = padded[2],
                isDigit1Entered = 2 >= (6 - inputLength),
                digit2 = padded[3],
                isDigit2Entered = 3 >= (6 - inputLength),
            )
        }
        // 秒の2桁
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            DigitsPair(
                digit1 = padded[4],
                isDigit1Entered = 4 >= (6 - inputLength),
                digit2 = padded[5],
                isDigit2Entered = 5 >= (6 - inputLength),
            )
        }
    }
}

/**
 * 各単位（時・分・秒）の2桁を描画するComposable。
 * 純正時計アプリの実測値92spを上限とし、狭い画面や文字拡大でも収まるよう40spまで自動縮小する。
 * 細め（W300）、等幅数字(tabular nums)を適用する。
 */
@Composable
private fun DigitsPair(
    digit1: Char,
    isDigit1Entered: Boolean,
    digit2: Char,
    isDigit2Entered: Boolean,
) {
    val activeColor = MaterialTheme.colorScheme.onSurface
    val inactiveColor = MaterialTheme.colorScheme.outline
    val annotatedDigits = remember(digit1, isDigit1Entered, digit2, isDigit2Entered, activeColor, inactiveColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = if (isDigit1Entered) activeColor else inactiveColor)) {
                append(digit1)
            }
            withStyle(SpanStyle(color = if (isDigit2Entered) activeColor else inactiveColor)) {
                append(digit2)
            }
        }
    }
    val textStyle = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.W300,
        fontSize = 92.sp,
        fontFeatureSettings = "tnum",
    )

    Text(
        text = annotatedDigits,
        style = textStyle,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 40.sp,
            maxFontSize = 92.sp,
            stepSize = 1.sp,
        ),
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * 数字の下に配置する単位ラベル行。「時間」「分」「秒」を3つの等しい幅の列の中央に置く。
 * 文字サイズは純正時計アプリの実測値16spとする。
 */
@Composable
private fun TimerUnitLabelsRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 21.6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.timer_unit_hours),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.timer_unit_minutes),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.timer_unit_seconds),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * すぐ選べる長さのボタン行（1分、5分、10分、15分）。
 * タップするとその長さのタイマーが即座に開始される。角丸は高さ56.6dpの半分(28.3dp)で完全に丸める。
 */
@Composable
private fun TimerQuickPresetsRow(
    onSelectMinutes: (Int) -> Unit,
) {
    val presets = listOf(
        1 to stringResource(R.string.timer_quick_1m),
        5 to stringResource(R.string.timer_quick_5m),
        10 to stringResource(R.string.timer_quick_10m),
        15 to stringResource(R.string.timer_quick_15m),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(QUICK_PRESET_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((minutes, label) in presets) {
            val interactionSource = remember { MutableInteractionSource() }
            Surface(
                onClick = { onSelectMinutes(minutes) },
                shape = RoundedCornerShape(28.3.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                interactionSource = interactionSource,
                modifier = Modifier
                    .width(QUICK_PRESET_BUTTON_WIDTH)
                    .height(QUICK_PRESET_BUTTON_HEIGHT)
                    .pressScaleEffect(interactionSource),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 開始ボタン。コンテンツ幅いっぱいの横長ボタンで、数字が未入力のときは無効状態となる。
 * 角丸は高さ89.9dpの半分(44.95dp)で完全に丸める。
 */
@Composable
private fun TimerStartButton(
    isStartEnabled: Boolean,
    onClick: () -> Unit,
) {
    val startInteractionSource = remember { MutableInteractionSource() }
    val containerColor = if (isStartEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isStartEnabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val startLabel = stringResource(R.string.timer_start)
    Surface(
        onClick = onClick,
        enabled = isStartEnabled,
        shape = RoundedCornerShape(44.95.dp),
        color = containerColor,
        contentColor = contentColor,
        interactionSource = startInteractionSource,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = START_BUTTON_MIN_HEIGHT)
            .pressScaleEffect(startInteractionSource)
            .semantics { contentDescription = startLabel },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = stringResource(R.string.timer_start),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/**
 * 地のない数字だけの並びによるテンキー。
 * 4行3列で各列は1/6、1/2、5/6の位置に中央揃えされ、押下時のみ円形リップルが表示される。
 */
@Composable
private fun TimerKeypad(
    onDigit: (Char) -> Unit,
    onZero: () -> Unit,
    onDoubleZero: () -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1行目: 1 2 3
        KeypadRow {
            KeypadNumberKey(text = stringResource(R.string.timer_key_1), onClick = { onDigit('1') })
            KeypadNumberKey(text = stringResource(R.string.timer_key_2), onClick = { onDigit('2') })
            KeypadNumberKey(text = stringResource(R.string.timer_key_3), onClick = { onDigit('3') })
        }
        // 2行目: 4 5 6
        KeypadRow {
            KeypadNumberKey(text = stringResource(R.string.timer_key_4), onClick = { onDigit('4') })
            KeypadNumberKey(text = stringResource(R.string.timer_key_5), onClick = { onDigit('5') })
            KeypadNumberKey(text = stringResource(R.string.timer_key_6), onClick = { onDigit('6') })
        }
        // 3行目: 7 8 9
        KeypadRow {
            KeypadNumberKey(text = stringResource(R.string.timer_key_7), onClick = { onDigit('7') })
            KeypadNumberKey(text = stringResource(R.string.timer_key_8), onClick = { onDigit('8') })
            KeypadNumberKey(text = stringResource(R.string.timer_key_9), onClick = { onDigit('9') })
        }
        // 4行目: 00 0 ⌫
        KeypadRow {
            KeypadNumberKey(text = stringResource(R.string.timer_key_00), onClick = onDoubleZero)
            KeypadNumberKey(text = stringResource(R.string.timer_key_0), onClick = onZero)
            KeypadNumberKey(
                text = stringResource(R.string.timer_key_backspace),
                contentDescription = stringResource(R.string.timer_backspace),
                onClick = onBackspace,
                onLongClick = onClearAll,
            )
        }
    }
}

/**
 * テンキーの1行分のコンテナ。行の最小高さ67.9dpを確保する。
 */
@Composable
private fun KeypadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = KEYPAD_ROW_MIN_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * テンキーの1つのキー。
 * 背景を描画せず数字のみを配置し、タップ時のみ円形リップル効果を表示する。
 * 文字サイズは純正時計アプリの実測値34spとする。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.KeypadNumberKey(
    text: String,
    onClick: () -> Unit,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = KEYPAD_ROW_MIN_HEIGHT)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 43.dp),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 34.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}
