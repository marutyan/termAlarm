package com.marutyan.termalarm.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.customColors

/**
 * 画面上部の帯に配置されるオーバーフローメニュー。「設定」「プライバシー」「このアプリについて」の3項目を表示する。
 * 三点アイコンのタップでドロップダウンを展開し、選択された画面への遷移イベントを親へ通知するために用いる。
 */
@Composable
fun TermAlarmOverflowMenu(
    onOpenSettings: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.menu_more),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.customColors.subtleText,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_privacy)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_shield), contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenPrivacyPolicy()
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_about)) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenAbout()
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
        }
    }
}
