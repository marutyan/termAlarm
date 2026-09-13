package com.marutyan.termalarm.ui.settings

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.AppTheme
import com.marutyan.termalarm.ui.theme.BlackSurface
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.LightSurface
import com.marutyan.termalarm.ui.theme.NavySurface
import com.marutyan.termalarm.ui.theme.customColors

/**
 * 設定画面で表示するポップアップピッカーの対象項目種別。
 * 徐々に音量を上げる、消音までの時間、二度寝チェックまで、配色の選択に用いる。
 */
private enum class SettingsPickerType {
    FADE_IN,
    SILENCE_AFTER,
    WAKE_CHECK,
    THEME,
}

/**
 * 設定画面。design/Settings.dc.htmlの設計に基づき、アイコン・項目名・値の行を配置し、
 * アラーム・見た目・このアプリの3つのまとまりに整理して表示する。
 * 各項目タップ時にdesign/SettingsPicker.dc.htmlに基づく中央ポップアップを開いて即時選択を提供する。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onOpenGameList: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var currentPicker by rememberSaveable { mutableStateOf<SettingsPickerType?>(null) }

    // システムのアラーム音選択ピッカー
    val soundPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.let { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
        }
        viewModel.setAlarmSoundUri(uri?.toString())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = statusBarTop + 74.dp,
                start = 18.dp,
                end = 18.dp,
                bottom = 32.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 見出し「設定」
        Text(
            text = stringResource(R.string.settings_title),
            style = TextStyle(
                fontFamily = com.marutyan.termalarm.ui.theme.HeadlineStyle.fontFamily,
                fontWeight = FontWeight.W300,
                fontSize = 28.sp,
                letterSpacing = (-0.01).em,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // 1. アラーム セクション
        SettingsSectionHeader(text = stringResource(R.string.settings_section_alarm))
        SettingsCard {
            // 音
            val currentSoundTitle = soundLabel(context, settings.alarmSoundUri)
            SettingsRow(
                icon = SettingsSoundIcon,
                title = stringResource(R.string.settings_sound_item_title),
                value = currentSoundTitle,
                isLast = false,
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        settings.alarmSoundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it.toUri()) }
                    }
                    soundPickerLauncher.launch(intent)
                },
            )

            // バイブレーション
            SettingsSwitchRow(
                icon = SettingsVibrationIcon,
                title = stringResource(R.string.vibration_title),
                isChecked = settings.vibration,
                isLast = false,
                onCheckedChange = { viewModel.setVibration(it) },
            )

            // 徐々に音量を上げる
            val fadeInText = if (settings.fadeInSeconds == 0) {
                stringResource(R.string.settings_fade_in_off)
            } else {
                stringResource(R.string.settings_seconds_format, settings.fadeInSeconds.toString())
            }
            SettingsRow(
                icon = SettingsFadeInIcon,
                title = stringResource(R.string.settings_fade_in_title),
                value = fadeInText,
                isLast = false,
                onClick = { currentPicker = SettingsPickerType.FADE_IN },
            )

            // 消音までの時間
            val silenceText = settings.silenceAfterMinutes?.let {
                stringResource(R.string.settings_minutes_format, it)
            } ?: stringResource(R.string.settings_picker_none)
            SettingsRow(
                icon = SettingsSilenceAfterIcon,
                title = stringResource(R.string.settings_silence_after_item_title),
                value = silenceText,
                isLast = false,
                onClick = { currentPicker = SettingsPickerType.SILENCE_AFTER },
            )

            // 二度寝チェックまで
            val wakeCheckText = stringResource(R.string.settings_minutes_format, settings.wakeCheckMinutes)
            SettingsRow(
                icon = SettingsWakeCheckIcon,
                title = stringResource(R.string.settings_wake_check_item_title),
                value = wakeCheckText,
                isLast = false,
                onClick = { currentPicker = SettingsPickerType.WAKE_CHECK },
            )

            // ミニゲーム
            val gamesCountText = stringResource(R.string.settings_mini_games_count_format, settings.enabledGames.size)
            SettingsRow(
                icon = SettingsMiniGamesIcon,
                title = stringResource(R.string.settings_mini_games_item_title),
                value = gamesCountText,
                isLast = true,
                onClick = onOpenGameList,
            )
        }

        // 2. 見た目 セクション
        SettingsSectionHeader(text = stringResource(R.string.settings_section_appearance), topPadding = 10.dp)
        SettingsCard {
            SettingsThemeRow(
                selectedTheme = settings.theme,
                onSelectTheme = { viewModel.setTheme(it) },
                onClickRow = { currentPicker = SettingsPickerType.THEME },
            )
        }

        // 3. このアプリ セクション
        SettingsSectionHeader(text = stringResource(R.string.settings_section_about_app), topPadding = 10.dp)
        SettingsCard {
            // プライバシー
            SettingsRow(
                icon = SettingsPrivacyIcon,
                title = stringResource(R.string.settings_privacy_item_title),
                value = null,
                isLast = false,
                onClick = onOpenPrivacyPolicy,
            )

            // このアプリについて
            SettingsRow(
                icon = SettingsAboutIcon,
                title = stringResource(R.string.settings_about_item_title),
                value = stringResource(R.string.settings_app_version_value),
                isMonospaceValue = true,
                isLast = true,
                onClick = onOpenAbout,
            )
        }
    }

    // 各種設定選択の中央ポップアップ
    currentPicker?.let { picker ->
        when (picker) {
            SettingsPickerType.FADE_IN -> {
                val options = listOf(0, 5, 10, 15, 20, 25, 30).map { sec ->
                    val label = if (sec == 0) stringResource(R.string.settings_fade_in_off) else stringResource(R.string.settings_seconds_format, sec.toString())
                    PickerOption(value = sec, label = label)
                }
                SettingsPickerPopup(
                    title = stringResource(R.string.settings_fade_in_title),
                    icon = SettingsFadeInIcon,
                    options = options,
                    selectedValue = settings.fadeInSeconds,
                    onSelect = {
                        viewModel.setFadeInSeconds(it)
                        currentPicker = null
                    },
                    onDismiss = { currentPicker = null },
                )
            }
            SettingsPickerType.SILENCE_AFTER -> {
                val noneLabel = stringResource(R.string.settings_picker_none)
                val options = listOf<Int?>(null, 1, 5, 10, 15).map { min ->
                    val label = min?.let { stringResource(R.string.settings_minutes_format, it) } ?: noneLabel
                    PickerOption(value = min, label = label)
                }
                SettingsPickerPopup(
                    title = stringResource(R.string.settings_silence_after_item_title),
                    icon = SettingsSilenceAfterIcon,
                    options = options,
                    selectedValue = settings.silenceAfterMinutes,
                    onSelect = {
                        viewModel.setSilenceAfterMinutes(it)
                        currentPicker = null
                    },
                    onDismiss = { currentPicker = null },
                )
            }
            SettingsPickerType.WAKE_CHECK -> {
                val options = listOf(1, 3, 5, 10, 15).map { min ->
                    PickerOption(value = min, label = stringResource(R.string.settings_minutes_format, min))
                }
                SettingsPickerPopup(
                    title = stringResource(R.string.settings_wake_check_item_title),
                    icon = SettingsWakeCheckIcon,
                    options = options,
                    selectedValue = settings.wakeCheckMinutes,
                    onSelect = {
                        viewModel.setWakeCheckMinutes(it)
                        currentPicker = null
                    },
                    onDismiss = { currentPicker = null },
                )
            }
            SettingsPickerType.THEME -> {
                val isDynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val themeOptions = buildList {
                    add(PickerOption(AppTheme.NAVY, stringResource(R.string.settings_theme_navy)))
                    add(PickerOption(AppTheme.LIGHT, stringResource(R.string.settings_theme_light)))
                    add(PickerOption(AppTheme.BLACK, stringResource(R.string.settings_theme_black)))
                    if (isDynamicAvailable) {
                        add(PickerOption(AppTheme.DYNAMIC, stringResource(R.string.settings_theme_dynamic)))
                    }
                }
                SettingsPickerPopup(
                    title = stringResource(R.string.settings_theme_item_title),
                    icon = SettingsThemeIcon,
                    options = themeOptions,
                    selectedValue = settings.theme,
                    onSelect = {
                        viewModel.setTheme(it)
                        currentPicker = null
                    },
                    onDismiss = { currentPicker = null },
                )
            }
        }
    }
}

/**
 * 設定画面のセクション見出しテキスト。
 * 等幅フォントで控えめな大文字トラッキングを設定して表示する。
 */
