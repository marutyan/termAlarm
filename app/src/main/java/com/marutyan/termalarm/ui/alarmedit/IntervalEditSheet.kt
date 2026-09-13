package com.marutyan.termalarm.ui.alarmedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.calculateOccurrenceOffsets
import com.marutyan.termalarm.domain.occurrenceCount
import com.marutyan.termalarm.ui.common.formatClockMinutes
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily

/**
 * 鳴動時刻のオフセットリストから2行に収まるプレビュー文字列を生成する内部関数。
 * 鳴動回数が多い場合は途中を「…」で省略し、2行以内で視認できるようにフォーマットする。
 */
internal fun formatOccurrenceTimesPreview(times: List<String>): String {
    if (times.isEmpty()) return ""
    if (times.size <= 6) return times.joinToString(", ")
    val firstLine = times.take(5).joinToString(", ") + ","
    val remaining = times.drop(5)
    val secondLine = if (remaining.size <= 3) {
        remaining.joinToString(", ")
    } else {
        "${remaining.take(2).joinToString(", ")} \u2026 ${times.last()}"
    }
    return "$firstLine\n$secondLine"
}

/**
 * 間隔設定（等間隔・加速）を行うシートComposable。
 * よく使う分数のチップ選択や加速設定時の開始・終了間隔、鳴動時刻プレビューおよび決定操作を提供する。
 */
