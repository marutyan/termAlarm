package com.marutyan.termalarm.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.ui.theme.COMPACT_SCREEN_HEIGHT_THRESHOLD
import com.marutyan.termalarm.R
import com.marutyan.termalarm.alarm.SoundFadeIn
import com.marutyan.termalarm.domain.ClockDisplayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean

// 消音までの時間で選べる分数の候補
private val MINUTE_PRESETS = listOf(1, 3, 5, 10, 15, 20, 30)

// フェードイン秒数の候補。0は「なし」を意味する
private val FADE_IN_PRESETS = listOf(0, 5, 10, 15, 20, 25, 30)

// 一度に1つしか開かないダイアログの種類。開いていなければnull
private enum class SettingsDialog {
    SILENCE_AFTER, FADE_IN, CLOCK_STYLE,
}

/**
 * 設定画面。design/tabs/Settings.dc.htmlを再現する。アラーム/時計のセクションに分け、
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

    // システムの音選択(RingtoneManager)
    val soundPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.let {
            androidx.core.content.IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        viewModel.setAlarmSoundUri(uri?.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingsSection(title = stringResource(R.string.settings_section_alarm), isCompact = isCompact) {
                    SettingsValueRow(
                        label = stringResource(R.string.sound_title),
                        value = soundLabel(context, settings.alarmSoundUri),
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                settings.alarmSoundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it.toUri()) }
                            }
                            soundPickerLauncher.launch(intent)
                        },
                        isCompact = isCompact,
                    )
                    SettingsToggleRow(
                        label = stringResource(R.string.vibration_title),
                        checked = settings.vibration,
                        onCheckedChange = viewModel::setVibration,
                        isCompact = isCompact,
                    )
                    AlarmVolumeRow(isCompact = isCompact)
                    SettingsValueRow(
                        label = stringResource(R.string.settings_fade_in_title),
                        value = formatSeconds(settings.fadeInSeconds),
                        onClick = { openDialog = SettingsDialog.FADE_IN },
                        isCompact = isCompact,
                    )
                    SettingsValueRow(
                        label = stringResource(R.string.settings_auto_stop_title),
                        value = settings.silenceAfterMinutes?.let { stringResource(R.string.interval_minutes_label, it) } ?: stringResource(R.string.settings_fade_in_off),
                        onClick = { openDialog = SettingsDialog.SILENCE_AFTER },
                        isCompact = isCompact,
                    )
                }

                SettingsSection(title = stringResource(R.string.settings_section_clock), showDivider = false, isCompact = isCompact) {
                    SettingsValueRow(
                        label = stringResource(R.string.settings_clock_style_title),
                        value = clockStyleLabel(clockDisplayMode),
                        onClick = { openDialog = SettingsDialog.CLOCK_STYLE },
                        isCompact = isCompact,
                    )
                    SettingsValueRow(
                        label = stringResource(R.string.settings_date_time_title),
                        value = null,
                        onClick = { context.startActivity(Intent(AndroidSettings.ACTION_DATE_SETTINGS)) },
                        isCompact = isCompact,
                    )
                }
            }
        }
    }

    when (openDialog) {
        SettingsDialog.SILENCE_AFTER -> ChoiceDialog(
            title = stringResource(R.string.settings_auto_stop_title),
            options = listOf(null to stringResource(R.string.settings_fade_in_off)) +
                MINUTE_PRESETS.map { it to stringResource(R.string.interval_minutes_label, it) },
            selected = settings.silenceAfterMinutes,
            onSelect = { viewModel.setSilenceAfterMinutes(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        SettingsDialog.FADE_IN -> ChoiceDialog(
            title = stringResource(R.string.settings_fade_in_title),
            options = FADE_IN_PRESETS.map { it to formatSeconds(it) },
            selected = settings.fadeInSeconds,
            onSelect = { viewModel.setFadeInSeconds(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        SettingsDialog.CLOCK_STYLE -> ChoiceDialog(
            title = stringResource(R.string.settings_clock_style_title),
            options = ClockDisplayMode.entries.map { it to clockStyleLabel(it) },
            selected = clockDisplayMode,
            onSelect = { viewModel.setClockDisplayMode(it); closeDialog() },
            onDismiss = ::closeDialog,
        )
        null -> Unit
    }
}

// セクション見出し + 行の並び + 区切り線。モックのpadding(見出し24/8, 行24/14, 区切り線上マージン8)を再現する。
// 狭い画面(isCompact=true)では上下の余白を詰めてスクロールしやすくする。
@Composable
private fun SettingsSection(title: String, showDivider: Boolean = true, isCompact: Boolean = false, content: @Composable () -> Unit) {
    val topPadding = if (isCompact) 12.dp else 24.dp
    val bottomPadding = if (isCompact) 4.dp else 8.dp
    val dividerTop = if (isCompact) 4.dp else 8.dp
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, top = topPadding, end = 24.dp, bottom = bottomPadding),
        )
        content()
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = dividerTop),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// 値を表示し、タップでダイアログや外部画面を開く行。value=nullなら値行を出さない(「日付と時刻の変更」用)
@Composable
private fun SettingsValueRow(label: String, value: String?, onClick: () -> Unit, isCompact: Boolean = false) {
    val verticalPadding = if (isCompact) 8.dp else 14.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = verticalPadding),
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
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, isCompact: Boolean = false) {
    val verticalPadding = if (isCompact) 8.dp else 14.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * アラームの音量スライダー。Androidの時計アプリが一般に取る扱いへ合わせている。
 *
 * - 値はアプリ側に持たず、端末のSTREAM_ALARMを直接読み書きする
 * - 下限は0ではなく端末が返す最小値。端末によってはアラームを完全に無音にできない
 * - 端末の音量が別の場所で変わることがあるため、保存先のSettings.Systemを見張って合わせる
 * - 自分で書き換えた分は見張りが1回読み飛ばす。指で動かしている最中も合わせに行かない
 * - サイレントモードでアラームが鳴らせない状態のときは操作できなくする
 * - 指を離した時点でその音量の試聴音を鳴らす。数字だけでは大きさが分からないため。
 *   連打で鳴り続けないよう、鳴らした後2秒は次を鳴らさない
 */