@Composable
private fun SettingsSectionHeader(
    text: String,
    topPadding: androidx.compose.ui.unit.Dp = 4.dp,
) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = IbmPlexMono,
            fontSize = 11.sp,
            letterSpacing = 0.15.em,
            color = MaterialTheme.customColors.subtleText,
        ),
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = topPadding, bottom = 2.dp),
    )
}

/**
 * 各セクションの項目を束ねる角丸カードコンテナ。
 * カード背景と角丸14dpを適用して内部要素をグループ化する。
 */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * アイコン、項目名、値、矢印アイコンからなる設定の基本行。
 * 44dp以上の操作領域を保ち、タップ時にダイアログや遷移アクションを呼ぶ。
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String?,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMonospaceValue: Boolean = false,
) {
    val dividerColor = MaterialTheme.colorScheme.background
    val subtleTextColor = MaterialTheme.customColors.subtleText
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .drawBehind {
                if (!isLast) {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = dividerColor,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height - strokeWidth / 2f),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height - strokeWidth / 2f),
                        strokeWidth = strokeWidth,
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 左アイコン
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = subtleTextColor,
        )

        // 項目名
        Text(
            text = title,
            style = TextStyle(fontSize = 15.sp, color = onSurfaceColor),
            modifier = Modifier.weight(1f),
        )

        // 値
        if (value != null) {
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = if (isMonospaceValue) IbmPlexMono else null,
                    fontSize = 14.sp,
                    color = subtleTextColor,
                ),
            )
        }

        // 矢印chevron
        ChevronRightIcon()
    }
}