@Composable
fun IntervalEditSheet(
    startMinutes: Int,
    endMinutes: Int,
    initialIsVariable: Boolean,
    initialStartInterval: Int,
    initialEndInterval: Int,
    onConfirm: (Boolean, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isVariable by rememberSaveable { mutableStateOf(initialIsVariable) }
    var startInterval by rememberSaveable { mutableIntStateOf(initialStartInterval) }
    var endInterval by rememberSaveable { mutableIntStateOf(initialEndInterval) }
    // 加速設定時に「開始」を編集中か「終了」を編集中か（true: 開始, false: 終了）
    var editingStartSide by rememberSaveable { mutableStateOf(true) }
    var showCustomIntervalDialog by rememberSaveable { mutableStateOf(false) }

    // domain関数を用いてプレビューを計算する
    val previewSchedule = remember(startMinutes, endMinutes, isVariable, startInterval, endInterval) {
        AlarmSchedule(
            id = 0L,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            startIntervalMinutes = startInterval,
            endIntervalMinutes = if (isVariable) endInterval else startInterval,
            repeatDays = emptySet(),
            label = "",
            enabled = true,
        )
    }
    val count = remember(previewSchedule) { occurrenceCount(previewSchedule) }
    val timePattern = remember { com.marutyan.termalarm.ui.common.clockTimePattern(false) }
    val timesString = remember(previewSchedule, timePattern) {
        val offsets = calculateOccurrenceOffsets(previewSchedule)
        val formatted = offsets.map { offset -> formatClockMinutes((startMinutes + offset) % 1440, timePattern) }
        formatOccurrenceTimesPreview(formatted)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ドラッグ目印
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline),
            )
        }

        // 見出し（アイコン + 「間隔」）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TermIntervalBarsIcon(
                modifier = Modifier.size(21.dp),
                color = MaterialTheme.customColors.subtleText,
            )
            Text(
                text = stringResource(R.string.term_edit_interval_label),
                fontSize = 19.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 「等間隔」と「加速」の選択ボタン（高さ52dpの2つ）
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 等間隔ボタン
            val isConstantSelected = !isVariable
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isConstantSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    )
                    .then(
                        if (isConstantSelected) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = { isVariable = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.term_edit_interval_constant),
                    fontSize = 15.sp,
                    color = if (isConstantSelected) MaterialTheme.colorScheme.primary else MaterialTheme.customColors.subtleText,
                )
            }

            // 加速ボタン
            val isAccelerateSelected = isVariable
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isAccelerateSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    )
                    .then(
                        if (isAccelerateSelected) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = { isVariable = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.term_edit_interval_accelerate),
                    fontSize = 15.sp,
                    color = if (isAccelerateSelected) MaterialTheme.colorScheme.primary else MaterialTheme.customColors.subtleText,
                )
            }
        }

        // 分数設定カード
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isVariable) {
                    // 加速時の開始・終了の分数指定ボックス
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // 開始側
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.term_edit_interval_start),
                                fontSize = 11.sp,
                                fontFamily = ibmPlexMonoFontFamily(400),
                                letterSpacing = 0.14.sp,
                                color = MaterialTheme.customColors.subtleText,
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (editingStartSide) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (editingStartSide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = { editingStartSide = true },
                                    )
                                    .padding(start = 12.dp, top = 5.dp, end = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "$startInterval",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Light,
                                    fontFamily = ibmPlexMonoFontFamily(300),
                                    color = if (editingStartSide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.unit_minutes),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.customColors.subtleText,
                                )
                            }
                        }

                        // 矢印アイコン
                        Box(
                            modifier = Modifier.padding(bottom = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TermArrowRightIcon(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.customColors.subtleText,
                            )
                        }

                        // 終了側
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                text = stringResource(R.string.term_edit_interval_end),
                                fontSize = 11.sp,
                                fontFamily = ibmPlexMonoFontFamily(400),
                                letterSpacing = 0.14.sp,
                                color = MaterialTheme.customColors.subtleText,
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (!editingStartSide) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (!editingStartSide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = { editingStartSide = false },
                                    )
                                    .padding(start = 12.dp, top = 5.dp, end = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "$endInterval",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Light,
                                    fontFamily = ibmPlexMonoFontFamily(300),
                                    color = if (!editingStartSide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.unit_minutes),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.customColors.subtleText,
                                )
                            }
                        }
                    }
                }

                // プリセットチップ（1, 3, 5, 10, 15 と「他」、46dp角）
                val currentEditingValue = if (!isVariable || editingStartSide) startInterval else endInterval
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    INTERVAL_PRESETS_MINUTES.forEach { preset ->
                        val isSelected = currentEditingValue == preset
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        if (!isVariable) {
                                            startInterval = preset
                                            endInterval = preset
                                        } else if (editingStartSide) {
                                            startInterval = preset
                                        } else {
                                            endInterval = preset
                                        }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = preset.toString(),
                                fontSize = 15.sp,
                                fontFamily = ibmPlexMonoFontFamily(400),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 「他」チップ
                    val isCustom = currentEditingValue !in INTERVAL_PRESETS_MINUTES
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isCustom) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = { showCustomIntervalDialog = true },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.term_edit_interval_other),
                            fontSize = 15.sp,
                            fontFamily = ibmPlexMonoFontFamily(400),
                            color = if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.customColors.subtleText,
                        )
                    }
                }
            }
        }

        // 実際に鳴る時刻と回数のプレビュー（2行まで、収まらなければ…で省略）
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = timesString,
                fontSize = 13.sp,
                fontFamily = ibmPlexMonoFontFamily(400),
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (isVariable) {
                    stringResource(R.string.term_edit_interval_accelerate_note, count)
                } else {
                    stringResource(R.string.term_edit_interval_constant_note, count)
                },
                fontSize = 11.5.sp,
                color = MaterialTheme.customColors.subtleText,
            )
        }

        // 「決定」ボタン
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = { onConfirm(isVariable, startInterval, endInterval) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.decide),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }

    // 任意の分数を入力するダイアログ
    if (showCustomIntervalDialog) {
        var customInput by remember {
            mutableStateOf(
                (if (!isVariable || editingStartSide) startInterval else endInterval).toString(),
            )
        }
        AlertDialog(
            onDismissRequest = { showCustomIntervalDialog = false },
            title = { Text(stringResource(R.string.term_edit_custom_interval_title)) },
            text = {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text(stringResource(R.string.unit_minutes)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = customInput.toIntOrNull()?.coerceIn(CUSTOM_INTERVAL_MIN, CUSTOM_INTERVAL_MAX) ?: 5
                        if (!isVariable) {
                            startInterval = parsed
                            endInterval = parsed
                        } else if (editingStartSide) {
                            startInterval = parsed
                        } else {
                            endInterval = parsed
                        }
                        showCustomIntervalDialog = false
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomIntervalDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
