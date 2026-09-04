package com.marutyan.termalarm.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * 各タブ共通の「⋮」メニュー。純正時計アプリに合わせた構成で「設定」「プライバシー ポリシー」「ライセンス」の3項目とアイコンを表示する。
 * 各タブ画面のTopAppBarのアクション領域に配置され、選択された画面への遷移イベントを親へ通知する。
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
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
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
                text = { Text(stringResource(R.string.menu_privacy_policy)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_shield), contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenPrivacyPolicy()
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_license)) },
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
