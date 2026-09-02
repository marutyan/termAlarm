package com.marutyan.termalarm.ui.alarmedit

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.occurrenceCount
import com.marutyan.termalarm.ui.common.formatClockMinutes
import java.time.DayOfWeek

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TimeRangeRow(
                startMinutes = uiState.startMinutes,
                endMinutes = uiState.endMinutes,
                onStartClick = { showStartPicker = true },
                onEndClick = { showEndPicker = true },
            )

            IntervalSection(
                intervalMinutes = uiState.intervalMinutes,
                useCustomInterval = uiState.useCustomInterval,
                customIntervalText = uiState.customIntervalText,
                onSelectPreset = viewModel::selectPresetInterval,
                onSelectCustom = viewModel::selectCustomInterval,
                onCustomTextChange = viewModel::setCustomIntervalText,
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

            RepeatDaysSection(selectedDays = uiState.repeatDays, onToggleDay = viewModel::toggleDay)

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
                        uiState.soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
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

            Spacer(Modifier.height(24.dp))
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
}

// 開始・終了時刻の2枚のカード
@Composable
private fun TimeRangeRow(startMinutes: Int, endMinutes: Int, onStartClick: () -> Unit, onEndClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeCard(
            label = stringResource(R.string.start_time),
            minutes = startMinutes,
            highlighted = true,
            modifier = Modifier.weight(1f),
            onClick = onStartClick,
        )
        TimeCard(
            label = stringResource(R.string.end_time),
            minutes = endMinutes,
            highlighted = false,
            modifier = Modifier.weight(1f),
            onClick = onEndClick,
        )
    }
}

@Composable
private fun TimeCard(label: String, minutes: Int, highlighted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
        Text(formatClockMinutes(minutes), style = MaterialTheme.typography.displaySmall, color = contentColor)
    }
}

// 鳴らす間隔の選択チップ。Material3 ExpressiveのToggleButtonを使う
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IntervalSection(
    intervalMinutes: Int,
    useCustomInterval: Boolean,
    customIntervalText: String,
    onSelectPreset: (Int) -> Unit,
    onSelectCustom: () -> Unit,
    onCustomTextChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.interval_section_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            OutlinedTextField(
                value = customIntervalText,
                onValueChange = onCustomTextChange,
                label = { Text(stringResource(R.string.interval_custom_input_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
private fun RepeatDaysSection(selectedDays: Set<DayOfWeek>, onToggleDay: (DayOfWeek) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.repeat_section_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
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

// ラベル・アラーム音・バイブレーションの設定カード
@Composable
private fun GeneralSettingsSection(
    label: String,
    soundLabel: String,
    vibrate: Boolean,
    onLabelClick: () -> Unit,
    onSoundClick: () -> Unit,
    onVibrateChange: (Boolean) -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        SettingsRow(iconRes = R.drawable.ic_label, title = stringResource(R.string.label_title), value = label.ifBlank { stringResource(R.string.label_placeholder) }, onClick = onLabelClick)
        HorizontalDivider()
        SettingsRow(iconRes = R.drawable.ic_sound, title = stringResource(R.string.sound_title), value = soundLabel, onClick = onSoundClick)
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painter = painterResource(R.drawable.ic_vibration), contentDescription = null)
            Text(stringResource(R.string.vibration_title), modifier = Modifier.weight(1f))
            Switch(checked = vibrate, onCheckedChange = onVibrateChange)
        }
    }
}

@Composable
private fun SettingsRow(iconRes: Int, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null)
        Text(title, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 「止めにくさ」設定カード。当日終了をアプリからのみ許すか、ゲームを挟むか、スヌーズの3項目(docs/SPEC.md「誤操作の防止と当日終了」)。
 * skipRequiresAppがオフのときはskipGameを選べないようにし、理由を添える。
 */
@Composable
private fun DifficultToStopSection(
    skipRequiresApp: Boolean,
    skipGame: Boolean,
    snoozeEnabled: Boolean,
    snoozeMinutes: Int,
    onSkipRequiresAppChange: (Boolean) -> Unit,
    onSkipGameChange: (Boolean) -> Unit,
    onSnoozeEnabledChange: (Boolean) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.difficulty_section_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            ToggleSettingRow(
                title = stringResource(R.string.skip_requires_app_title),
                subtitle = stringResource(R.string.skip_requires_app_subtitle),
                checked = skipRequiresApp,
                onCheckedChange = onSkipRequiresAppChange,
            )
            HorizontalDivider()
            ToggleSettingRow(
                title = stringResource(R.string.skip_game_title),
                subtitle = if (skipRequiresApp) stringResource(R.string.skip_game_subtitle) else stringResource(R.string.skip_game_disabled_reason),
                checked = skipGame,
                enabled = skipRequiresApp,
                onCheckedChange = onSkipGameChange,
            )
            HorizontalDivider()
            ToggleSettingRow(
                title = stringResource(R.string.snooze_title),
                subtitle = stringResource(R.string.snooze_subtitle),
                checked = snoozeEnabled,
                onCheckedChange = onSnoozeEnabledChange,
            )
            if (snoozeEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.snooze_minutes_label), modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = snoozeMinutes.toString(),
                        onValueChange = { text -> text.toIntOrNull()?.let(onSnoozeMinutesChange) },
                        modifier = Modifier.size(width = 88.dp, height = 56.dp),
                        singleLine = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// 開始・終了時刻を選ぶダイアログ。Material3のTimePickerをそのまま表示するだけの薄いラッパー
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogBox(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

// ラベルを編集するダイアログ
@Composable
private fun LabelEditDialog(initialLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_title)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
