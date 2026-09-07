package com.marutyan.termalarm.ui.alarmedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.R

/**
 * アラームの編集画面のうち、設定を並べる部分。
 * 画面本体が長くなりすぎたため、行を並べるだけの部品をこちらへ分けている。
 */

// ラベル・アラーム音・バイブレーションの設定カード
@Composable
fun GeneralSettingsSection(
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
fun SettingsRow(iconRes: Int, title: String, value: String, onClick: () -> Unit) {
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
fun DifficultToStopSection(
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
fun ToggleSettingRow(
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
