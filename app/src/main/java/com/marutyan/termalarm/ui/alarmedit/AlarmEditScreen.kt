package com.marutyan.termalarm.ui.alarmedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.R
import com.marutyan.termalarm.alarm.NotificationPermission
import com.marutyan.termalarm.domain.ChallengeLevel
import com.marutyan.termalarm.domain.ChallengeTiming
import com.marutyan.termalarm.ui.common.formatClockMinutes
import com.marutyan.termalarm.ui.permission.isNotificationPermissionRequested
import com.marutyan.termalarm.ui.permission.setNotificationPermissionRequested
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily
import java.time.DayOfWeek

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
 * 下から持ち上がるModalBottomSheet形式で構成し、時刻範囲、曜日、設定行、二度寝チェック、保存・削除ボタンを提供する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(
    viewModel: AlarmEditViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState

    // 二度寝チェックの説明に出す待ち時間。全体の設定で変えられるため、読んで反映する
    val wakeCheckMinutes by remember(context) { Repositories.settings(context).observe() }
        .collectAsStateWithLifecycle(initialValue = AppSettings())
        .let { state -> remember { derivedStateOf { state.value.wakeCheckMinutes } } }

    // 通知権限の要求ランチャー。初回保存時に要求し、結果受け取り後に保存を実行する
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        setNotificationPermissionRequested(context)
        viewModel.save()
    }

    // ターム保存時のアクション。初回保存時かつ未要求の場合は通知権限を先に求め、それ以外は直接保存する
    val onSave = {
        val shouldRequestPermission = NotificationPermission.isRuntimeRequestRequired() &&
            !NotificationPermission.isGranted(context) &&
            !isNotificationPermissionRequested(context)

        if (shouldRequestPermission) {
            notificationPermissionLauncher.launch(NotificationPermission.PERMISSION)
        } else {
            viewModel.save()
        }
    }

    // 保存または削除が完了したら閉じる
    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) onClose()
    }

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var showSinglePicker by rememberSaveable { mutableStateOf(false) }
    var showLabelDialog by rememberSaveable { mutableStateOf(false) }
    var showIntervalSheet by rememberSaveable { mutableStateOf(false) }
    var showChallengePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        // ターム編集シートの高さを画面の6割前後に設定する（デザイン TermEdit.dc.html に合わせる）
        val sheetMinHeight = screenHeight * 0.60f

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = sheetMinHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // 1. 時刻の範囲と有効・無効切り替え
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val timePattern = remember { com.marutyan.termalarm.ui.common.clockTimePattern(false) }
                    if (uiState.isSingleAlarm) {
                        // 通常アラーム: 時刻を1つだけ選ばせる（開始と終了へ同じ時刻を入れる）
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
                                    onClick = { showSinglePicker = true },
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
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
                    }

                    // タームの有効・無効スイッチ
                    TermEditSwitch(
                        checked = uiState.enabled,
                        onCheckedChange = viewModel::setEnabled,
                    )
                }

                // 2. 曜日（直径44dpの丸7つ、月から日の順で均等配置。当たり判定44dp以上を確保）
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val availableWidth = maxWidth
                    val idealCircleSize = 44.dp
                    // 7つの丸が44dpで収まるか判定（44dp * 7 = 308dp）
                    val circleVisualSize = if (availableWidth >= idealCircleSize * 7) {
                        idealCircleSize
                    } else {
                        (availableWidth / 7).coerceAtLeast(28.dp)
                    }

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
                            // 外側は最低44dp×44dpのタップ当たり判定を保証
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 22.dp),
                                        role = Role.Checkbox,
                                        onClick = { viewModel.toggleDay(day) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(circleVisualSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = dayLabel,
                                        fontSize = 14.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
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
                            Icon(
                                imageVector = TermTagIcon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.customColors.subtleText,
                            )
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
                            Icon(
                                imageVector = TermChevronRightIcon,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.customColors.subtleText,
                            )
                        }

                        // 区切り線
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.surface),
                        )

                        // 行2: 間隔（通常アラーム時は表示しない）
                        if (!uiState.isSingleAlarm) {
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
                                Icon(
                                    imageVector = TermIntervalBarsIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.customColors.subtleText,
                                )
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
                                Icon(
                                    imageVector = TermChevronRightIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = MaterialTheme.customColors.subtleText,
                                )
                            }

                            // 区切り線
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.surface),
                            )
                        }

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
                            Icon(
                                imageVector = TermQuestionCircleIcon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.customColors.subtleText,
                            )
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
                            Icon(
                                imageVector = TermChevronRightIcon,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.customColors.subtleText,
                            )
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
                        Icon(
                            imageVector = TermWakeCheckIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.customColors.subtleText,
                        )
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
                                text = stringResource(R.string.term_edit_wake_check_desc, wakeCheckMinutes),
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
                                onClick = onSave,
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

            // 間隔設定ポップアップ
            if (showIntervalSheet) {
                IntervalEditDialog(
                    startMinutes = uiState.startMinutes,
                    endMinutes = uiState.endMinutes,
                    initialIsVariable = uiState.isVariableInterval,
                    initialStartInterval = uiState.startIntervalMinutes,
                    initialEndInterval = uiState.endIntervalMinutes,
                    onDismiss = { showIntervalSheet = false },
                    onConfirm = { isVariable, startInt, endInt ->
                        viewModel.setInterval(isVariable, startInt, endInt)
                        showIntervalSheet = false
                    },
                )
            }
        }

        // --- サブ画面・ダイアログ ---

        // 単一時刻選択ダイアログ（通常アラーム用）
        if (showSinglePicker) {
            TimePickerDialogBox(
                initialMinutes = uiState.startMinutes,
                onDismiss = { showSinglePicker = false },
                onConfirm = { minutes ->
                    viewModel.setSingleMinutes(minutes)
                    showSinglePicker = false
                },
            )
        }

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