/**
 * バイブレーション等のON/OFFを切り替えるスイッチ行コンポーネント。
 * design/Settings.dc.html記載の44dp×26dp角丸トグルスイッチ意匠を再現する。
 */
@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    isChecked: Boolean,
    isLast: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dividerColor = MaterialTheme.colorScheme.background
    val subtleTextColor = MaterialTheme.customColors.subtleText
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = { onCheckedChange(!isChecked) },
            )
            .drawBehind {
                if (!isLast) {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = dividerColor,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height - strokeWidth / 2f),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height - strokeWidth / 2f),
                        strokeWidth = strokeWidth,
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = subtleTextColor,
        )

        Text(
            text = title,
            style = TextStyle(fontSize = 15.sp, color = onSurfaceColor),
            modifier = Modifier.weight(1f),
        )

        // カスタムトグルスイッチ (44dp x 26dp, 角丸13dp)
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(26.dp)
                .background(
                    color = if (isChecked) primaryColor else outlineVariantColor,
                    shape = RoundedCornerShape(13.dp),
                )
                .padding(3.dp),
            contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = if (isChecked) surfaceColor else subtleTextColor,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * 配色テーマの選択行コンポーネント。
 * 丸プレビューを並べて表示し、直接タップまたは行タップで切り替えを提供する。
 */
