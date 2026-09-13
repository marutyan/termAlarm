package com.marutyan.termalarm.ui.skipgame

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.alarm.OccurrenceScaleBar
import com.marutyan.termalarm.alarm.VolumeIndicator
import com.marutyan.termalarm.domain.GameQuestion
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily
import kotlin.math.roundToInt

/**
 * 解除チャレンジ（ミニゲーム）画面のメインComposable。
 * design/RingingChallenge.dc.htmlの設計に基づき、上部に鳴動情報と目盛、中央に各ゲーム画面、下部に案内バーを表示する。
 * 正解時に次の問題または完了通知、不正解時に再試行を行う。
 */
@Composable
fun SkipGameScreen(
    viewModel: SkipGameViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val wrongAnswerMessage = stringResource(R.string.skip_game_wrong_answer)

    // 全問正解時の画面終了処理
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onClose()
        }
    }

    // 不正解時のフィードバック案内表示
    LaunchedEffect(uiState.justFailed) {
        if (uiState.justFailed) {
            snackbarHostState.showSnackbar(wrongAnswerMessage)
            viewModel.consumeFailureNotice()
        }
    }

    // 音量レベル (0..5) を取得
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val currentVolume = remember(audioManager) { audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 3 }
    val maxVolume = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 5 }
    val volumeLevel = remember(currentVolume, maxVolume) {
        if (maxVolume > 0) ((currentVolume.toFloat() / maxVolume) * 5).roundToInt().coerceIn(1, 5) else 3
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = statusBarTop + 24.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 24.dp,
                ),
        ) {
            // 上部情報表示（左: 回数ラベル、時刻、範囲・間隔 / 右: 音量目盛）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    // 「4回目 / 25回」またはターム終了見出し
                    val topLabelText = if (uiState.isTermEnd) {
                        stringResource(R.string.ringing_skip_today)
                    } else {
                        stringResource(
                            R.string.ringing_occurrence_label,
                            uiState.currentOccurrence,
                            uiState.totalOccurrences,
                        )
                    }
                    Text(
                        text = topLabelText,
                        style = TextStyle(
                            fontFamily = ibmPlexMonoFontFamily(400),
                            fontSize = 11.sp,
                            letterSpacing = 0.16.em,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )

                    // 鳴っている時刻 (86sp, W200, 等幅数字)
                    Text(
                        text = uiState.occurrenceTimeString.ifEmpty { "00:00" },
                        style = TextStyle(
                            fontFamily = ibmPlexMonoFontFamily(200),
                            fontWeight = FontWeight.W200,
                            fontSize = 86.sp,
                            lineHeight = 76.sp,
                            letterSpacing = (-0.055).em,
                            fontFeatureSettings = "tnum",
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )

                    // 範囲と間隔 (13sp, 薄い文字)
                    if (uiState.rangeAndIntervalText.isNotEmpty()) {
                        Text(
                            text = uiState.rangeAndIntervalText,
                            style = TextStyle(
                                fontFamily = ibmPlexMonoFontFamily(400),
                                fontSize = 13.sp,
                                fontFeatureSettings = "tnum",
                                color = MaterialTheme.customColors.subtleText,
                            ),
                        )
                    }
                }

                // 右上に音量目盛（鳴動時のみ）
                if (!uiState.isTermEnd) {
                    VolumeIndicator(volumeLevel = volumeLevel)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 鳴動スロット目盛バー
            OccurrenceScaleBar(
                totalCount = uiState.totalOccurrences,
                currentIndex = uiState.currentOccurrenceIndex,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // スロット下の説明文
            val hintText = if (uiState.isTermEnd) {
                stringResource(R.string.game_challenge_end_term_hint)
            } else {
                stringResource(R.string.game_challenge_stop_alarm_hint)
            }
            Text(
                text = hintText,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.customColors.subtleText,
                ),
            )

            // 出題数が複数のときは「3問中1問目」のように進み具合を出す
            if (uiState.totalQuestions > 1) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = stringResource(
                            R.string.game_question_progress,
                            uiState.totalQuestions,
                            uiState.currentQuestionIndex + 1,
                        ),
                        style = TextStyle(
                            fontFamily = ibmPlexMonoFontFamily(500),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 中央ゲームコンテンツエリア
            val currentQuestion = uiState.question
            if (uiState.isLoading || currentQuestion == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                GameDispatcher(
                    question = currentQuestion,
                    onSubmit = viewModel::submitAnswer,
                )
            }

            Spacer(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 32.dp),
            )

            // 下部案内バー (design/RingingChallenge.dc.html準拠)
            ChallengeBottomBar(
                hintText = hintText,
                showCancel = uiState.isTermEnd,
                onCancel = onClose,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp),
        )
    }
}

/**
 * 出題された問題種別（GameQuestion）に応じて対応するゲーム画面を呼び分けるComposable。
 * domainで定義された全10種類のミニゲームを過不足なく画面へ接続するために用いる。
 */
@Composable
private fun GameDispatcher(
    question: GameQuestion,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when (question) {
            is GameQuestion.Arithmetic -> ArithmeticGameContent(question, onSubmit)
            is GameQuestion.SequentialTap -> SequentialTapGameContent(question, onSubmit)
            is GameQuestion.Transcribe -> TranscribeGameContent(question, onSubmit)
            is GameQuestion.MirrorText -> MirrorTextGameContent(question, onSubmit)
            is GameQuestion.SequenceRecall -> SequenceRecallGameContent(question, onSubmit)
            is GameQuestion.MemoryPairs -> MemoryPairsGameContent(question, onSubmit)
            is GameQuestion.CountShapes -> CountShapesGameContent(question, onSubmit)
            is GameQuestion.ColorWord -> ColorWordGameContent(question, onSubmit)
            is GameQuestion.ShakeDevice -> ShakeDeviceGameContent(question, onSubmit)
            is GameQuestion.Walk -> WalkGameContent(question, onSubmit)
        }
    }
}

/**
 * 画面下部に配置するチェックアイコン付きの案内バーComposable。
 * design/RingingChallenge.dc.htmlの設計に基づき、解除条件を知らせ、キャンセル導線を提供する。
 */
@Composable
private fun ChallengeBottomBar(
    hintText: String,
    showCancel: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // チェックアイコン
            CheckMarkIcon(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
            )

            // 案内メッセージ（13.5sp）
            Text(
                text = hintText,
                style = TextStyle(
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.weight(1f),
            )

            // ターム終了時など中断可能な場合のキャンセルボタン
            if (showCancel) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = onCancel,
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.game_give_up),
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = MaterialTheme.customColors.subtleText,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * design/RingingChallenge.dc.htmlの下部バーに描画されているチェックマークアイコン。
 * 正解条件が満たされた際に解除されることを示すために用いる。
 */
@Composable
private fun CheckMarkIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val scale = size.width / 24f
        val strokeWidth = 1.8f * scale
        // M5 13l4 4L19 7
        val p1 = Offset(5f * scale, 13f * scale)
        val p2 = Offset(9f * scale, 17f * scale)
        val p3 = Offset(19f * scale, 7f * scale)

        drawLine(color = color, start = p1, end = p2, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = color, start = p2, end = p3, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}
