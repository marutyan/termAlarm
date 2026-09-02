package com.marutyan.termalarm.ui.settings

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AlarmDismissMethod
import com.marutyan.termalarm.domain.ClockDisplayMode
import com.marutyan.termalarm.domain.VolumeButtonAction
import com.marutyan.termalarm.domain.WeekStart
import kotlin.math.roundToInt

// 消音までの時間・スヌーズの長さで選べる分数の候補(AlarmEditScreenのスヌーズ入力(1〜60分)より粗い、
// 設定画面としてよく使う値だけに絞ったプリセット)
private val MINUTE_PRESETS = listOf(1, 3, 5, 10, 15, 20, 30)

// アラームのフェードイン秒数の候補。0は「なし」を意味する
private val ALARM_FADE_IN_PRESETS = listOf(0, 5, 10, 15, 20, 25, 30)

// タイマーのフェードイン秒数の候補。既存の固定値(1.5秒)を含める
private val TIMER_FADE_IN_PRESETS = listOf(0f, 1.5f, 3f, 5f, 10f)

// 一度に1つしか開かないダイアログの種類。開いていなければnull
private enum class SettingsDialog {
    DISMISS_METHOD, AUTO_STOP, SNOOZE_LENGTH, ALARM_FADE_IN, VOLUME_BUTTON, WEEK_START,
    CLOCK_STYLE, TIMER_FADE_IN,
}

