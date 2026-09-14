package com.marutyan.termalarm.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.ui.theme.customColors

/**
 * 画面最下部に配置される横並びのナビゲーション帯。
 * 主要5画面（ターム、アラーム、記録、タイマー、ストップ）への切り替え機能を提供し、現在選択されている項目を主役色で強調表示する。
 */
@Composable
fun TermAlarmBottomBar(
    selectedItem: NavItem,
    onSelectItem: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = navigationBarBottom),
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bottomBarItems = listOf(
                NavItem.TERMS,
                NavItem.STANDARD_ALARM,
                NavItem.RECORD,
                NavItem.TIMER,
                NavItem.STOPWATCH,
            )

            bottomBarItems.forEach { item ->
                BottomBarItem(
                    item = item,
                    isSelected = item == selectedItem,
                    onClick = { onSelectItem(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 下部ナビゲーション帯に配置される各機能の操作ボタン。
 * 幅5等分かつ高さ60dpのタップ領域を確保し、上部の23dpアイコンと下部の11spテキストで画面種別を表現する。
 */
@Composable
private fun BottomBarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val subtleTextColor = MaterialTheme.customColors.subtleText
    val contentColor = if (isSelected) primaryColor else subtleTextColor
    val label = stringResource(item.labelRes)

    Box(
        modifier = modifier
            .height(60.dp)
            .semantics {
                contentDescription = label
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(item.iconSize),
                tint = contentColor,
            )
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = contentColor,
            )
        }
    }
}
