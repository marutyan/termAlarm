package com.marutyan.termalarm.ui.alarmedit

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.COMPACT_SCREEN_HEIGHT_THRESHOLD
import com.marutyan.termalarm.ui.theme.alarmCardClock
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.WeekStart
import com.marutyan.termalarm.domain.occurrenceCount
import com.marutyan.termalarm.ui.alarmlist.orderedDaysOfWeek
import com.marutyan.termalarm.ui.common.formatClockMinutes
import com.marutyan.termalarm.ui.theme.tabularNums
import java.time.DayOfWeek
import kotlin.math.roundToInt

/**
 * アラーム追加・編集画面。design/AlarmEdit.dc.htmlを再現する。
 * idがnullなら新規作成、そうでなければ既存アラームの編集として動作する(ViewModelがロードを担う)。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlarmEditScreen(
    viewModel: AlarmEditViewModel,
    onClose: () -> Unit,
) {
    val uiState = viewModel.uiState
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()

    // 保存・削除が完了したら呼び出し側(NavHost)に画面を閉じてもらう
    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) onClose()
    }

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var showLabelDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    // アラーム音選択はAndroid標準のRingtonePickerを呼び出す(新しい依存やUIの自作をしない)
    val soundPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let { androidx.core.content.IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        viewModel.setSoundUri(uri?.toString())
    }
    val soundLabel = remember(uiState.soundUri) {
        val uri = uiState.soundUri?.let(Uri::parse)
        if (uri == null) {
            null
        } else {
            runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
        }
    } ?: stringResource(R.string.sound_default)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.id == null) stringResource(R.string.edit_title_new) else stringResource(R.string.edit_title_existing),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    Button(onClick = viewModel::save, modifier = Modifier.padding(end = 12.dp)) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val isCompact = maxHeight < COMPACT_SCREEN_HEIGHT_THRESHOLD
            val itemSpacing = if (isCompact) 12.dp else 20.dp
            val bottomSpacerHeight = if (isCompact) 12.dp else 24.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                TimeRangeRow(
                    startMinutes = uiState.startMinutes,
                    endMinutes = uiState.endMinutes,
                    onStartClick = { showStartPicker = true },
                    onEndClick = { showEndPicker = true },
                    isCompact = isCompact,
                )

                IntervalSection(
                    intervalMinutes = uiState.intervalMinutes,
                    useCustomInterval = uiState.useCustomInterval,
                    onSelectPreset = viewModel::selectPresetInterval,
                    onSelectCustom = viewModel::selectCustomInterval,
                    onCustomIntervalChange = viewModel::setCustomInterval,
                )

                PreviewBanner(
                    startMinutes = uiState.startMinutes,
                    endMinutes = uiState.endMinutes,
                    intervalMinutes = uiState.intervalMinutes,
                    isValid = uiState.validationError == null,
                )

                uiState.validationError?.let { error ->
                    Text(
                        text = stringResource(validationMessageRes(error)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                RepeatDaysSection(selectedDays = uiState.repeatDays, weekStart = weekStart, onToggleDay = viewModel::toggleDay)

                GeneralSettingsSection(
                    label = uiState.label,
                    soundLabel = soundLabel,
                    vibrate = uiState.vibrate,
                    onLabelClick = { showLabelDialog = true },
                    onSoundClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            uiState.soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it.toUri()) }
                        }
                        soundPickerLauncher.launch(intent)
                    },
                    onVibrateChange = viewModel::setVibrate,
                )

                DifficultToStopSection(
                    skipRequiresApp = uiState.skipRequiresApp,
                    skipGame = uiState.skipGame,
                    snoozeEnabled = uiState.snoozeEnabled,
                    snoozeMinutes = uiState.snoozeMinutes,
                    onSkipRequiresAppChange = viewModel::setSkipRequiresApp,
                    onSkipGameChange = viewModel::setSkipGame,
                    onSnoozeEnabledChange = viewModel::setSnoozeEnabled,
                    onSnoozeMinutesChange = viewModel::setSnoozeMinutes,
                )

                if (uiState.id != null) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.delete_alarm))
                    }
                }

                Spacer(Modifier.height(bottomSpacerHeight))
            }
        }
    }

    if (showStartPicker) {
        TimePickerDialogBox(
            initialMinutes = uiState.startMinutes,
            onDismiss = { showStartPicker = false },
            onConfirm = { minutes -> viewModel.setStartMinutes(minutes); showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimePickerDialogBox(
            initialMinutes = uiState.endMinutes,
            onDismiss = { showEndPicker = false },
            onConfirm = { minutes -> viewModel.setEndMinutes(minutes); showEndPicker = false },
        )
    }
    if (showLabelDialog) {
        LabelEditDialog(
            initialLabel = uiState.label,
            onDismiss = { showLabelDialog = false },
            onConfirm = { newLabel -> viewModel.setLabel(newLabel); showLabelDialog = false },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_alarm)) },
            text = { Text(stringResource(R.string.delete_alarm_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) {
                    Text(stringResource(R.string.delete_alarm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// 検証エラーの種類をユーザー向け文言のリソースIDへ変換する
private fun validationMessageRes(error: AlarmEditValidationError): Int = when (error) {
    AlarmEditValidationError.INTERVAL_NOT_POSITIVE -> R.string.error_interval_not_positive
    AlarmEditValidationError.INTERVAL_TOO_LARGE -> R.string.error_interval_too_large
    AlarmEditValidationError.CUSTOM_INTERVAL_INVALID -> R.string.error_custom_interval_invalid
    AlarmEditValidationError.SNOOZE_OUT_OF_RANGE -> R.string.error_snooze_out_of_range
}

// 開始・終了時刻の2枚のカード。狭い画面(isCompact=true)ではカード内の余白と文字を詰める。
@Composable
private fun TimeRangeRow(
    startMinutes: Int,
    endMinutes: Int,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    isCompact: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)) {
        TimeCard(
            label = stringResource(R.string.start_time),
            minutes = startMinutes,
            highlighted = true,
            modifier = Modifier.weight(1f),
            onClick = onStartClick,
            isCompact = isCompact,
        )
        TimeCard(
            label = stringResource(R.string.end_time),
            minutes = endMinutes,
            highlighted = false,
            modifier = Modifier.weight(1f),
            onClick = onEndClick,
            isCompact = isCompact,
        )
    }
}

@Composable
private fun TimeCard(
    label: String,
    minutes: Int,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isCompact: Boolean = false,
) {
    val containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val cardPadding = if (isCompact) 10.dp else 16.dp
    val textStyle = if (isCompact) {
        MaterialTheme.typography.titleLarge.alarmCardClock().copy(fontSize = 32.sp, lineHeight = 38.sp)
    } else {
        MaterialTheme.typography.displayLarge.alarmCardClock()
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(if (isCompact) 16.dp else 24.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(cardPadding),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
        // 一覧のカードと同じ大きさにする。狭い画面でははみ出しを防ぐため一段階小さくする
        Text(
            text = formatClockMinutes(minutes),
            style = textStyle,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// 「その他」選択時に大きな数字とスライダーで間隔(1〜120分)を直感的に指定する入力欄。
// 小さな文字入力欄に比べて視認性を高め、指で素早く調整できるようにするために必要
@Composable
private fun CustomIntervalPicker(
    intervalMinutes: Int,
    onIntervalChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = intervalMinutes.toString(),
                style = MaterialTheme.typography.displayMedium.tabularNums(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.unit_minutes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Slider(
            value = intervalMinutes.toFloat().coerceIn(CUSTOM_INTERVAL_MIN.toFloat(), CUSTOM_INTERVAL_MAX.toFloat()),
            onValueChange = { onIntervalChange(it.roundToInt().coerceIn(CUSTOM_INTERVAL_MIN, CUSTOM_INTERVAL_MAX)) },
            valueRange = CUSTOM_INTERVAL_MIN.toFloat()..CUSTOM_INTERVAL_MAX.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// 鳴らす間隔の選択チップ。Material3 ExpressiveのToggleButtonを使う
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IntervalSection(
    intervalMinutes: Int,
    useCustomInterval: Boolean,
    onSelectPreset: (Int) -> Unit,
    onSelectCustom: () -> Unit,
    onCustomIntervalChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.interval_section_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 行ごとに中央へ寄せる。端から端へ散らすと、2行目に少数のボタンが残ったとき左右へ離れて偏る
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            INTERVAL_PRESETS_MINUTES.forEach { minutes ->
                val selected = !useCustomInterval && intervalMinutes == minutes
                ToggleButton(checked = selected, onCheckedChange = { onSelectPreset(minutes) }) {
                    Text(stringResource(R.string.interval_minutes_label, minutes))
                }
            }
            ToggleButton(checked = useCustomInterval, onCheckedChange = { onSelectCustom() }) {
                Text(stringResource(R.string.interval_custom_label))
            }
        }
        if (useCustomInterval) {
            CustomIntervalPicker(
                intervalMinutes = intervalMinutes,
                onIntervalChange = onCustomIntervalChange,
            )
        }
    }
}

// 「7:00から9:00まで25回鳴ります」のリアルタイムプレビュー。回数計算はdomain.occurrenceCountをそのまま使う
@Composable
private fun PreviewBanner(startMinutes: Int, endMinutes: Int, intervalMinutes: Int, isValid: Boolean) {
    if (!isValid || intervalMinutes <= 0) return
    val schedule = remember(startMinutes, endMinutes, intervalMinutes) {
        AlarmSchedule(
            id = 0, startMinutes = startMinutes, endMinutes = endMinutes, intervalMinutes = intervalMinutes,
            repeatDays = emptySet(), label = "", soundUri = null, vibrate = false, enabled = true, skippedSessionStart = null,
        )
    }
    val count = occurrenceCount(schedule)
    val message = if (startMinutes == endMinutes) {
        stringResource(R.string.preview_single, formatClockMinutes(startMinutes))
    } else {
        stringResource(R.string.preview_range, formatClockMinutes(startMinutes), formatClockMinutes(endMinutes), count)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_clock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp),
        )
        Text(message, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

// 繰り返す曜日の選択。design/AlarmEdit.dc.htmlと同じ真円のチップに合わせるため、ToggleButtonではなく直接描画する
@Composable
private fun RepeatDaysSection(selectedDays: Set<DayOfWeek>, weekStart: WeekStart, onToggleDay: (DayOfWeek) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.repeat_section_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 固定間隔で並べると7つが左へ寄って右に余白ができるため、幅いっぱいに均等配置する
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            orderedDaysOfWeek(weekStart).forEach { day ->
                val selected = day in selectedDays
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onToggleDay(day) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dayShortLabel(day),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun dayShortLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "月"
    DayOfWeek.TUESDAY -> "火"
    DayOfWeek.WEDNESDAY -> "水"
    DayOfWeek.THURSDAY -> "木"
    DayOfWeek.FRIDAY -> "金"
    DayOfWeek.SATURDAY -> "土"
    DayOfWeek.SUNDAY -> "日"
}