/**
 * 設定画面。design/tabs/Settings.dc.htmlを再現する。アラーム/時計/タイマーの3セクションに分け、
 * 値を持つ行はタップでダイアログを開き、切り替えの行はSwitchを直接操作する。
 * 時計のスタイル(アナログ/デジタル)はSettingsViewModel経由で既存のclock_settingsテーブルを読み書きする。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clockDisplayMode by viewModel.clockDisplayMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var openDialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    fun closeDialog() { openDialog = null }

    // システムの音選択(RingtoneManager)。AlarmEditScreenのアラーム音選択と同じ仕組みをタイマー用に持つ
    // (RingingService/AlarmEditは書き込み範囲外のため、共通化せずここに独立して持つ)
    val timerSoundPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.let {
            androidx.core.content.IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        viewModel.setTimerSoundUri(uri?.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSection(title = stringResource(R.string.settings_section_alarm)) {
                SettingsValueRow(
                    label = stringResource(R.string.settings_dismiss_method_title),
                    value = dismissMethodLabel(settings.dismissMethod),
                    onClick = { openDialog = SettingsDialog.DISMISS_METHOD },
                )
                SettingsValueRow(
                    label = stringResource(R.string.settings_auto_stop_title),
                    value = stringResource(R.string.interval_minutes_label, settings.autoStopMinutes),
                    onClick = { openDialog = SettingsDialog.AUTO_STOP },
                )
                SettingsValueRow(
                    label = stringResource(R.string.settings_snooze_length_title),
                    value = stringResource(R.string.interval_minutes_label, settings.defaultSnoozeMinutes),
                    onClick = { openDialog = SettingsDialog.SNOOZE_LENGTH },
                )
                SettingsValueRow(
                    label = stringResource(R.string.settings_fade_in_title),
                    value = formatSeconds(settings.alarmFadeInSeconds),
                    onClick = { openDialog = SettingsDialog.ALARM_FADE_IN },
                )
                SettingsValueRow(
                    label = stringResource(R.string.settings_volume_button_title),
                    value = volumeButtonLabel(settings.volumeButtonAction),
                    onClick = { openDialog = SettingsDialog.VOLUME_BUTTON },
                )
                SettingsValueRow(
                    label = stringResource(R.string.settings_week_start_title),
                    value = weekStartLabel(settings.weekStart),
                    onClick = { openDialog = SettingsDialog.WEEK_START },
                )
                AlarmVolumeRow()
            }

            SettingsSection(title = stringResource(R.string.settings_section_clock)) {
                SettingsValueRow(
                    label = stringResource(R.string.settings_clock_style_title),
                    value = clockStyleLabel(clockDisplayMode),
                    onClick = { openDialog = SettingsDialog.CLOCK_STYLE },
                )
                SettingsToggleRow(
                    label = stringResource(R.string.settings_show_seconds_title),
                    checked = settings.showClockSeconds,
                    onCheckedChange = viewModel::setShowClockSeconds,
                )
                SettingsValueRow(
                    label = stringResource(R.string.settings_date_time_title),
                    value = null,
                    onClick = { context.startActivity(Intent(AndroidSettings.ACTION_DATE_SETTINGS)) },
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_timer), showDivider = false) {
                SettingsValueRow(
                    label = stringResource(R.string.settings_timer_sound_title),
                    value = timerSoundLabel(context, settings.timerSoundUri),
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            settings.timerSoundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        }
                        timerSoundPickerLauncher.launch(intent)
                    },
                )
                SettingsValueRow(
                    label = stringResource(R.string.settings_fade_in_title),
                    value = formatSeconds(settings.timerFadeInSeconds),
                    onClick = { openDialog = SettingsDialog.TIMER_FADE_IN },
                )
                SettingsToggleRow(
                    label = stringResource(R.string.settings_timer_vibration_title),
                    checked = settings.timerVibration,
                    onCheckedChange = viewModel::setTimerVibration,
                )
            }
        }
    }

    when (openDialog) {
        SettingsDialog.DISMISS_METHOD -> ChoiceDialog(
            title = stringResource(R.string.settings_dismiss_method_title),
            options = AlarmDismissMethod.entries.map { it to dismissMethodLabel(it) },
            selected = settings.dismissMethod,
            onSelect = { viewModel.setDismissMethod(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        SettingsDialog.AUTO_STOP -> ChoiceDialog(
            title = stringResource(R.string.settings_auto_stop_title),
            options = MINUTE_PRESETS.map { it to stringResource(R.string.interval_minutes_label, it) },
            selected = settings.autoStopMinutes,
            onSelect = { viewModel.setAutoStopMinutes(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        SettingsDialog.SNOOZE_LENGTH -> ChoiceDialog(
            title = stringResource(R.string.settings_snooze_length_title),
            options = MINUTE_PRESETS.map { it to stringResource(R.string.interval_minutes_label, it) },
            selected = settings.defaultSnoozeMinutes,
            onSelect = { viewModel.setDefaultSnoozeMinutes(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        SettingsDialog.ALARM_FADE_IN -> ChoiceDialog(
            title = stringResource(R.string.settings_fade_in_title),
            options = ALARM_FADE_IN_PRESETS.map { it to formatSeconds(it) },
            selected = settings.alarmFadeInSeconds,
            onSelect = { viewModel.setAlarmFadeInSeconds(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        SettingsDialog.VOLUME_BUTTON -> ChoiceDialog(
            title = stringResource(R.string.settings_volume_button_title),
            options = VolumeButtonAction.entries.map { it to volumeButtonLabel(it) },
            selected = settings.volumeButtonAction,
            onSelect = { viewModel.setVolumeButtonAction(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        SettingsDialog.WEEK_START -> ChoiceDialog(
            title = stringResource(R.string.settings_week_start_title),
            options = WeekStart.entries.map { it to weekStartLabel(it) },
            selected = settings.weekStart,
            onSelect = { viewModel.setWeekStart(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        SettingsDialog.CLOCK_STYLE -> ChoiceDialog(
            title = stringResource(R.string.settings_clock_style_title),
            options = ClockDisplayMode.entries.map { it to clockStyleLabel(it) },
            selected = clockDisplayMode,
            onSelect = { viewModel.setClockDisplayMode(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        SettingsDialog.TIMER_FADE_IN -> ChoiceDialog(
            title = stringResource(R.string.settings_fade_in_title),
            options = TIMER_FADE_IN_PRESETS.map { it to formatSeconds(it) },
            selected = settings.timerFadeInSeconds,
            onSelect = { viewModel.setTimerFadeInSeconds(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        null -> Unit
    }
}

// セクション見出し + 行の並び + 区切り線。モックのpadding(見出し24/8, 行24/14, 区切り線上マージン8)を再現する
@Composable
private fun SettingsSection(title: String, showDivider: Boolean = true, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        content()
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// 値を表示し、タップでダイアログや外部画面を開く行。value=nullなら値行を出さない(「日付と時刻の変更」用)
@Composable
private fun SettingsValueRow(label: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// 切り替え(Switch)の行。行全体をタップしても切り替わるようにする
@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// アラームの音量スライダー。アプリ側に値を持たず、端末のSTREAM_ALARMを直接読み書きする
// (docs/OFFICIAL_SETTINGS.md「アラームの音量」)。ハードウェアの音量ボタンによる変更は
// この画面を開き直すまで反映されない(簡易な実装として許容する)。
@Composable
private fun AlarmVolumeRow() {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).toFloat() }
    var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat()) }

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_volume_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = volume,
            valueRange = 0f..maxVolume,
            steps = (maxVolume.roundToInt() - 1).coerceAtLeast(0),
            onValueChange = { newValue ->
                volume = newValue
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, newValue.roundToInt(), 0)
            },
        )
    }
}

// 単一選択のダイアログ。ダイアログの種類ごとにAlertDialogを書き分けないための共通実装
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(value) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Text(text = label, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun dismissMethodLabel(method: AlarmDismissMethod): String = when (method) {
    AlarmDismissMethod.TAP -> stringResource(R.string.settings_dismiss_method_tap)
    AlarmDismissMethod.SWIPE -> stringResource(R.string.settings_dismiss_method_swipe)
}

@Composable
private fun volumeButtonLabel(action: VolumeButtonAction): String = when (action) {
    VolumeButtonAction.ADJUST_VOLUME -> stringResource(R.string.settings_volume_button_adjust)
    VolumeButtonAction.SNOOZE -> stringResource(R.string.settings_volume_button_snooze)
    VolumeButtonAction.DISMISS -> stringResource(R.string.settings_volume_button_dismiss)
}

@Composable
private fun weekStartLabel(weekStart: WeekStart): String = when (weekStart) {
    WeekStart.SUNDAY -> stringResource(R.string.settings_week_start_sunday)
    WeekStart.MONDAY -> stringResource(R.string.settings_week_start_monday)
}

@Composable
private fun clockStyleLabel(mode: ClockDisplayMode): String = when (mode) {
    ClockDisplayMode.ANALOG -> stringResource(R.string.clock_display_mode_analog)
    ClockDisplayMode.DIGITAL -> stringResource(R.string.clock_display_mode_digital)
}

// 秒数の表示用整形。0は「なし」、小数を含む場合はそのまま(1.5秒)、整数なら小数点を出さない(5秒)
@Composable
private fun formatSeconds(seconds: Number): String {
    val value = seconds.toFloat()
    if (value == 0f) return stringResource(R.string.settings_fade_in_off)
    val text = if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
    return stringResource(R.string.settings_seconds_format, text)
}

// 選択中のUriからタイトルを取り出す。未設定・取得失敗時は既定の音の表記にする(AlarmEditScreenと同じ方針)
private fun timerSoundLabel(context: Context, uriString: String?): String {
    val defaultLabel = context.getString(R.string.sound_default)
    val uri = uriString?.let(Uri::parse) ?: return defaultLabel
    return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: defaultLabel
}
