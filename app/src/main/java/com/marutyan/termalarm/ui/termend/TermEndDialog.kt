package com.marutyan.termalarm.ui.termend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import com.marutyan.termalarm.ui.alarmedit.TermEndIcon
import com.marutyan.termalarm.ui.common.formatClockMinutes
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily
import java.time.ZonedDateTime
import com.marutyan.termalarm.domain.challengeQuestionCount
import kotlinx.coroutines.launch

/**
 * 当日分のターム鳴動を終了する確認ポップアップComposable。
 * 対象タームの時刻範囲と残り回数を表示し、出題設定時はミニゲームへ遷移、非出題時は直接AlarmRepository.endTodaySessionを実行する。
 */
@Composable
fun TermEndDialog(
    schedule: AlarmSchedule,
    repository: AlarmRepository,
    now: ZonedDateTime,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onStartChallenge: (Long) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val remainingCount = remember(schedule, now) {
        remainingOccurrenceCount(schedule, now)
    }
    val timePattern = remember { com.marutyan.termalarm.ui.common.clockTimePattern(false) }
    val timeRangeText = remember(schedule, timePattern) {
        val start = formatClockMinutes(schedule.startMinutes, timePattern)
        val end = formatClockMinutes(schedule.endMinutes, timePattern)
        "$start \u2013 $end"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier
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
                    modifier = Modifier.padding(start = 22.dp, top = 24.dp, end = 22.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 見出し（アイコン + 「タームを終了する」）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = TermEndIcon,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.customColors.subtleText,
                        )
                        Text(
                            text = stringResource(R.string.term_end_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // ターム情報カード（時刻範囲 + 残り回数）
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = timeRangeText,
                                fontSize = 17.sp,
                                fontFamily = ibmPlexMonoFontFamily(400),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.term_end_remaining_note, remainingCount),
                                fontSize = 12.5.sp,
                                color = MaterialTheme.customColors.subtleText,
                            )
                        }
                    }

                    // 説明文
                    Text(
                        text = stringResource(R.string.term_end_description),
                        fontSize = 12.5.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // ボタン行（やめる・終了する）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // やめるボタン
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
                                text = stringResource(R.string.term_end_cancel),
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // 終了するボタン
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = {
                                        val questionCount = challengeQuestionCount(schedule, 1.0)
                                        if (questionCount > 0) {
                                            onStartChallenge(schedule.id)
                                            onDismiss()
                                        } else {
                                            coroutineScope.launch {
                                                repository.endTodaySession(schedule.id, now)
                                                onDismiss()
                                            }
                                        }
                                    },
                                )
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.term_end_confirm),
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
