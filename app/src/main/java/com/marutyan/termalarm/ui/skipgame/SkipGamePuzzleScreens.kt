package com.marutyan.termalarm.ui.skipgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.GameQuestion
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 順にタップゲーム（GameQuestion.SequentialTap）の画面Composable。
 * design/RingingChallenge.dc.htmlに基づき、1〜12のタイルを昇順にタップさせるために用いる。
 */
@Composable
internal fun SequentialTapGameContent(
    question: GameQuestion.SequentialTap,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nextExpected by rememberSaveable(question) { mutableIntStateOf(1) }
    var tappedOrder by rememberSaveable(question) { mutableStateOf(listOf<Int>()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        // 上部ガイド行: 「1から12を昇順に押す」と進捗「3 / 12」
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.sequential_tap_prompt),
                style = TextStyle(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Text(
                text = "${tappedOrder.size} / 12",
                style = TextStyle(
                    fontFamily = ibmPlexMonoFontFamily(400),
                    fontSize = 13.sp,
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        // 3列×4行のタイルグリッド (design/RingingChallenge.dc.html準拠: 高さ78dp、枠線1dp、角丸3dp)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (row in 0..3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (col in 0..2) {
                        val index = row * 3 + col
                        val number = question.shuffledNumbers.getOrElse(index) { 0 }
                        val isPressed = number in tappedOrder

                        val bgColor = if (isPressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
                        val borderColor = if (isPressed) MaterialTheme.customColors.scalePast else MaterialTheme.colorScheme.outline
                        val textColor = if (isPressed) MaterialTheme.customColors.scalePast else MaterialTheme.colorScheme.onSurface

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 78.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(3.dp))
                            .clickable(
                                enabled = !isPressed,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = {
                                    if (number == nextExpected) {
                                        val updated = tappedOrder + number
                                        tappedOrder = updated
                                        if (updated.size == question.shuffledNumbers.size) {
                                            onSubmit(updated.joinToString(","))
                                        } else {
                                            nextExpected += 1
                                        }
                                    } else {
                                        // 順序を誤ったのでやり直し通知
                                        tappedOrder = emptyList()
                                        nextExpected = 1
                                        onSubmit("wrong")
                                    }
                                },
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = number.toString(),
                                style = TextStyle(
                                    fontFamily = ibmPlexMonoFontFamily(300),
                                    fontSize = 28.sp,
                                    fontFeatureSettings = "tnum",
                                    color = textColor,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 光った順を再現ゲーム（GameQuestion.SequenceRecall）の画面Composable。
 * 出題時に1〜9のセルを順次点灯させ、消灯後に入力順を受け付けてカンマ区切りで回答を提出するために用いる。
 */
@Composable
internal fun SequenceRecallGameContent(
    question: GameQuestion.SequenceRecall,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeLitNumber by remember { mutableStateOf<Int?>(null) }
    var isShowingSequence by remember { mutableStateOf(true) }
    var userTappedOrder by rememberSaveable(question) { mutableStateOf(listOf<Int>()) }

    LaunchedEffect(question) {
        userTappedOrder = emptyList()
        isShowingSequence = true
        delay(600L)
        for (num in question.sequence) {
            activeLitNumber = num
            delay(500L)
            activeLitNumber = null
            delay(250L)
        }
        isShowingSequence = false
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        // 上部ガイド行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isShowingSequence) {
                    stringResource(R.string.sequence_recall_prompt_wait)
                } else {
                    stringResource(R.string.sequence_recall_prompt_input)
                },
                style = TextStyle(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Text(
                text = "${userTappedOrder.size} / ${question.sequence.size}",
                style = TextStyle(
                    fontFamily = ibmPlexMonoFontFamily(400),
                    fontSize = 13.sp,
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        // 3×3の1〜9セルグリッド
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (col in 0..2) {
                        val num = row * 3 + col + 1
                        val isLit = (activeLitNumber == num)

                        val bgColor = if (isLit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
                        val borderColor = if (isLit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        val textColor = if (isLit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 68.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                                .clickable(
                                    enabled = !isShowingSequence,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        val updated = userTappedOrder + num
                                        userTappedOrder = updated
                                        if (updated.size >= question.sequence.size) {
                                            onSubmit(updated.joinToString(","))
                                        }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = num.toString(),
                                style = TextStyle(
                                    fontFamily = ibmPlexMonoFontFamily(300),
                                    fontSize = 26.sp,
                                    fontFeatureSettings = "tnum",
                                    color = textColor,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 神経衰弱ゲーム（GameQuestion.MemoryPairs）の画面Composable。
 * 3×4の伏せ札をめくり、絵柄の一致を判定して全6ペアそろえた時点で回答"6"を提出するために用いる。
 */
@Composable
internal fun MemoryPairsGameContent(
    question: GameQuestion.MemoryPairs,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var matchedIndices by rememberSaveable(question) { mutableStateOf(setOf<Int>()) }
    var selectedIndices by rememberSaveable(question) { mutableStateOf(listOf<Int>()) }
    var isWaitingMismatch by remember { mutableStateOf(false) }

    val glyphs = remember { listOf("\u2605", "\u25CF", "\u25B2", "\u25A0", "\u25C6", "\u2660") } // ★, ●, ▲, ■, ◆, ♠

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 上部ガイド行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.memory_pairs_prompt),
                style = TextStyle(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Text(
                text = stringResource(R.string.memory_pairs_progress, matchedIndices.size / 2, 6),
                style = TextStyle(
                    fontFamily = ibmPlexMonoFontFamily(400),
                    fontSize = 13.sp,
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        // 3列×4行のカードグリッド
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (row in 0..3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (col in 0..2) {
                        val index = row * 3 + col
                        val cardId = question.cards.getOrElse(index) { 0 }
                        val isMatched = index in matchedIndices
                        val isSelected = index in selectedIndices

                        val bgColor = when {
                            isMatched -> MaterialTheme.colorScheme.primaryContainer
                            isSelected -> MaterialTheme.colorScheme.surfaceContainer
                            else -> MaterialTheme.colorScheme.background
                        }
                        val borderColor = when {
                            isMatched -> MaterialTheme.customColors.scalePast
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        }
                        val textColor = when {
                            isMatched -> MaterialTheme.customColors.scalePast
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.customColors.subtleText
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 60.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                                .clickable(
                                    enabled = !isMatched && !isSelected && !isWaitingMismatch,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        val newSelected = selectedIndices + index
                                        selectedIndices = newSelected

                                        if (newSelected.size == 2) {
                                            val firstIdx = newSelected[0]
                                            val secondIdx = newSelected[1]
                                            if (question.cards[firstIdx] == question.cards[secondIdx]) {
                                                // 一致！
                                                val newMatched = matchedIndices + firstIdx + secondIdx
                                                matchedIndices = newMatched
                                                selectedIndices = emptyList()
                                                if (newMatched.size == question.cards.size) {
                                                    onSubmit("6")
                                                }
                                            } else {
                                                // 不一致のため少し待ってから伏せる
                                                isWaitingMismatch = true
                                                coroutineScope.launch {
                                                    delay(800L)
                                                    selectedIndices = emptyList()
                                                    isWaitingMismatch = false
                                                }
                                            }
                                        }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isMatched || isSelected) glyphs.getOrElse(cardId) { "?" } else "?",
                                style = TextStyle(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 色と文字ゲーム（GameQuestion.ColorWord）の画面Composable。
 * 意味と文字色が異なる単語を提示し、文字の色を選択肢から正しく選ばせるために用いる。
 */
@Composable
internal fun ColorWordGameContent(
    question: GameQuestion.ColorWord,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GameKindBadge(title = stringResource(R.string.game_kind_color_word))

        Text(
            text = stringResource(R.string.color_word_prompt),
            style = TextStyle(
                fontSize = 13.sp,
                color = MaterialTheme.customColors.subtleText,
            ),
        )

        // 文字の意味と表示色が異なる語を大きく表示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = question.word,
                style = TextStyle(
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    // 色は出題データが持っている。画面側で名前から引き直さない
                    color = Color(question.displayColor.rgb),
                ),
            )
        }

        // 選択肢（2列グリッドで並べる）
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val chunkedChoices = question.choices.chunked(2)
            for (pair in chunkedChoices) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (choice in pair) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = { onSubmit(choice) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = choice,
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

