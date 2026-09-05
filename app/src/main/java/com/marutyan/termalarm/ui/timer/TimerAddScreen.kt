package com.marutyan.termalarm.ui.timer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.keypadInput
import com.marutyan.termalarm.ui.theme.pressScaleEffect
import com.marutyan.termalarm.ui.theme.tabularNums

// テンキーのキー直径。純正を実機で測ると82dpだった（uiautomatorで測定）。
// 3列と間隔2つで 82*3 + 3*2 = 252dp。画面幅の64%に収まり、左右に余白が残る。
private val KEY_SIZE = 82.dp

// キーどうしの間隔。純正は中心の間隔が85dpで、キーが82dpなので隙間は3dpしかない
private val KEY_SPACING = 3.dp

/**
 * タイマー新規追加画面。純正の時計アプリと同じく、3列×4行の円形テンキーで右から数字を詰めて
 * 時分秒を入力する(docs/OFFICIAL_UI.md「タイマー / 追加画面はテンキー」)。
 * NavHostのルートではなくTimerScreen内のローカルな状態切り替えとして表示するため、
 * システムの戻る操作にはBackHandlerで対応する。
 */
@Composable
fun TimerAddScreen(
    onStart: (hours: Int, minutes: Int, seconds: Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    // 入力された数字列（最大6桁）。電子レンジと同様に右から順に詰まる。
    var inputDigits by rememberSaveable { mutableStateOf("") }

    // 6桁にゼロパディングして時・分・秒を右から2桁ずつ解釈する。
    val padded = inputDigits.padStart(6, '0')
    val hours = padded.substring(0, 2).toInt()
    val minutes = padded.substring(2, 4).toInt()
    val seconds = padded.substring(4, 6).toInt()

    val isStartEnabled = inputDigits.isNotEmpty() && (hours > 0 || minutes > 0 || seconds > 0)

    // 画面の枠(Scaffold)は呼び出し側が持つ。ここで自前の枠を作ると下部ナビが隠れる。
    // 純正もテンキーを出している間、下部ナビは見えたままになっている。
    // 縦が足りない端末や分割画面でも押せるよう、縦へはみ出したらスクロールできるようにする
    Column(
    modifier = modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 上部の入力中時間表示。純正は画面の上の方へ大きく出す
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            TimerDisplay(
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                padded = padded,
                inputLength = inputDigits.length,
            )
        }

        // 中央の3列×4行テンキー
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
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 下部の取り消し（×）と開始（▶）
        TimerActionRow(
            isStartEnabled = isStartEnabled,
            onClose = onClose,
            onStart = { onStart(hours, minutes, seconds) },
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 入力中の時間を「00h 00m 00s」の書式で大きく表示する。
 * 未入力の桁は薄く、入力済みの桁は通常色で表示し、単位は数字に小さく添える。
 * 画面読み上げでは1つの時間として読み上げられるよう、子ノードの個別数字を隠して集約する。
 */
@Composable
private fun TimerDisplay(
    hours: Int,
    minutes: Int,
    seconds: Int,
    padded: String,
    inputLength: Int,
) {
    val description = stringResource(R.string.timer_time_description, hours, minutes, seconds)
    Row(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TimeUnitDisplay(
            digit1 = padded[0],
            isDigit1Entered = 0 >= (6 - inputLength),
            digit2 = padded[1],
            isDigit2Entered = 1 >= (6 - inputLength),
            unit = stringResource(R.string.timer_unit_hours),
            isUnitEntered = inputLength >= 5,
        )
        TimeUnitDisplay(
            digit1 = padded[2],
            isDigit1Entered = 2 >= (6 - inputLength),
            digit2 = padded[3],
            isDigit2Entered = 3 >= (6 - inputLength),
            unit = stringResource(R.string.timer_unit_minutes),
            isUnitEntered = inputLength >= 3,
        )
        TimeUnitDisplay(
            digit1 = padded[4],
            isDigit1Entered = 4 >= (6 - inputLength),
            digit2 = padded[5],
            isDigit2Entered = 5 >= (6 - inputLength),
            unit = stringResource(R.string.timer_unit_seconds),
            isUnitEntered = inputLength >= 1,
        )
    }
}

/**
 * 時間・分・秒の各単位における数字2桁と単位文字を表示する。
 * 各桁ごとに未入力か入力済みかに応じて色を切り替える。
 */
@Composable
private fun TimeUnitDisplay(
    digit1: Char,
    isDigit1Entered: Boolean,
    digit2: Char,
    isDigit2Entered: Boolean,
    unit: String,
    isUnitEntered: Boolean,
) {
    val activeColor = MaterialTheme.colorScheme.onSurface
    val inactiveColor = MaterialTheme.colorScheme.outline

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = digit1.toString(),
            style = MaterialTheme.typography.displayLarge.keypadInput(),
            color = if (isDigit1Entered) activeColor else inactiveColor,
        )
        Text(
            text = digit2.toString(),
            style = MaterialTheme.typography.displayLarge.keypadInput(),
            color = if (isDigit2Entered) activeColor else inactiveColor,
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.titleLarge,
            color = if (isUnitEntered) activeColor else inactiveColor,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
    }
}

/**
 * 3列×4行の円形テンキー。
 * 純正時計アプリと同じ配置（1〜9、00、0、⌫）で数字を入力するために使う。
 */
@Composable
private fun TimerKeypad(
    onDigit: (Char) -> Unit,
    onZero: () -> Unit,
    onDoubleZero: () -> Unit,
    onBackspace: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KEY_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_SPACING)) {
            KeypadButton(text = stringResource(R.string.timer_key_1), onClick = { onDigit('1') })
            KeypadButton(text = stringResource(R.string.timer_key_2), onClick = { onDigit('2') })
            KeypadButton(text = stringResource(R.string.timer_key_3), onClick = { onDigit('3') })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_SPACING)) {
            KeypadButton(text = stringResource(R.string.timer_key_4), onClick = { onDigit('4') })
            KeypadButton(text = stringResource(R.string.timer_key_5), onClick = { onDigit('5') })
            KeypadButton(text = stringResource(R.string.timer_key_6), onClick = { onDigit('6') })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_SPACING)) {
            KeypadButton(text = stringResource(R.string.timer_key_7), onClick = { onDigit('7') })
            KeypadButton(text = stringResource(R.string.timer_key_8), onClick = { onDigit('8') })
            KeypadButton(text = stringResource(R.string.timer_key_9), onClick = { onDigit('9') })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_SPACING)) {
            KeypadButton(text = stringResource(R.string.timer_key_00), onClick = onDoubleZero)
            KeypadButton(text = stringResource(R.string.timer_key_0), onClick = onZero)
            KeypadButton(
                text = stringResource(R.string.timer_key_backspace),
                contentDescription = stringResource(R.string.timer_backspace),
                onClick = onBackspace,
            )
        }
    }
}

