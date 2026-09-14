package com.marutyan.termalarm.ui.alarms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marutyan.termalarm.ui.common.SCREEN_HORIZONTAL_PADDING
import com.marutyan.termalarm.ui.common.TOP_BAR_CONTENT_GAP
import com.marutyan.termalarm.ui.common.TOP_BAR_TOP_INSET
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.ui.common.TermAlarmTopBar
import com.marutyan.termalarm.ui.common.clockTimePattern
import com.marutyan.termalarm.ui.common.formatClockMinutes
import com.marutyan.termalarm.ui.theme.IbmPlexMono
import com.marutyan.termalarm.ui.theme.customColors
import com.marutyan.termalarm.ui.theme.ibmPlexMonoFontFamily
import java.time.DayOfWeek

/**
 * 破線枠を描画するModifier拡張関数。
 * アラーム追加ボタンなどの輪郭線をデザイン通りに描画するために用いる。
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

/** 曜日の並び順リスト。月曜日から日曜日までの順序で繰り返し曜日を表示するために用いる。 */
private val DAYS_OF_WEEK_ORDER = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

/**
 * 通常アラーム画面の最上位Composable。design/Alarms.dc.htmlを再現する。
 * 見出し、説明、通常アラームカード一覧、アラーム追加ボタンを上から順に配置する。
 */
@Composable
fun AlarmsScreen(
    viewModel: AlarmsViewModel,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val timePattern = remember { clockTimePattern(false) }


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
            .verticalScroll(rememberScrollState())
            .padding(start = SCREEN_HORIZONTAL_PADDING, end = SCREEN_HORIZONTAL_PADDING, bottom = 24.dp),
    ) {

        Spacer(modifier = Modifier.height(TOP_BAR_CONTENT_GAP))

        // 1. 見出し「アラーム」。28sp、太さ300
        Text(
            text = stringResource(R.string.alarms_title),
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.W300,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.01).em,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. 説明の1行。12.5sp、薄い文字の色
        Text(
            text = stringResource(R.string.alarms_subtitle),
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.customColors.subtleText,
            ),
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 3. アラームのカード一覧と「アラームを追加」
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            alarms.forEach { schedule ->
                AlarmCard(
                    schedule = schedule,
                    timePattern = timePattern,
                    onToggleEnabled = { enabled -> viewModel.toggleEnabled(schedule, enabled) },
                    onClick = { onEditAlarm(schedule.id) },
                )
            }

            // 4. 「アラームを追加」。高さ56dp、破線の枠
            AddAlarmButton(onClick = onAddAlarm)
        }
    }
    }
}

/**
 * 個別の通常アラーム情報を表示するカードComposable。
 * 時刻、ラベル、曜日テキスト、有効・無効切り替えスイッチを包含する。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmCard(
    schedule: AlarmSchedule,
    timePattern: String,
    onToggleEnabled: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val subtleTextColor = MaterialTheme.customColors.subtleText
    val timeColor = if (schedule.enabled) MaterialTheme.colorScheme.onSurface else subtleTextColor

    val timeText = remember(schedule.startMinutes, timePattern) {
        formatClockMinutes(schedule.startMinutes, timePattern)
    }
    val repeatDaysText = formatRepeatDays(schedule.repeatDays)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceContainer, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 左側のテキスト領域（時刻、ラベル、曜日）
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 時刻32sp・太さ200・等幅数字、右にラベル13sp
            FlowRow(
                verticalArrangement = Arrangement.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = timeText,
                    style = TextStyle(
                        fontFamily = ibmPlexMonoFontFamily(200),
                        fontWeight = FontWeight.W200,
                        fontSize = 32.sp,
                        lineHeight = 32.sp,
                        fontFeatureSettings = "tnum",
                        letterSpacing = (-0.04).em,
                    ),
                    color = timeColor,
                )

                if (schedule.label.isNotBlank()) {
                    Text(
                        text = schedule.label,
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = subtleTextColor,
                        ),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }

            // 下に曜日12sp
            Text(
                text = repeatDaysText,
                style = TextStyle(
                    fontFamily = IbmPlexMono,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = subtleTextColor,
                ),
            )
        }

        // 右端に切り替えスイッチ 44×26dp（タッチターゲット44dp以上を確保）
        val switchDesc = stringResource(R.string.alarms_switch_description, timeText)
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                .semantics { contentDescription = switchDesc }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 22.dp),
                    role = Role.Switch,
                    onClick = { onToggleEnabled(!schedule.enabled) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 26.dp)
                    .background(
                        color = if (schedule.enabled) primaryColor else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(13.dp),
                    )
                    .padding(3.dp),
                contentAlignment = if (schedule.enabled) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
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
 * 通常アラーム追加操作を受け付ける破線枠ボタンComposable。
 * 高さ56dpの枠線内へプラスアイコンとテキストを均等に配置する。
 */
@Composable
private fun AddAlarmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val subtleTextColor = MaterialTheme.customColors.subtleText

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .dashedBorder(
                width = 1.dp,
                color = outlineColor,
                cornerRadius = 14.dp,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val strokeWidth = 1.8.dp.toPx()
            val halfW = size.width / 2f
            val halfH = size.height / 2f
            // 縦線
            drawLine(
                color = subtleTextColor,
                start = Offset(halfW, 2.5.dp.toPx()),
                end = Offset(halfW, size.height - 2.5.dp.toPx()),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            // 横線
            drawLine(
                color = subtleTextColor,
                start = Offset(2.5.dp.toPx(), halfH),
                end = Offset(size.width - 2.5.dp.toPx(), halfH),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        Spacer(modifier = Modifier.width(9.dp))

        Text(
            text = stringResource(R.string.alarms_add_alarm),
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 14.sp,
                color = subtleTextColor,
            ),
        )
    }
}

/**
 * 繰り返しの曜日セットを表示用テキストに変換する関数。
 * 空集合の場合は「きょうだけ」、それ以外は月曜から日曜の順でスペース区切りの短縮曜日名を返す。
 */
@Composable
private fun formatRepeatDays(repeatDays: Set<DayOfWeek>): String {
    if (repeatDays.isEmpty()) {
        return stringResource(R.string.alarms_repeat_today_only)
    }
    val mon = stringResource(R.string.day_monday_short)
    val tue = stringResource(R.string.day_tuesday_short)
    val wed = stringResource(R.string.day_wednesday_short)
    val thu = stringResource(R.string.day_thursday_short)
    val fri = stringResource(R.string.day_friday_short)
    val sat = stringResource(R.string.day_saturday_short)
    val sun = stringResource(R.string.day_sunday_short)

    val labels = remember(mon, tue, wed, thu, fri, sat, sun) {
        mapOf(
            DayOfWeek.MONDAY to mon,
            DayOfWeek.TUESDAY to tue,
            DayOfWeek.WEDNESDAY to wed,
            DayOfWeek.THURSDAY to thu,
            DayOfWeek.FRIDAY to fri,
            DayOfWeek.SATURDAY to sat,
            DayOfWeek.SUNDAY to sun,
        )
    }
    return DAYS_OF_WEEK_ORDER
        .filter { it in repeatDays }
        .mapNotNull { labels[it] }
        .joinToString(" ")
}
