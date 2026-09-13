package com.marutyan.termalarm.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.calculateOccurrenceOffsets
import com.marutyan.termalarm.domain.canEndTodaySession
import com.marutyan.termalarm.domain.nextTrigger
import com.marutyan.termalarm.domain.occurrenceCount
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import com.marutyan.termalarm.domain.scheduleSummary
import com.marutyan.termalarm.domain.sessionStartDate
import com.marutyan.termalarm.ui.common.clockTimePattern
import com.marutyan.termalarm.ui.common.formatClockMinutes
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 破線枠を描画するModifier拡張関数。
 * ターム追加カードなどの輪郭線を描画するために用いる。
 */
private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp,
): Modifier = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashLength.toPx(), gapLength.toPx()),
            0f,
        ),
    )
    val halfWidth = width.toPx() / 2f
    val r = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(halfWidth, halfWidth),
        size = Size(size.width - width.toPx(), size.height - width.toPx()),
        cornerRadius = CornerRadius(r, r),
        style = stroke,
    )
}

/**
 * ホーム画面の最上位Composable。
 * 11個の主要要素を上から順に配置し、進行中タームや登録件数に応じて適切に出し分ける。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddTerm: () -> Unit = {},
    onEditTerm: (Long) -> Unit = {},
    onEndTodayTerm: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val terms by viewModel.terms.collectAsStateWithLifecycle()
    val now = rememberCurrentSecond()

    // 進行中のタームを抽出する。有効かつ当日セッション終了が可能なものの中で次回鳴動が最も近いものを採用する。
    val activeSchedule = remember(terms, now) {
        terms.filter { it.enabled && canEndTodaySession(it, now) }
            .minByOrNull { schedule ->
                nextTrigger(schedule, now)?.toEpochSecond() ?: Long.MAX_VALUE
            }
    }
    val nextTriggerTime = activeSchedule?.let { nextTrigger(it, now) }

    // ステータスバーの下端から74dp空けるため、WindowInsets.statusBarsの高さを足す
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = statusBarTop + 74.dp, start = 18.dp, end = 18.dp, bottom = 26.dp),
    ) {
        // 1. 現在時刻。78sp、太さ200、等幅数字。秒を33spで右へ添える
        val timePattern = remember { clockTimePattern(false) }
        val currentTimeString = remember(now, timePattern) {
            now.format(DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()))
        }
        val currentSecondString = remember(now) {
            now.format(DateTimeFormatter.ofPattern("ss", Locale.getDefault()))
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = currentTimeString,
                style = TextStyle(
                    fontFamily = ibmPlexMonoFontFamily(200),
                    fontWeight = FontWeight.W200,
                    fontSize = 78.sp,
                    lineHeight = 70.sp,
                    fontFeatureSettings = "tnum",
                    letterSpacing = (-0.05).em,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = currentSecondString,
                style = TextStyle(
                    fontFamily = ibmPlexMonoFontFamily(200),
                    fontWeight = FontWeight.W200,
                    fontSize = 33.sp,
                    lineHeight = 35.sp,
                    fontFeatureSettings = "tnum",
                    letterSpacing = (-0.04).em,
                ),
                color = MaterialTheme.customColors.subtleText,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        // 2. 日付。14.5sp、薄い文字の色。左へ9dpずらす（等幅数字の余白のぶん）
        val dayLabel = dayShortLabel(now.dayOfWeek)
        val dateString = remember(now, dayLabel) {
            now.format(DateTimeFormatter.ofPattern("MM.dd ", Locale.getDefault())) + dayLabel
        }
        Text(
            text = dateString,
            style = TextStyle(
                fontFamily = IbmPlexMono,
                fontSize = 14.5.sp,
                color = MaterialTheme.customColors.subtleText,
            ),
            modifier = Modifier.padding(start = 9.dp),
        )

        // 3. 34dp空ける
        Spacer(modifier = Modifier.height(34.dp))

        // 進行中のタームがある場合のみ 4〜8 を表示する
        if (activeSchedule != null && nextTriggerTime != null) {
            val totalOccurrences = occurrenceCount(activeSchedule)
            val remainingOccurrences = remainingOccurrenceCount(activeSchedule, nextTriggerTime) + 1

            // 4. 「次の鳴動」のラベル。11sp、字間0.15em、薄い文字の色
            Text(
                text = stringResource(R.string.home_next_trigger_label),
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontSize = 11.sp,
                    letterSpacing = 0.15.em,
                    color = MaterialTheme.customColors.subtleText,
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. 次の鳴動時刻。44sp、太さ300、主役の色。右に「残り22回」を13sp
            val nextTimeString = remember(nextTriggerTime, timePattern) {
                nextTriggerTime.format(DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()))
            }

            FlowRow(
                verticalArrangement = Arrangement.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = nextTimeString,
                    style = TextStyle(
                        fontFamily = ibmPlexMonoFontFamily(300),
                        fontWeight = FontWeight.W300,
                        fontSize = 44.sp,
                        lineHeight = 44.sp,
                        fontFeatureSettings = "tnum",
                        letterSpacing = (-0.035).em,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = stringResource(R.string.home_remaining_count, remainingOccurrences),
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. 範囲と間隔。13.5sp、副次の文字の色
            val intervalSummary = remember(activeSchedule) {
                if (activeSchedule.startIntervalMinutes == activeSchedule.endIntervalMinutes) {
                    "${activeSchedule.startIntervalMinutes}分ごと"
                } else {
                    "${activeSchedule.startIntervalMinutes}〜${activeSchedule.endIntervalMinutes}分ごと"
                }
            }
            val rangeAndInterval = remember(activeSchedule, intervalSummary, timePattern) {
                val startStr = formatClockMinutes(activeSchedule.startMinutes, timePattern)
                val endStr = formatClockMinutes(activeSchedule.endMinutes, timePattern)
                "$startStr \u2013 $endStr \u00b7 $intervalSummary"
            }
            Text(
                text = rangeAndInterval,
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontSize = 13.5.sp,
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 7. 鳴動の目盛。高さ12dp。鳴り終わった回・いま鳴っている回・これからの回で色を分ける
            OccurrenceScale(
                schedule = activeSchedule,
                nextTriggerTime = nextTriggerTime,
                totalOccurrences = totalOccurrences,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 8. 「このタームを終了」。13.5sp、薄い文字の色。押せる高さ44dp
            val subtleTextColor = MaterialTheme.customColors.subtleText
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = { onEndTodayTerm(activeSchedule.id) },
                    ),
            ) {
                Canvas(modifier = Modifier.size(17.dp)) {
                    val strokeWidth = 1.7.dp.toPx()
                    val r = (size.minDimension - strokeWidth) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        color = subtleTextColor,
                        radius = r,
                        center = center,
                        style = Stroke(width = strokeWidth),
                    )
                    val scale = size.width / 24f
                    drawLine(
                        color = subtleTextColor,
                        start = Offset(8.5f * scale, 8.5f * scale),
                        end = Offset(15.5f * scale, 15.5f * scale),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
                Text(
                    text = stringResource(R.string.home_end_term),
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        color = subtleTextColor,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // 9. 「ターム」の小見出し（タームが1件以上ある場合のみ表示）
        if (terms.isNotEmpty()) {
            Text(
                text = stringResource(R.string.home_section_terms),
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontSize = 11.sp,
                    letterSpacing = 0.15.em,
                    color = MaterialTheme.customColors.subtleText,
                ),
                modifier = Modifier.padding(bottom = 11.dp),
            )
        }

        // 10. タームのカード一覧
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            terms.forEach { schedule ->
                TermCard(
                    schedule = schedule,
                    timePattern = timePattern,
                    onToggleEnabled = { enabled ->
                        viewModel.toggleEnabled(schedule, enabled)
                    },
                    onClick = { onEditTerm(schedule.id) },
                )
            }

            // 11. 「タームを追加」。高さ52dp、破線の枠
            AddTermButton(onClick = onAddTerm)
        }
    }
}

/**
 * 鳴動の進行状況を示す目盛コンポーネント。
 * 経過済み・現在・未到来の各スロットを色分けして水平方向に並べる。
 */
