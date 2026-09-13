package com.marutyan.termalarm.ui.alarmedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.ChallengeLevel
import com.marutyan.termalarm.domain.ChallengeTiming
import com.marutyan.termalarm.ui.theme.customColors

/**
 * 解除チャレンジの出題タイミングと難易度を設定するポップアップComposable。
 * 「なし」「ターム終了時だけ」「毎回」のラジオボタン選択と、「Easy」「Hard」の難易度指定を提供する。
 */
@Composable
fun ChallengePickerDialog(
    initialTiming: ChallengeTiming,
    initialLevel: ChallengeLevel,
    onDismiss: () -> Unit,
    onConfirm: (ChallengeTiming, ChallengeLevel) -> Unit,
) {
    var timing by rememberSaveable { mutableStateOf(initialTiming) }
    var level by rememberSaveable { mutableStateOf(initialLevel) }

    val options = listOf(
        Triple(
            ChallengeTiming.NEVER,
            stringResource(R.string.challenge_timing_never),
            stringResource(R.string.challenge_timing_never_desc),
        ),
        Triple(
            ChallengeTiming.END_ONLY,
            stringResource(R.string.challenge_timing_end_only),
            stringResource(R.string.challenge_timing_end_only_desc),
        ),
        Triple(
            ChallengeTiming.EVERY_TIME,
            stringResource(R.string.challenge_timing_every_time),
            stringResource(R.string.challenge_timing_every_time_desc),
        ),
    )

    Dialog(
        onDismissRequest = {
            onConfirm(timing, level)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.customColors.scaleUpcoming,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 22.dp, bottom = 12.dp),
                ) {
                    // 見出し（アイコン + 「問題」）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
                    ) {
                        TermQuestionCircleIcon(color = MaterialTheme.customColors.subtleText)
                        Text(
                            text = stringResource(R.string.term_edit_challenge_label),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // 選択肢（なし、ターム終了時だけ、毎回）
                    options.forEach { (optionTiming, label, note) ->
                        val isSelected = timing == optionTiming
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    role = Role.RadioButton,
                                    onClick = { timing = optionTiming },
                                )
                                .padding(horizontal = 22.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(15.dp),
                        ) {
                            // ラジオボタンの円
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.customColors.scaleUpcoming)
                                    .border(
                                        width = if (isSelected) 6.dp else 2.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape,
                                    ),
                            )

                            // タイトルと注記
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 15.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = note,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // 区切り線
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 16.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline),
                    )

                    // 難易度（Easy / Hard）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 22.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.challenge_difficulty),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            // Easy
                            val isEasy = level == ChallengeLevel.EASY
                            Box(
                                modifier = Modifier
                                    .size(width = 84.dp, height = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isEasy) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isEasy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = { level = ChallengeLevel.EASY },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.challenge_level_easy),
                                    fontSize = 14.sp,
                                    color = if (isEasy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // Hard
                            val isHard = level == ChallengeLevel.HARD
                            Box(
                                modifier = Modifier
                                    .size(width = 84.dp, height = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isHard) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isHard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = { level = ChallengeLevel.HARD },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.challenge_level_hard),
                                    fontSize = 14.sp,
                                    color = if (isHard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // 難易度説明
                    Text(
                        text = stringResource(R.string.challenge_hard_note),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 9.dp),
                    )

                    // ボタン行（キャンセル・決定）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // キャンセルボタン（元の値のまま閉じる）
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = onDismiss,
                                )
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // 決定ボタン（変更を確定して閉じる）
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        onConfirm(timing, level)
                                    },
                                )
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.decide),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