/**
 * テンキーの1つの円形キー。
 * 1辺72dp以上の円形領域を確保し、押し間違いを防ぐために使う。
 */
@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(KEY_SIZE)
            .pressScaleEffect(interactionSource),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    }
                ),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * テンキーの下に配置する操作ボタン行（取り消しと開始）。
 * テンキーの列に合わせ、×は左列の中心、▶は中央列の中心へ来るように配置する。
 */
@Composable
private fun TimerActionRow(
    isStartEnabled: Boolean,
    onClose: () -> Unit,
    onStart: () -> Unit,
) {
    // テンキー全体の幅（KEY_SIZE * 3 + KEY_SPACING * 2）に合わせて配置する
    val keypadWidth = KEY_SIZE * 3 + KEY_SPACING * 2
    val cancelInteractionSource = remember { MutableInteractionSource() }
    val startInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier.width(keypadWidth),
        horizontalArrangement = Arrangement.spacedBy(KEY_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 取り消し（×）: テンキーの左列中心に配置
        Surface(
            onClick = onClose,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            interactionSource = cancelInteractionSource,
            modifier = Modifier
                .size(KEY_SIZE)
                .pressScaleEffect(cancelInteractionSource),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.timer_cancel),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 開始（▶）: テンキーの中央列中心に配置
        FilledIconButton(
            onClick = onStart,
            enabled = isStartEnabled,
            shape = CircleShape,
            interactionSource = startInteractionSource,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .size(KEY_SIZE)
                .pressScaleEffect(startInteractionSource),
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.timer_start),
            )
        }

        // 右列の空きスペース
        Spacer(modifier = Modifier.size(KEY_SIZE))
    }
}
