package com.marutyan.termalarm.ui.settings

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.alarm.SoundFadeIn
import com.marutyan.termalarm.domain.AlarmDismissMethod
import com.marutyan.termalarm.domain.ClockDisplayMode
import com.marutyan.termalarm.domain.VolumeButtonAction
import com.marutyan.termalarm.domain.WeekStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 設定の値を、画面へ出す言葉へ変える。
 * 設定画面が長くなりすぎたため、表示のための変換だけをこちらへ分けている。
 */

@Composable
internal fun dismissMethodLabel(method: AlarmDismissMethod): String = when (method) {
    AlarmDismissMethod.TAP -> stringResource(R.string.settings_dismiss_method_tap)
    // 鳴動画面のスワイプ操作は未実装のため、選んでも何も変わらないことが伝わる表記にする
    AlarmDismissMethod.SWIPE -> stringResource(R.string.settings_dismiss_method_swipe_unavailable)
}

@Composable
internal fun volumeButtonLabel(action: VolumeButtonAction): String = when (action) {
    VolumeButtonAction.ADJUST_VOLUME -> stringResource(R.string.settings_volume_button_adjust)
    VolumeButtonAction.SNOOZE -> stringResource(R.string.settings_volume_button_snooze)
    VolumeButtonAction.DISMISS -> stringResource(R.string.settings_volume_button_dismiss)
}

@Composable
internal fun weekStartLabel(weekStart: WeekStart): String = when (weekStart) {
    WeekStart.SUNDAY -> stringResource(R.string.settings_week_start_sunday)
    WeekStart.MONDAY -> stringResource(R.string.settings_week_start_monday)
}

@Composable
internal fun clockStyleLabel(mode: ClockDisplayMode): String = when (mode) {
    ClockDisplayMode.ANALOG -> stringResource(R.string.clock_display_mode_analog)
    ClockDisplayMode.DIGITAL -> stringResource(R.string.clock_display_mode_digital)
}

// 秒数の表示用整形。0は「なし」、小数を含む場合はそのまま(1.5秒)、整数なら小数点を出さない(5秒)
@Composable
internal fun formatSeconds(seconds: Number): String {
    val value = seconds.toFloat()
    if (value == 0f) return stringResource(R.string.settings_fade_in_off)
    val text = if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
    return stringResource(R.string.settings_seconds_format, text)
}

// 選択中のUriからタイトルを取り出す。未設定・取得失敗時は既定の音の表記にする(AlarmEditScreenと同じ方針)
internal fun timerSoundLabel(context: Context, uriString: String?): String {
    val defaultLabel = context.getString(R.string.sound_default)
    val uri = uriString?.let(Uri::parse) ?: return defaultLabel
    return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: defaultLabel
}
