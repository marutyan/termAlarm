package com.marutyan.termalarm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.GameType
import com.marutyan.termalarm.ui.theme.customColors

/**
 * 出題するミニゲーム一覧画面に表示する各項目の定義情報。
 * GameType、表示名リソース、短い説明リソース、加速度センサー必須フラグを保持する。
 */
private data class GameListItemInfo(
    val type: GameType,
    val nameRes: Int,
    val noteRes: Int,
    val requiresSensor: Boolean,
)

private val GAME_ITEMS = listOf(
    GameListItemInfo(GameType.ARITHMETIC, R.string.game_kind_arithmetic, R.string.game_note_arithmetic, false),
    GameListItemInfo(GameType.SEQUENTIAL_TAP, R.string.game_kind_sequential_tap, R.string.game_note_sequential_tap, false),
    GameListItemInfo(GameType.TRANSCRIBE, R.string.game_kind_transcribe, R.string.game_note_transcribe, false),
    GameListItemInfo(GameType.MIRROR_TEXT, R.string.game_kind_mirror_text, R.string.game_note_mirror_text, false),
    GameListItemInfo(GameType.SEQUENCE_RECALL, R.string.game_kind_sequence_recall, R.string.game_note_sequence_recall, false),
    GameListItemInfo(GameType.MEMORY_PAIRS, R.string.game_kind_memory_pairs, R.string.game_note_memory_pairs, false),
    GameListItemInfo(GameType.COUNT_SHAPES, R.string.game_kind_count_shapes, R.string.game_note_count_shapes, false),
    GameListItemInfo(GameType.COLOR_WORD, R.string.game_kind_color_word, R.string.game_note_color_word, false),
    GameListItemInfo(GameType.SHAKE_DEVICE, R.string.game_kind_shake_device, R.string.game_note_shake_device, true),
    GameListItemInfo(GameType.WALK, R.string.game_kind_walk, R.string.game_note_walk, true),
)

/**
 * 出題するミニゲームを選択する画面。design/GameList.dc.htmlの設計に基づき、
 * 10種類のミニゲームを並べてチェックボックスで選択・解除を提供する。
 * 加速度センサーの有無に応じた選択可否判定と、最後の1つを解除させない制約を遵守する。
 */
@Composable
fun GameListScreen(
    viewModel: SettingsViewModel,
    hasShakeSensor: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val enabledGames = settings.enabledGames
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = statusBarTop + 24.dp,
                start = 18.dp,
                end = 18.dp,
                bottom = 32.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 戻るボタンと見出し「ミニゲーム」
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.customColors.subtleText,
                )
            }
            Text(
                text = stringResource(R.string.game_list_title),
                style = TextStyle(
                    fontFamily = com.marutyan.termalarm.ui.theme.HeadlineStyle.fontFamily,
                    fontWeight = FontWeight.W300,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        // 説明文
        Text(
            text = stringResource(R.string.game_list_subtitle),
            style = TextStyle(
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.customColors.subtleText,
            ),
        )

        // ゲームカード一覧
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GAME_ITEMS.forEachIndexed { index, item ->
                    val isChecked = item.type in enabledGames
                    val isSensorAvailable = !item.requiresSensor || hasShakeSensor
                    val isLastEnabled = isChecked && enabledGames.size == 1
                    val isRowClickable = isSensorAvailable && (!isLastEnabled || !isChecked)

                    GameItemRow(
                        name = stringResource(item.nameRes),
                        note = stringResource(item.noteRes),
                        isChecked = isChecked,
                        isEnabled = isSensorAvailable,
                        isLastOne = isLastEnabled,
                        isLastRow = index == GAME_ITEMS.size - 1,
                        onClick = {
                            if (isRowClickable) {
                                viewModel.toggleGame(item.type)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * ミニゲーム選択画面の1行分の項目コンポーネント。
 * チェックボックス、ゲーム名、短い補足説明を描画し、タップでトグル操作を呼び出す。
 */
@Composable
private fun GameItemRow(
    name: String,
    note: String,
    isChecked: Boolean,
    isEnabled: Boolean,
    isLastOne: Boolean,
    isLastRow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dividerColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val subtleTextColor = MaterialTheme.customColors.subtleText

    val textColor = when {
        !isEnabled -> subtleTextColor.copy(alpha = 0.5f)
        isChecked -> onSurfaceColor
        else -> subtleTextColor
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(
                enabled = isEnabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .drawBehind {
                if (!isLastRow) {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = dividerColor,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height - strokeWidth / 2f),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height - strokeWidth / 2f),
                        strokeWidth = strokeWidth,
                    )
                }
            }
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        // チェックボックス
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    color = if (isChecked && isEnabled) primaryColor else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                )
                .border(
                    width = if (isChecked && isEnabled) 1.dp else 2.dp,
                    color = if (isChecked && isEnabled) primaryColor else if (isEnabled) outlineColor else outlineColor.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isChecked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isEnabled) surfaceColor else subtleTextColor.copy(alpha = 0.5f),
                )
            }
        }

        // ゲーム名
        Text(
            text = name,
            style = TextStyle(
                fontSize = 14.5.sp,
                color = textColor,
            ),
            modifier = Modifier.weight(1f),
        )

        // 補足説明
        Text(
            text = note,
            style = TextStyle(
                fontSize = 13.sp,
                color = if (isEnabled) subtleTextColor else subtleTextColor.copy(alpha = 0.4f),
            ),
        )
    }
}