@Composable
private fun OccurrenceScale(
    schedule: AlarmSchedule,
    nextTriggerTime: ZonedDateTime,
    totalOccurrences: Int,
    modifier: Modifier = Modifier,
) {
    val offsets = remember(schedule) { calculateOccurrenceOffsets(schedule) }
    val sessionStart = remember(schedule, nextTriggerTime) { sessionStartDate(schedule, nextTriggerTime) }
    val sessionStartDateTime = remember(sessionStart, schedule) {
        LocalDateTime.of(sessionStart, LocalTime.MIDNIGHT).plusMinutes(schedule.startMinutes.toLong())
    }
    val nextElapsedMinutes = remember(sessionStartDateTime, nextTriggerTime) {
        Duration.between(sessionStartDateTime, nextTriggerTime.toLocalDateTime()).toMinutes().toInt()
    }

    val pastColor = MaterialTheme.customColors.scalePast
    val currentColor = MaterialTheme.colorScheme.primary
    val upcomingColor = MaterialTheme.customColors.scaleUpcoming

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until totalOccurrences) {
            val offset = offsets.getOrElse(i) { 0 }
            val slotColor = when {
                offset < nextElapsedMinutes -> pastColor
                offset == nextElapsedMinutes -> currentColor
                else -> upcomingColor
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(slotColor),
            )
        }
    }
}

