package com.marutyan.termalarm.ui.skipgame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.GameQuestion
import com.marutyan.termalarm.ui.common.formatClockMinutes
import com.marutyan.termalarm.ui.theme.tabularNums

/**
 * 「今日はもう止める」ゲーム画面の入口。design/SkipGame.dc.htmlの枠組み(閉じるボタン・案内バナー・
 * 問題表示エリア・下部操作)を6種類共通で使い、問題の内容(GameQuestion)に応じて中身だけを出し分ける。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipGameScreen(viewModel: SkipGameViewModel, onClose: () -> Unit) {
    val uiState = viewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    val wrongAnswerMessage = stringResource(R.string.skip_game_wrong_answer)

    LaunchedEffect(uiState.isSuccess) { if (uiState.isSuccess) onClose() }
    LaunchedEffect(uiState.justFailed) {
        if (uiState.justFailed) {
            snackbarHostState.showSnackbar(wrongAnswerMessage)
            viewModel.consumeFailureNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ringing_skip_today)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            SkipGameInfoBanner(
                startMinutes = uiState.startMinutes,
                endMinutes = uiState.endMinutes,
                totalOccurrences = uiState.totalOccurrences,
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val question = uiState.question
                if (uiState.isLoading || question == null) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    GameContent(question = question, onSubmit = viewModel::submitAnswer, onCancel = onClose)
                }
            }
        }
    }
}

// 正解すると何を止めるかを伝える案内バナー(design/SkipGame.dc.htmlの水色バナー)
@Composable
private fun SkipGameInfoBanner(startMinutes: Int, endMinutes: Int, totalOccurrences: Int) {
    val range = if (startMinutes == endMinutes) {
        formatClockMinutes(startMinutes)
    } else {
        "${formatClockMinutes(startMinutes)}–${formatClockMinutes(endMinutes)}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_clock), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            stringResource(R.string.skip_game_banner, range, totalOccurrences),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// 問題の種類に応じて対応する画面を出し分ける
@Composable
private fun GameContent(question: GameQuestion, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    when (question) {
        is GameQuestion.Arithmetic -> ArithmeticGame(question, onSubmit, onCancel)
        is GameQuestion.CountShapes -> CountShapesGame(question, onSubmit, onCancel)
        is GameQuestion.Transcribe -> TranscribeGame(question, onSubmit, onCancel)
        is GameQuestion.SequentialTap -> SequentialTapGame(question, onSubmit, onCancel)
        is GameQuestion.ShakeDevice -> ShakeDeviceGame(question, onSubmit, onCancel)
        is GameQuestion.ColorWord -> ColorWordGame(question, onSubmit, onCancel)
    }
}

// 「決定」「やめる」の共通下部操作。数字入力・文字入力の3種類のゲームで使う
@Composable
private fun GameBottomActions(onConfirm: () -> Unit, onCancel: () -> Unit, confirmEnabled: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(stringResource(R.string.decide), style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    }
}

// 計算問題:2桁の足し算・引き算(design/SkipGame.dc.htmlそのもの)
@Composable
private fun ArithmeticGame(question: GameQuestion.Arithmetic, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var answer by rememberSaveable(question) { mutableStateOf("") }
    val operatorSymbol = if (question.isAddition) "+" else "–"
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        GameKindChip(stringResource(R.string.game_kind_arithmetic))
        Text(
            "${question.left} $operatorSymbol ${question.right}",
            style = MaterialTheme.typography.displayMedium.tabularNums(),
        )
        AnswerDisplay(answer)
        NumericKeypad(
            onDigit = { digit -> answer += digit },
            onBackspace = { answer = answer.dropLast(1) },
            onConfirmKey = { if (answer.isNotEmpty()) onSubmit(answer) },
        )
        GameBottomActions(onConfirm = { onSubmit(answer) }, onCancel = onCancel, confirmEnabled = answer.isNotEmpty())
    }
}

// 図形を数える問題。SPEC「図形を数えるの0個」の通り0を入力できるようにする
@Composable
private fun CountShapesGame(question: GameQuestion.CountShapes, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var answer by rememberSaveable(question) { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        GameKindChip(stringResource(R.string.game_kind_count_shapes))
        Text(stringResource(R.string.count_shapes_prompt, shapeKindLabel(question.target)), style = MaterialTheme.typography.titleMedium)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.height(160.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(question.shapes) { shape -> ShapeGlyph(shape) }
        }
        AnswerDisplay(answer)
        NumericKeypad(
            onDigit = { digit -> answer += digit },
            onBackspace = { answer = answer.dropLast(1) },
            onConfirmKey = { if (answer.isNotEmpty()) onSubmit(answer) },
        )
        GameBottomActions(onConfirm = { onSubmit(answer) }, onCancel = onCancel, confirmEnabled = answer.isNotEmpty())
    }
}

private fun shapeKindLabel(kind: com.marutyan.termalarm.domain.ShapeKind): String = when (kind) {
    com.marutyan.termalarm.domain.ShapeKind.CIRCLE -> "●"
    com.marutyan.termalarm.domain.ShapeKind.SQUARE -> "■"
    com.marutyan.termalarm.domain.ShapeKind.TRIANGLE -> "▲"
    com.marutyan.termalarm.domain.ShapeKind.STAR -> "★"
}

@Composable
private fun ShapeGlyph(kind: com.marutyan.termalarm.domain.ShapeKind) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(shapeKindLabel(kind), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    }
}

// 書き写し問題:表示された8文字を入力させる
@Composable
private fun TranscribeGame(question: GameQuestion.Transcribe, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var answer by rememberSaveable(question) { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        GameKindChip(stringResource(R.string.game_kind_transcribe))
        Text(question.text, style = MaterialTheme.typography.displaySmall.tabularNums())
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        GameBottomActions(onConfirm = { onSubmit(answer) }, onCancel = onCancel, confirmEnabled = answer.isNotEmpty())
    }
}

// 回答欄。数字専用ゲーム(計算・図形数え)で入力中の値を大きく表示する
@Composable
private fun AnswerDisplay(answer: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(answer, style = MaterialTheme.typography.headlineMedium.tabularNums())
    }
}

// 種類名を示す小さなチップ(design/SkipGame.dc.htmlの「計算」ラベル)
@Composable
private fun GameKindChip(label: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// 0-9とバックスペース・確定を3列グリッドで並べるテンキー(design/SkipGame.dc.htmlと同じ配置)
@Composable
private fun NumericKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit, onConfirmKey: () -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "←", "0", "✓")
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(280.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(keys) { key ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        when (key) {
                            "←" -> onBackspace()
                            "✓" -> onConfirmKey()
                            else -> onDigit(key)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(key, style = MaterialTheme.typography.headlineSmall.tabularNums())
            }
        }
    }
}
