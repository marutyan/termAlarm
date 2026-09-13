package com.marutyan.termalarm.ui.alarmedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.R

/**
 * アラームの編集画面のうち、設定を並べる部分。
 * 画面本体が長くなりすぎたため、行を並べるだけの部品をこちらへ分けている。
 */

// ラベルの設定カード
@Composable
fun GeneralSettingsSection(
    label: String,
    onLabelClick: () -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        SettingsRow(iconRes = R.drawable.ic_label, title = stringResource(R.string.label_title), value = label.ifBlank { stringResource(R.string.label_placeholder) }, onClick = onLabelClick)
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