@Composable
private fun SettingsThemeRow(
    selectedTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    onClickRow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtleTextColor = MaterialTheme.customColors.subtleText
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val isDynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClickRow,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = SettingsThemeIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = subtleTextColor,
        )

        Text(
            text = stringResource(R.string.settings_theme_item_title),
            style = TextStyle(fontSize = 15.sp, color = onSurfaceColor),
            modifier = Modifier.weight(1f),
        )

        // 配色の丸プレビュー
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeCircle(
                color = NavySurface,
                isSelected = selectedTheme == AppTheme.NAVY,
                onClick = { onSelectTheme(AppTheme.NAVY) },
            )
            ThemeCircle(
                color = BlackSurface,
                isSelected = selectedTheme == AppTheme.BLACK,
                onClick = { onSelectTheme(AppTheme.BLACK) },
            )
            ThemeCircle(
                color = LightSurface,
                isSelected = selectedTheme == AppTheme.LIGHT,
                onClick = { onSelectTheme(AppTheme.LIGHT) },
            )
            if (isDynamicAvailable) {
                DynamicThemeCircle(
                    isSelected = selectedTheme == AppTheme.DYNAMIC,
                    onClick = { onSelectTheme(AppTheme.DYNAMIC) },
                )
            }
        }

        ChevronRightIcon()
    }
}

/**
 * 単色配色テーマのプレビュー円。
 * 22dpの円形で、選択時は主役色2dpの枠線で強調する。
 */
@Composable
private fun ThemeCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .size(22.dp)
            .background(color, CircleShape)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) primaryColor else outlineColor,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 16.dp),
                onClick = onClick,
            ),
    )
}

/**
 * 端末の色（DYNAMIC）用のグラデーションプレビュー円。
 * 壁紙連携を連想させる3色グラデーションを描画する。
 */
@Composable
private fun DynamicThemeCircle(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val dynamicBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFB79CE8), Color(0xFFE8A0B4), Color(0xFFF0C48A)),
    )

    Box(
        modifier = modifier
            .size(22.dp)
            .background(dynamicBrush, CircleShape)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) primaryColor else outlineColor,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 16.dp),
                onClick = onClick,
            ),
    )
}

/**
 * 設定行の右端に配置されるchevron-right意匠。
 */
@Composable
private fun ChevronRightIcon() {
    Icon(
        imageVector = SettingsChevronRightIcon,
        contentDescription = null,
        modifier = Modifier.size(17.dp),
        tint = MaterialTheme.customColors.subtleText,
    )
}

/**
 * ポップアップピッカーの個別選択肢データ。
 */
data class PickerOption<T>(
    val value: T,
    val label: String,
)

/**
 * design/SettingsPicker.dc.htmlの設計に基づく中央ポップアップダイアログ。
 * アイコン、タイトル、ラジオボタン意匠の選択肢リスト、キャンセルボタンを描画し、選択時に即座に閉じる。
 */
@Composable
fun <T> SettingsPickerPopup(
    title: String,
    icon: ImageVector,
    options: List<PickerOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 8.dp),
            ) {
                // タイトル行
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.customColors.subtleText,
                    )
                    Text(
                        text = title,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 選択肢一覧
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { option ->
                        val isSelected = option.value == selectedValue
                        PickerOptionRow(
                            label = option.label,
                            isSelected = isSelected,
                            onClick = { onSelect(option.value) },
                        )
                    }
                }

                // キャンセルボタン
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = onDismiss,
                            )
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = TextStyle(
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * ピッカーポップアップの1選択肢行コンポーネント。
 * ラジオボタン風の22dp円とラベルを配置し、タップで選択を行う。
 */
@Composable
private fun PickerOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        // ラジオボタン意匠 (22dp)
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color.Transparent, CircleShape)
                .border(
                    width = if (isSelected) 6.dp else 2.dp,
                    color = if (isSelected) primaryColor else outlineColor,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(surfaceContainerColor, CircleShape),
                )
            }
        }

        // ラベル
        Text(
            text = label,
            style = TextStyle(
                fontSize = 15.sp,
                color = if (isSelected) onSurfaceColor else onSurfaceVariantColor,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}
