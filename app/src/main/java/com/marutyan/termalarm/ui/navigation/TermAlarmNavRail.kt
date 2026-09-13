package com.marutyan.termalarm.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.ui.theme.customColors

/**
 * 画面左端に配置される幅54dpの縦ナビゲーションバー。
 * 各機能画面への迅速な切り替えを提供し、現在の選択項目を強調するために用いる。
 */
@Composable
fun TermAlarmNavRail(
    selectedItem: NavItem,
    onSelectItem: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .width(54.dp)
            .fillMaxHeight()
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = outlineVariantColor,
                    start = Offset(size.width - strokeWidth / 2f, 0f),
                    end = Offset(size.width - strokeWidth / 2f, size.height),
                    strokeWidth = strokeWidth,
                )
            }
            .padding(top = 71.dp, bottom = 19.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val topItems = listOf(
            NavItem.TERMS,
            NavItem.STANDARD_ALARM,
            NavItem.RECORD,
            NavItem.TIMER,
            NavItem.STOPWATCH,
        )

        topItems.forEachIndexed { index, item ->
            if (index > 0) {
                // 48dpのタップ枠同士の間隔を8dpとすることで、内部の34dp視覚要素の間隔を22dpに維持する
                Spacer(modifier = Modifier.height(8.dp))
            }
            NavRailItemButton(
                item = item,
                isSelected = item == selectedItem,
                onClick = { onSelectItem(item) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        NavRailItemButton(
            item = NavItem.SETTINGS,
            isSelected = NavItem.SETTINGS == selectedItem,
            onClick = { onSelectItem(NavItem.SETTINGS) },
        )
    }
}

/**
 * 縦ナビゲーションバーに配置される個別項目の操作ボタン。
 * 48dp角以上のタップ判定を確保しつつ34dp角のアイコン意匠を描画するために用いる。
 */
@Composable
private fun NavRailItemButton(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(3.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.labelRes),
                    modifier = Modifier.size(item.iconSize),
                    tint = MaterialTheme.colorScheme.surface,
                )
            }
        } else {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.labelRes),
                    modifier = Modifier.size(item.iconSize),
                    tint = MaterialTheme.customColors.subtleText,
                )
            }
        }
    }
}