@Composable
private fun AlarmVolumeRow(isCompact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val minVolume = remember { alarmMinVolume(audioManager).toFloat() }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).toFloat() }
    var volume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat().coerceIn(minVolume, maxVolume))
    }
    var isEnabled by remember { mutableStateOf(alarmVolumeAdjustable(context)) }
    val previewPlayer = remember { AlarmVolumePreviewPlayer(context) }
    DisposableEffect(Unit) { onDispose { previewPlayer.stop() } }

    // 指で動かしている最中は、端末側の値で上書きしない。触っている場所が飛んでしまうため
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    // 自分でsetStreamVolumeした分の通知を1回だけ読み飛ばすための目印
    val skipNextChange = remember { AtomicBoolean(false) }

    // 端末側で音量が変わったら、このスライダーも合わせる（純正と同じくSettings.Systemを見張る）
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (skipNextChange.getAndSet(false)) return
                if (isDragged) return
                volume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat().coerceIn(minVolume, maxVolume)
                isEnabled = alarmVolumeAdjustable(context)
            }
        }
        context.contentResolver.registerContentObserver(AndroidSettings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val verticalPadding = if (isCompact) 4.dp else 8.dp

    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 純正はスライダーの左にアラームのアイコンを置き、消音のときは絵柄を切り替える
        Icon(
            painter = painterResource(
                if (volume <= 0f) R.drawable.ic_alarm_off else R.drawable.ic_alarm_tab,
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_volume_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = volume,
                valueRange = minVolume..maxVolume,
                enabled = isEnabled,
                interactionSource = interactionSource,
                // stepsを指定すると目盛りの点が描かれる。純正の音量スライダーに点は無いので指定せず、
                // 代わりに値を整数へ丸めることで、見た目を保ったまま段階どおりに止まるようにする
                onValueChange = { newValue ->
                    val stepped = newValue.roundToInt()
                    if (stepped.toFloat() != volume) {
                        volume = stepped.toFloat()
                        skipNextChange.set(true)
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, stepped, 0)
                    }
                },
                onValueChangeFinished = { if (volume > 0f) previewPlayer.play(scope) },
            )
        }
    }
}

// 端末が許すアラーム音量の下限。API28より前はこの値を聞けないため0とする
private fun alarmMinVolume(audioManager: AudioManager): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        audioManager.getStreamMinVolume(AudioManager.STREAM_ALARM)
    } else {
        0
    }

/**
 * いま音量を変えられる状態か。サイレントモードでアラームまで止められているときは、
 * 動かしても意味がないので操作できなくする（純正も同じ判定でスライダーを無効にする）。
 * 通知ポリシーを読む権限が無い端末では、判断できないので操作できる扱いにする。
 */
private fun alarmVolumeAdjustable(context: Context): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return true
    return when (manager.currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_NONE -> false
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> runCatching {
            manager.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0
        }.getOrDefault(true)
        else -> true
    }
}

// アラーム音量スライダーの試聴音再生。USAGE_ALARMで鳴らすことで、端末のアラーム音量(直前にAudioManagerで
// 変えた値)がそのまま反映される。短く鳴らして自動的に止める使い切りのMediaPlayerを、呼ぶたびに作り直す。
private class AlarmVolumePreviewPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var stopJob: Job? = null

    // 直前に鳴らし始めた時刻。連打で鳴り続けるのを防ぐために覚えておく
    private var lastPlayedAt = 0L

    fun play(scope: CoroutineScope) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayedAt < PREVIEW_COOLDOWN_MILLIS) return
        stop()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) ?: return
        val newPlayer = MediaPlayer()
        runCatching {
            newPlayer.setAudioAttributes(SoundFadeIn.alarmAudioAttributes())
            newPlayer.setDataSource(context, uri)
            newPlayer.prepare()
            newPlayer.start()
        }.onSuccess {
            lastPlayedAt = now
            player = newPlayer
            stopJob = scope.launch {
                delay(PREVIEW_DURATION_MILLIS)
                stop()
            }
        }.onFailure { newPlayer.release() }
    }

    fun stop() {
        stopJob?.cancel()
        stopJob = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    companion object {
        // 大きさが分かれば十分な長さ。純正のスライダーも一瞬だけ鳴る
        private const val PREVIEW_DURATION_MILLIS = 1200L

        // 鳴らした後、次を鳴らさない時間。純正も2秒空けている
        private const val PREVIEW_COOLDOWN_MILLIS = 2000L
    }
}

// 単一選択のダイアログ。ダイアログの種類ごとにAlertDialogを書き分けないための共通実装。
// disabledOptionsは、まだ実装していない選択肢を選べないまま表示する(DISMISS_METHODの「スワイプ」用)。
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    disabledOptions: Set<T> = emptySet(),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    val enabled = value !in disabledOptions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = enabled) { onSelect(value) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) }, enabled = enabled)
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

