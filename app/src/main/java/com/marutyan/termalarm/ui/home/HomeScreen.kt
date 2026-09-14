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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.marutyan.termalarm.ui.common.SCREEN_HORIZONTAL_PADDING
import com.marutyan.termalarm.ui.common.TOP_BAR_CONTENT_GAP
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.calculateOccurrenceOffsets
import com.marutyan.termalarm.domain.canEndTodaySession
import com.marutyan.termalarm.domain.nextTrigger
import com.marutyan.termalarm.domain.occurrenceCount
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import com.marutyan.termalarm.domain.scheduleSummary
import com.marutyan.termalarm.domain.sessionStartDate
import com.marutyan.termalarm.ui.common.TermAlarmTopBar
import com.marutyan.termalarm.ui.common.clockTimePattern
import com.marutyan.termalarm.ui.common.formatClockMinutes
import com.marutyan.termalarm.ui.common.formatRangeAndInterval
import com.marutyan.termalarm.ui.permission.ExactAlarmPermissionBanner
import com.marutyan.termalarm.ui.permission.NotificationPermissionBanner
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.homeNextTriggerExpandSpec
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily
import com.marutyan.termalarm.ui.timer.isReduceMotionEnabled
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
    modifier: Modifier = Modifier,
    onAddTerm: () -> Unit = {},
    onEditTerm: (Long) -> Unit = {},
    onEndTodayTerm: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val terms by viewModel.terms.collectAsStateWithLifecycle()

    // 追加した直後に一番下まで送られないよう、件数が増えたら上へ戻す。
    // 追加のボタンが一覧の下にあり、シートを閉じたときにそこへ焦点が戻るのが原因
    val scrollState = rememberScrollState()
    var previousTermCount by rememberSaveable { mutableIntStateOf(terms.size) }
    LaunchedEffect(terms.size) {
        if (terms.size > previousTermCount) scrollState.animateScrollTo(0)
        previousTermCount = terms.size
    }
    val now = rememberCurrentSecond()

    // 次に鳴動予定のタームを抽出する。有効で次回鳴動があるものの中で最も近いものを採用する。
    val activeSchedule = remember(terms, now) {
        terms.filter { it.enabled && nextTrigger(it, now) != null }
            .minByOrNull { schedule ->
                nextTrigger(schedule, now)?.toEpochSecond() ?: Long.MAX_VALUE
            }
    }
    val nextTriggerTime = activeSchedule?.let { nextTrigger(it, now) }


    // 画面上部の帯。スクロールの外へ置き、どの画面でも同じ位置に固定する
    Column(modifier = modifier.fillMaxSize()) {
        TermAlarmTopBar(
            onOpenSettings = onOpenSettings,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            onOpenAbout = onOpenAbout,
        )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = SCREEN_HORIZONTAL_PADDING, end = SCREEN_HORIZONTAL_PADDING, bottom = 24.dp),
    ) {

        Spacer(modifier = Modifier.height(TOP_BAR_CONTENT_GAP))

        // 1. 現在時刻。78sp、太さ200、等幅数字。秒を33spで右へ添える
        val timePattern = remember { clockTimePattern(false) }
        val currentTimeString = remember(now, timePattern) {
            now.format(DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()))
        }
        val currentSecondString = remember(now) {
            now.format(DateTimeFormatter.ofPattern("ss", Locale.getDefault()))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = currentTimeString,
                modifier = Modifier.alignByBaseline(),
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
                modifier = Modifier.alignByBaseline(),
                style = TextStyle(
                    fontFamily = ibmPlexMonoFontFamily(200),
                    fontWeight = FontWeight.W200,
                    fontSize = 33.sp,
                    lineHeight = 35.sp,
                    fontFeatureSettings = "tnum",
                    letterSpacing = (-0.04).em,
                ),
                color = MaterialTheme.customColors.subtleText,
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        // 2. 日付と曜日。17sp、薄い文字の色。左へ9dpずらす（等幅数字の余白のぶん）
        // 「ターム」「次の鳴動」の小見出し(13sp)よりはっきり大きくして、時刻の次に読ませる
        val dayLabel = dayShortLabel(now.dayOfWeek)
        val dateString = remember(now, dayLabel) {
            now.format(DateTimeFormatter.ofPattern("MM.dd ", Locale.getDefault())) + dayLabel
        }
        Text(
            text = dateString,
            style = TextStyle(
                fontFamily = IbmPlexMono,
                fontSize = 17.sp,
                color = MaterialTheme.customColors.subtleText,
            ),
            modifier = Modifier.padding(start = 9.dp),
        )

        // 権限バナー（通知権限・正確なアラーム権限）。足りないものがあれば縦に並べ、足りているときは余白も作らない
        NotificationPermissionBanner()
        ExactAlarmPermissionBanner()

        // 3. 34dp空ける
        Spacer(modifier = Modifier.height(34.dp))

    val context = LocalContext.current
    val reduceMotion = remember(context) { isReduceMotionEnabled(context) }

    val isNextTriggerVisible = activeSchedule != null && nextTriggerTime != null
    var lastActiveSchedule by remember { mutableStateOf<AlarmSchedule?>(null) }
    var lastNextTriggerTime by remember { mutableStateOf<ZonedDateTime?>(null) }
    if (activeSchedule != null && nextTriggerTime != null) {
        lastActiveSchedule = activeSchedule
        lastNextTriggerTime = nextTriggerTime
    }
    val currentSchedule = activeSchedule ?: lastActiveSchedule
    val currentNextTriggerTime = nextTriggerTime ?: lastNextTriggerTime

    // 進行中のタームがある場合のみ 4〜8 を表示する。出入り時は上から開閉して下の一覧を滑らかに動かす
    AnimatedVisibility(
        visible = isNextTriggerVisible,
        enter = if (reduceMotion) {
            EnterTransition.None
        } else {
            expandVertically(
                animationSpec = homeNextTriggerExpandSpec(),
                expandFrom = Alignment.Top,
            )
        },
        exit = if (reduceMotion) {
            ExitTransition.None
        } else {
            shrinkVertically(
                animationSpec = homeNextTriggerExpandSpec(),
                shrinkTowards = Alignment.Top,
            )
        },
    ) {
        if (currentSchedule != null && currentNextTriggerTime != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val totalOccurrences = occurrenceCount(currentSchedule)
                val remainingOccurrences = remainingOccurrenceCount(currentSchedule, currentNextTriggerTime) + 1

                // 4. 「次の鳴動」のラベル。字間0.15em、薄い文字の色
                Text(
                    text = stringResource(R.string.home_next_trigger_label),
                    style = TextStyle(
                        fontFamily = IbmPlexMono,
                        fontSize = 13.sp,
                        letterSpacing = 0.15.em,
                        color = MaterialTheme.customColors.subtleText,
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 5. 次の鳴動時刻。44sp、太さ300、主役の色。右に「残り22回」を13sp
                val nextTimeString = remember(currentNextTriggerTime, timePattern) {
                    currentNextTriggerTime.format(DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()))
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = nextTimeString,
                        modifier = Modifier.alignByBaseline(),
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
                        modifier = Modifier.alignByBaseline(),
                        style = TextStyle(
                            fontSize = 14.5.sp,
                            color = MaterialTheme.customColors.subtleText,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 6. 範囲と間隔。13.5sp、副次の文字の色
                val rangeAndInterval = remember(currentSchedule, timePattern) {
                    formatRangeAndInterval(currentSchedule, timePattern)
                }
                Text(
                    text = rangeAndInterval,
                    style = TextStyle(
                        fontFamily = IbmPlexMono,
                        fontSize = 15.sp,
                        fontFeatureSettings = "tnum",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 7. 鳴動の目盛。高さ12dp。鳴り終わった回・いま鳴っている回・これからの回で色を分ける
                OccurrenceScale(
                    schedule = currentSchedule,
                    nextTriggerTime = currentNextTriggerTime,
                    totalOccurrences = totalOccurrences,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                )

                // 8. 「このタームを終了」。セッションが開始済みで当日終了が可能な場合のみ表示する
                if (canEndTodaySession(currentSchedule, now)) {
                    Spacer(modifier = Modifier.height(12.dp))

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
                                onClick = { onEndTodayTerm(currentSchedule.id) },
                            ),
                    ) {
                        Canvas(modifier = Modifier.size(17.dp)) {
                            val strokeWidth = 1.7.dp.toPx()
                            val scale = size.width / 24f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(
                                color = subtleTextColor,
                                radius = 9f * scale,
                                center = center,
                                style = Stroke(width = strokeWidth),
                            )
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
                                fontSize = 15.sp,
                                color = subtleTextColor,
                            ),
                        )
                    }
                }

                // 「次の鳴動」のまとまりと「ターム」の小見出しを、はっきり別の段として見せる
                Spacer(modifier = Modifier.height(26.dp))
            }
        }
    }

        // 9. 「ターム」の小見出し（タームが1件以上ある場合のみ表示）
        if (terms.isNotEmpty()) {
            Text(
                text = stringResource(R.string.home_section_terms),
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontSize = 13.sp,
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
                    onToggleDay = { day -> viewModel.toggleDay(schedule, day) },
                    onClick = { onEditTerm(schedule.id) },
                )
            }

            // 11. 「タームを追加」。高さ52dp、破線の枠
            AddTermButton(onClick = onAddTerm)
        }
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

/** カードの曜日の丸の大きさ。設計図 design/Main.dc.html の26dpに合わせる。 */
private val TERM_DAY_CIRCLE_SIZE = 32.dp

/** 曜日を押せる範囲の横幅。丸は26dpのまま、指で狙える幅を確保するために広げる。 */
private val TERM_DAY_TOUCH_WIDTH = 42.dp

/** 曜日を押せる範囲の高さ。丸の外側にも余裕を持たせて押し外しを減らす。 */
private val TERM_DAY_TOUCH_HEIGHT = 44.dp

/** 曜日の押せる範囲どうしの間隔。見た目の丸の間が設計図の5dpに近くなる値とする。 */
private val TERM_DAY_SPACING = 0.dp

/**
 * 個別のターム情報を表示するカードComposable。
 * 左端の帯、時刻範囲、曜日の丸、要約、有効無効スイッチを包含する。
 */
@Composable
private fun TermCard(
    schedule: AlarmSchedule,
    timePattern: String,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
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
            // 上下の余白は中身の側へ付ける。行に付けると左の帯もそのぶん短くなる
            .padding(end = 14.dp),
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
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            // 時刻の範囲
            Text(
                text = timeRangeText,
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontSize = 27.sp,
                    lineHeight = 30.sp,
                    fontFeatureSettings = "tnum",
                    color = timeColor,
                ),
            )

            // 曜日の丸。カードの上でそのまま押して繰り返す曜日を切り替える
            Row(
                horizontalArrangement = Arrangement.spacedBy(TERM_DAY_SPACING),
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

                    val dayDesc = stringResource(R.string.home_term_day_description, dayShortLabel(day))
                    Box(
                        modifier = Modifier
                            .size(width = TERM_DAY_TOUCH_WIDTH, height = TERM_DAY_TOUCH_HEIGHT)
                            .semantics { contentDescription = dayDesc }
                            .clickable(
                                interactionSource = remember(day) { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 22.dp),
                                onClick = { onToggleDay(day) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(TERM_DAY_CIRCLE_SIZE)
                                .background(circleBg, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = dayShortLabel(day),
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    color = circleFg,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                ),
                            )
                        }
                    }
                }
            }

            // 間隔と回数
            Text(
                text = summaryText,
                style = TextStyle(
                    fontSize = 14.5.sp,
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
                fontSize = 14.5.sp,
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
