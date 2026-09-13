package com.marutyan.termalarm.ui.alarmedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.ChallengeLevel
import com.marutyan.termalarm.domain.ChallengeTiming
import com.marutyan.termalarm.ui.common.formatClockMinutes
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// 月曜から日曜の順序リスト
private val DAYS_OF_WEEK_ORDER = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

/**
 * ターム編集画面の最上位Composable。design/TermEdit.dc.htmlを再現する。
 * 下から持ち上がるシート形式で構成し、時刻範囲、曜日、設定行、二度寝チェック、保存・削除ボタンを提供する。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(
    viewModel: AlarmEditViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState

    // 保存または削除が完了したら閉じる
    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) onClose()
    }

    // 端末の戻る操作で閉じる
    BackHandler(onBack = onClose)

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var showLabelDialog by rememberSaveable { mutableStateOf(false) }
    var showIntervalSheet by rememberSaveable { mutableStateOf(false) }
    var showChallengePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060C1C)),
    ) {
        // 背景の薄いホーム時計表示（上部エリア・タップで閉じるScrim）
        val now = remember { ZonedDateTime.now() }
        val currentTimeString = remember(now) { now.format(DateTimeFormatter.ofPattern("H:mm", Locale.getDefault())) }
        val currentSecondString = remember(now) { now.format(DateTimeFormatter.ofPattern("ss", Locale.getDefault())) }
        val currentDateString = remember(now) { now.format(DateTimeFormatter.ofPattern("MM.dd E", Locale.JAPANESE)) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        ) {
            // 背景の時計（opacity 0.2）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 74.dp, end = 20.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(bottom = 7.dp),
                ) {
                    Text(
                        text = currentTimeString,
                        style = TextStyle(
                            fontFamily = ibmPlexMonoFontFamily(200),
                            fontSize = 78.sp,
                            lineHeight = 70.sp,
                            letterSpacing = (-0.05).em,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    )
                    Text(
                        text = currentSecondString,
                        style = TextStyle(
                            fontFamily = ibmPlexMonoFontFamily(200),
                            fontSize = 33.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.customColors.subtleText.copy(alpha = 0.2f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    text = currentDateString,
                    style = TextStyle(
                        fontFamily = ibmPlexMonoFontFamily(400),
                        fontSize = 14.5.sp,
                    ),
                    color = MaterialTheme.customColors.subtleText.copy(alpha = 0.2f),
                    modifier = Modifier.padding(start = 9.dp),
                )
            }

            // 下部シート領域（デザイン: background #0B1530, border-radius 24dp 24dp 0 0）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // シート内部のタップは背後に伝播させない
                    )
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // 上部中央ドラッグ目印（38dp×4dp）
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline),
                    )
                }

                // 1. 時刻の範囲と有効・無効切り替え
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        val timePattern = remember { com.marutyan.termalarm.ui.common.clockTimePattern(false) }
                        // 開始時刻（押すと選べる）
                        Text(
                            text = formatClockMinutes(uiState.startMinutes, timePattern),
                            style = TextStyle(
                                fontFamily = ibmPlexMonoFontFamily(200),
                                fontSize = 40.sp,
                                lineHeight = 40.sp,
                                letterSpacing = (-0.04).em,
                                fontFeatureSettings = "tnum",
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = { showStartPicker = true },
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                        Text(
                            text = "\u2013",
                            fontSize = 20.sp,
                            color = MaterialTheme.customColors.subtleText,
                        )
                        // 終了時刻（押すと選べる）
                        Text(
                            text = formatClockMinutes(uiState.endMinutes, timePattern),
                            style = TextStyle(
                                fontFamily = ibmPlexMonoFontFamily(200),
                                fontSize = 40.sp,
                                lineHeight = 40.sp,
                                letterSpacing = (-0.04).em,
                                fontFeatureSettings = "tnum",
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = { showEndPicker = true },
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }

                    // タームの有効・無効スイッチ
                    TermEditSwitch(
                        checked = uiState.enabled,
                        onCheckedChange = viewModel::setEnabled,
                    )
                }

                // 2. 曜日（直径44dpの丸7つ、月から日の順で均等配置）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DAYS_OF_WEEK_ORDER.forEach { day ->
                        val isSelected = day in uiState.repeatDays
                        val dayLabel = when (day) {
                            DayOfWeek.MONDAY -> stringResource(R.string.day_monday_short)
                            DayOfWeek.TUESDAY -> stringResource(R.string.day_tuesday_short)
                            DayOfWeek.WEDNESDAY -> stringResource(R.string.day_wednesday_short)
                            DayOfWeek.THURSDAY -> stringResource(R.string.day_thursday_short)
                            DayOfWeek.FRIDAY -> stringResource(R.string.day_friday_short)
                            DayOfWeek.SATURDAY -> stringResource(R.string.day_saturday_short)
                            DayOfWeek.SUNDAY -> stringResource(R.string.day_sunday_short)
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    role = Role.Checkbox,
                                    onClick = { viewModel.toggleDay(day) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = dayLabel,
                                fontSize = 14.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.customColors.subtleText,
                            )
                        }
                    }
                }

                // 3. カード1枚にまとめた3行（ラベル、間隔、問題。各行58dp）
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        // 行1: ラベル
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = { showLabelDialog = true },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            TermTagIcon(color = MaterialTheme.customColors.subtleText)
                            Text(
                                text = stringResource(R.string.label_title),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (uiState.label.isNotBlank()) {
                                Text(
                                    text = uiState.label,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.customColors.subtleText,
                                )
                            }
                            TermChevronRightIcon(color = MaterialTheme.customColors.subtleText)
                        }

                        // 区切り線
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.surface),
                        )

                        // 行2: 間隔
                        val intervalSummary = if (uiState.isVariableInterval) {
                            stringResource(
                                R.string.term_edit_interval_accelerate_summary,
                                uiState.startIntervalMinutes,
                                uiState.endIntervalMinutes,
                            )
                        } else {
                            stringResource(
                                R.string.term_edit_interval_constant_summary,
                                uiState.startIntervalMinutes,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = { showIntervalSheet = true },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            TermIntervalBarsIcon(color = MaterialTheme.customColors.subtleText)
                            Text(
                                text = stringResource(R.string.term_edit_interval_label),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = intervalSummary,
                                fontSize = 14.sp,
                                fontFamily = ibmPlexMonoFontFamily(400),
                                color = MaterialTheme.customColors.subtleText,
                            )
                            TermChevronRightIcon(color = MaterialTheme.customColors.subtleText)
                        }

                        // 区切り線
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.surface),
                        )

                        // 行3: 問題
                        val challengeSummary = when (uiState.challengeTiming) {
                            ChallengeTiming.NEVER -> stringResource(R.string.challenge_timing_never)
                            ChallengeTiming.END_ONLY -> {
                                val timingLabel = stringResource(R.string.challenge_timing_end_only)
                                val levelLabel = if (uiState.challenge == ChallengeLevel.HARD) {
                                    stringResource(R.string.challenge_level_hard)
                                } else {
                                    stringResource(R.string.challenge_level_easy)
                                }
                                stringResource(R.string.challenge_summary_format, timingLabel, levelLabel)
                            }
                            ChallengeTiming.EVERY_TIME -> {
                                val timingLabel = stringResource(R.string.challenge_timing_every_time)
                                val levelLabel = if (uiState.challenge == ChallengeLevel.HARD) {
                                    stringResource(R.string.challenge_level_hard)
                                } else {
                                    stringResource(R.string.challenge_level_easy)
                                }
                                stringResource(R.string.challenge_summary_format, timingLabel, levelLabel)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = { showChallengePicker = true },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            TermQuestionCircleIcon(color = MaterialTheme.customColors.subtleText)
                            Text(
                                text = stringResource(R.string.term_edit_challenge_label),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = challengeSummary,
                                fontSize = 14.sp,
                                color = MaterialTheme.customColors.subtleText,
                            )
                            TermChevronRightIcon(color = MaterialTheme.customColors.subtleText)
                        }
                    }
                }

                // 4. カード1枚の二度寝チェック（58dp、左にアイコン、右に切り替え、下に説明1行）
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TermWakeCheckIcon(color = MaterialTheme.customColors.subtleText)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.term_edit_wake_check_label),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.term_edit_wake_check_desc),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.customColors.subtleText,
                            )
                        }
                        TermEditSwitch(
                            checked = uiState.wakeCheck,
                            onCheckedChange = viewModel::setWakeCheck,
                        )
                    }
                }

                // 5. 削除と保存ボタン（ピル形）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 削除ボタン（既存タームの場合に表示）
                    if (uiState.id != null) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = { showDeleteConfirm = true },
                                )
                                .padding(horizontal = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.term_edit_delete),
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(1.dp))
                    }

                    // 保存ボタン
                    Box(
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = viewModel::save,
                            )
                            .padding(horizontal = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }

        // --- サブ画面・ダイアログ ---

        // 開始時刻選択ダイアログ
        if (showStartPicker) {
            TimePickerDialogBox(
                initialMinutes = uiState.startMinutes,
                onDismiss = { showStartPicker = false },
                onConfirm = { minutes ->
                    viewModel.setStartMinutes(minutes)
                    showStartPicker = false
                },
            )
        }

        // 終了時刻選択ダイアログ
        if (showEndPicker) {
            TimePickerDialogBox(
                initialMinutes = uiState.endMinutes,
                onDismiss = { showEndPicker = false },
                onConfirm = { minutes ->
                    viewModel.setEndMinutes(minutes)
                    showEndPicker = false
                },
            )
        }

        // ラベル入力ポップアップ
        if (showLabelDialog) {
            LabelInputDialog(
                initialLabel = uiState.label,
                onDismiss = { showLabelDialog = false },
                onConfirm = { newLabel ->
                    viewModel.setLabel(newLabel)
                    showLabelDialog = false
                },
            )
        }

        // 問題選択ポップアップ
        if (showChallengePicker) {
            ChallengePickerDialog(
                initialTiming = uiState.challengeTiming,
                initialLevel = uiState.challenge,
                onDismiss = { showChallengePicker = false },
                onConfirm = { timing, level ->
                    viewModel.setChallenge(timing, level)
                    showChallengePicker = false
                },
            )
        }

        // 間隔設定シート（下から重なるシート）
        if (showIntervalSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showIntervalSheet = false },
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                IntervalEditSheet(
                    startMinutes = uiState.startMinutes,
                    endMinutes = uiState.endMinutes,
                    initialIsVariable = uiState.isVariableInterval,
                    initialStartInterval = uiState.startIntervalMinutes,
                    initialEndInterval = uiState.endIntervalMinutes,
                    onConfirm = { isVariable, startInt, endInt ->
                        viewModel.setInterval(isVariable, startInt, endInt)
                        showIntervalSheet = false
                    },
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // シート内タップは伝播させない
                    ),
                )
            }
        }

        // 削除確認ダイアログ
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.delete_alarm)) },
                text = { Text(stringResource(R.string.delete_alarm_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            viewModel.delete()
                        },
                    ) {
                        Text(stringResource(R.string.term_edit_delete), color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}
