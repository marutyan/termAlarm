package com.marutyan.termalarm.ui.skipgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.GameQuestion
import com.marutyan.termalarm.domain.ShapeKind
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily

/**
 * ゲームの種類名を示すバッジラベル。
 * 画面中央の出題領域の上部に配置し、ユーザーにゲームの種別を明示するために用いる。
 */
@Composable
internal fun GameKindBadge(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = ibmPlexMonoFontFamily(400),
                fontSize = 11.sp,
                letterSpacing = 0.08.em,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

/**
 * テンキー入力中の数値を表示する枠線コンポーネント。
 * 計算や図形数えゲームで入力された回答を大きく明瞭に表示するために用いる。
 */
@Composable
internal fun AnswerDisplay(
    answer: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = answer.ifEmpty { "_" },
            style = TextStyle(
                fontFamily = ibmPlexMonoFontFamily(300),
                fontSize = 24.sp,
                fontFeatureSettings = "tnum",
                color = if (answer.isEmpty()) MaterialTheme.customColors.subtleText else MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

/**
 * 0〜9、バックスペース、確定キーを配置したテンキーComposable。
 * 押しやすい44dp以上の領域を確保し、数値回答の入力を提供するために用いる。
 */
@Composable
internal fun NumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirmKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys = remember { listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "\u232B", "0", "\u2713") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (rowIndex in 0..3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (colIndex in 0..2) {
                    val key = keys[rowIndex * 3 + colIndex]
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
                                onClick = {
                                    when (key) {
                                        "\u232B" -> onBackspace()
                                        "\u2713" -> onConfirmKey()
                                        else -> onDigit(key)
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key,
                            style = TextStyle(
                                fontFamily = ibmPlexMonoFontFamily(300),
                                fontSize = 20.sp,
                                fontFeatureSettings = "tnum",
                                color = if (key == "\u2713") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 計算ゲーム（GameQuestion.Arithmetic）の出題と入力を行うComposable。
 * 2桁の足し算・引き算を大きく提示し、テンキーで答えを入力させるために用いる。
 */
@Composable
internal fun ArithmeticGameContent(
    question: GameQuestion.Arithmetic,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var answer by rememberSaveable(question) { mutableStateOf("") }
    val operatorSymbol = if (question.isAddition) "+" else "\u2013"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameKindBadge(title = stringResource(R.string.game_kind_arithmetic))

        // 式の表示（IBM Plex Mono 36sp）
        Text(
            text = "${question.left} $operatorSymbol ${question.right}",
            style = TextStyle(
                fontFamily = ibmPlexMonoFontFamily(300),
                fontSize = 36.sp,
                fontFeatureSettings = "tnum",
                letterSpacing = (-0.02).em,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )

        AnswerDisplay(answer = answer)

        NumericKeypad(
            onDigit = { digit -> if (answer.length < 6) answer += digit },
            onBackspace = { if (answer.isNotEmpty()) answer = answer.dropLast(1) },
            onConfirmKey = { if (answer.isNotEmpty()) onSubmit(answer) },
        )
    }
}

/**
 * 図形を数えるゲーム（GameQuestion.CountShapes）の出題と入力を行うComposable。
 * グリッドに並んだ混在図形から指定された種類の個数をテンキーで入力させるために用いる。
 */
@Composable
internal fun CountShapesGameContent(
    question: GameQuestion.CountShapes,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var answer by rememberSaveable(question) { mutableStateOf("") }
    val targetGlyph = shapeGlyphLabel(question.target)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GameKindBadge(title = stringResource(R.string.game_kind_count_shapes))

        Text(
            text = stringResource(R.string.count_shapes_prompt, targetGlyph),
            style = TextStyle(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )

        // 12個の図形を4列グリッドで配置
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (col in 0..3) {
                        val index = row * 4 + col
                        val shape = question.shapes.getOrElse(index) { ShapeKind.CIRCLE }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = shapeGlyphLabel(shape),
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    color = if (shape == question.target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
            }
        }

        AnswerDisplay(answer = answer)

        NumericKeypad(
            onDigit = { digit -> if (answer.length < 4) answer += digit },
            onBackspace = { if (answer.isNotEmpty()) answer = answer.dropLast(1) },
            onConfirmKey = { if (answer.isNotEmpty()) onSubmit(answer) },
        )
    }
}

/**
 * 書き写しゲーム（GameQuestion.Transcribe）の出題と入力を行うComposable。
 * 画面に提示された英数字8文字をテキスト入力欄へ正確に入力させるために用いる。
 */
@Composable
internal fun TranscribeGameContent(
    question: GameQuestion.Transcribe,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var answer by rememberSaveable(question) { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameKindBadge(title = stringResource(R.string.game_kind_transcribe))

        Text(
            text = stringResource(R.string.transcribe_prompt),
            style = TextStyle(
                fontSize = 13.sp,
                color = MaterialTheme.customColors.subtleText,
            ),
        )

        // 提示文字列（大きく表示）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(vertical = 18.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = question.text,
                style = TextStyle(
                    fontFamily = ibmPlexMonoFontFamily(400),
                    fontSize = 28.sp,
                    letterSpacing = 0.12.em,
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it.uppercase() },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = ibmPlexMonoFontFamily(400),
                fontSize = 20.sp,
                letterSpacing = 0.08.em,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (answer.isNotEmpty()) onSubmit(answer) }),
            modifier = Modifier.fillMaxWidth(),
        )

        // 決定ボタン
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (answer.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                .clickable(
                    enabled = answer.isNotEmpty(),
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = { onSubmit(answer) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.decide),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (answer.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.customColors.subtleText,
                ),
            )
        }
    }
}

/**
 * 鏡文字ゲーム（GameQuestion.MirrorText）の出題と入力を行うComposable。
 * 描画時に水平方向（scaleX = -1f）へ反転させた英数字を読み取って入力させるために用いる。
 */
@Composable
internal fun MirrorTextGameContent(
    question: GameQuestion.MirrorText,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var answer by rememberSaveable(question) { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameKindBadge(title = stringResource(R.string.game_kind_mirror_text))

        Text(
            text = stringResource(R.string.mirror_text_prompt),
            style = TextStyle(
                fontSize = 13.sp,
                color = MaterialTheme.customColors.subtleText,
            ),
        )

        // 水平方向に反転描画（scaleX = -1f）した文字列
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(vertical = 18.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = question.text,
                modifier = Modifier.graphicsLayer(scaleX = -1f),
                style = TextStyle(
                    fontFamily = ibmPlexMonoFontFamily(400),
                    fontSize = 28.sp,
                    letterSpacing = 0.12.em,
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it.uppercase() },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = ibmPlexMonoFontFamily(400),
                fontSize = 20.sp,
                letterSpacing = 0.08.em,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (answer.isNotEmpty()) onSubmit(answer) }),
            modifier = Modifier.fillMaxWidth(),
        )

        // 決定ボタン
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (answer.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                .clickable(
                    enabled = answer.isNotEmpty(),
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = { onSubmit(answer) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.decide),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (answer.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.customColors.subtleText,
                ),
            )
        }
    }
}

/**
 * ShapeKindに対応するUnicode記号文字列を返すヘルパー関数。
 * 画面上で図形を簡潔かつ確実に視覚化するために用いる。
 */
private fun shapeGlyphLabel(kind: ShapeKind): String = when (kind) {
    ShapeKind.CIRCLE -> "\u25CF" // ●
    ShapeKind.SQUARE -> "\u25A0" // ■
    ShapeKind.TRIANGLE -> "\u25B2" // ▲
    ShapeKind.STAR -> "\u2605" // ★
}