/**
 * 個別のターム情報を表示するカードComposable。
 * 左端の帯、時刻範囲、曜日の丸、要約、有効無効スイッチを包含する。
 */
@Composable
private fun TermCard(
    schedule: AlarmSchedule,
    timePattern: String,
    onToggleEnabled: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val subtleTextColor = MaterialTheme.customColors.subtleText

    val accentColor = if (schedule.enabled) primaryColor else MaterialTheme.colorScheme.outline
    val timeColor = if (schedule.enabled) MaterialTheme.colorScheme.onSurface else subtleTextColor

    val startStr = remember(schedule.startMinutes, timePattern) {
        formatClockMinutes(schedule.startMinutes, timePattern)
    }
    val endStr = remember(schedule.endMinutes, timePattern) {
        formatClockMinutes(schedule.endMinutes, timePattern)
    }
    val timeRangeText = "$startStr \u2013 $endStr"
    val summaryText = remember(schedule) { scheduleSummary(schedule) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(surfaceContainer, RoundedCornerShape(3.dp))
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val half = strokeWidth / 2f
                val r = 3.dp.toPx()
                drawRoundRect(
                    color = outlineVariant,
                    topLeft = Offset(half, half),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = strokeWidth),
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(top = 14.dp, bottom = 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左端の4dpの帯
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = accentColor,
                    shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp),
                ),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 中央コンテンツ
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            // 時刻の範囲 19sp
            Text(
                text = timeRangeText,
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontSize = 19.sp,
                    lineHeight = 20.sp,
                    fontFeatureSettings = "tnum",
                    color = timeColor,
                ),
            )

            // 曜日の丸 26dp
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val daysOfWeek = remember {
                    listOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                        DayOfWeek.SATURDAY,
                        DayOfWeek.SUNDAY,
                    )
                }

                daysOfWeek.forEach { day ->
                    val isSelected = day in schedule.repeatDays
                    val circleBg = when {
                        isSelected && schedule.enabled -> primaryColor
                        isSelected && !schedule.enabled -> subtleTextColor
                        else -> Color.Transparent
                    }
                    val circleFg = when {
                        isSelected && schedule.enabled -> MaterialTheme.colorScheme.surface
                        isSelected && !schedule.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> subtleTextColor
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(circleBg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dayShortLabel(day),
                            style = TextStyle(
                                fontSize = 11.5.sp,
                                color = circleFg,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }

            // 間隔と回数 12sp
            Text(
                text = summaryText,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = subtleTextColor,
                ),
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 右端の切り替えスイッチ 40×24dp
        val switchDesc = stringResource(R.string.home_term_switch_description, timeRangeText)
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 48.dp)
                .semantics { contentDescription = switchDesc }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 24.dp),
                    onClick = { onToggleEnabled(!schedule.enabled) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 24.dp)
                    .background(
                        color = if (schedule.enabled) primaryColor else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(3.dp),
                contentAlignment = if (schedule.enabled) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            color = if (schedule.enabled) MaterialTheme.colorScheme.surface else subtleTextColor,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

/**
 * ターム追加操作を受け付ける破線枠ボタンComposable。
 */
@Composable
private fun AddTermButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val subtleTextColor = MaterialTheme.customColors.subtleText

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .dashedBorder(
                width = 1.dp,
                color = outlineColor,
                cornerRadius = 3.dp,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(15.dp)) {
            val strokeWidth = 1.8.dp.toPx()
            val halfW = size.width / 2f
            val halfH = size.height / 2f
            drawLine(
                color = subtleTextColor,
                start = Offset(halfW, 2.5.dp.toPx()),
                end = Offset(halfW, size.height - 2.5.dp.toPx()),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = subtleTextColor,
                start = Offset(2.5.dp.toPx(), halfH),
                end = Offset(size.width - 2.5.dp.toPx(), halfH),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = stringResource(R.string.home_add_term),
            style = TextStyle(
                fontSize = 13.sp,
                color = subtleTextColor,
            ),
        )
    }
}

/**
 * 指定した曜日に対応する1文字短縮ラベルを解決する関数。
 */
@Composable
private fun dayShortLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> stringResource(R.string.day_monday_short)
    DayOfWeek.TUESDAY -> stringResource(R.string.day_tuesday_short)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.day_wednesday_short)
    DayOfWeek.THURSDAY -> stringResource(R.string.day_thursday_short)
    DayOfWeek.FRIDAY -> stringResource(R.string.day_friday_short)
    DayOfWeek.SATURDAY -> stringResource(R.string.day_saturday_short)
    DayOfWeek.SUNDAY -> stringResource(R.string.day_sunday_short)
}
