package com.marutyan.termalarm.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.domain.nextTrigger
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

// ウィジェットに出す内容。次の鳴動の有無で表示が変わるため、画面側で組み立てずここへまとめる
private data class WidgetContent(
    val date: String,
    val time: String,
    val nextTime: String?,
)

// 日付と曜日の書式。「9月16日(水)」のように月日と曜日を並べる
private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日(E)")

// 時刻の書式。秒はウィジェットでは出さない。1分ごとの更新で足りるため
private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm")

/**
 * ホーム画面へ置く時計ウィジェット。
 * 現在時刻を大きく出し、月日・曜日と次に鳴る時刻を添える。
 * 画面の寸法に合わせて文字の大きさを滑らかに計算し、どんな大きさでも崩れないようにする。
 */
class TermAlarmWidget : GlanceAppWidget() {

    // 置かれた大きさに応じて中身を変えるため、システムに実際の大きさを渡してもらう
    override val sizeMode: SizeMode = SizeMode.Exact

    // 配色と背景の選択をウィジェットごとに保存するため、Preferencesを状態として持つ
    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadContent(context)
        provideContent {
            // GlanceThemeの既定が端末のダイナミックカラーなので、色を渡さずそのまま使う
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val palette = widgetPalette(prefs)
                val accentTarget = widgetAccentTarget(prefs)
                val fontStyle = widgetFontStyle(prefs)
                val fontWeight = widgetFontWeight(prefs)
                WidgetBody(
                    content = content,
                    palette = palette,
                    accentTarget = accentTarget,
                    fontStyle = fontStyle,
                    fontWeight = fontWeight,
                )
            }
        }
    }

    // 次に鳴るタームを選び、画面へ出す形へ整える。時刻の計算はdomainの関数へ任せる
    private suspend fun loadContent(context: Context): WidgetContent {
        val now = ZonedDateTime.now()
        val schedules = runCatching { Repositories.alarm(context).observeAll().first() }
            .getOrDefault(emptyList())
        val next = schedules
            .filter { it.enabled }
            .mapNotNull { schedule -> nextTrigger(schedule, now)?.let { schedule to it } }
            .minByOrNull { it.second }
        return WidgetContent(
            date = LocalDateTime.now().format(DATE_FORMAT),
            time = LocalDateTime.now().format(TIME_FORMAT),
            nextTime = next?.second?.format(TIME_FORMAT),
        )
    }
}

// ウィジェットの中身。押すとアプリが開く。置かれた大きさに合わせて文字サイズを計算し、3つの要素を描画する
@Composable
private fun WidgetBody(
    content: WidgetContent,
    palette: WidgetPalette,
    accentTarget: WidgetAccentTarget,
    fontStyle: WidgetFontStyle,
    fontWeight: WidgetFontWeight,
) {
    val size = LocalSize.current
    val padding = minOf(14f, size.height.value * 0.12f).dp
    val h = (size.height.value - padding.value * 2f).dp
    val w = (size.width.value - padding.value * 2f).dp

    val showDate = h >= 62.dp
    val showNextRing = h >= 92.dp && content.nextTime != null

    val timeAvailableHeight = (h.value - (if (showDate) 20f else 0f) - (if (showNextRing) 22f else 0f)).dp
    val timeFontSize = minOf(timeAvailableHeight.value * 0.80f, w.value / 2.9f).coerceIn(14f, 96f).sp
    val secondaryFontSize = (timeFontSize.value * 0.30f).coerceIn(10f, 18f).sp
    val iconSize = (secondaryFontSize.value * 1.05f).dp

    val dateColor = palette.colorOf(WidgetAccentTarget.DATE, accentTarget)
    val timeColor = palette.colorOf(WidgetAccentTarget.TIME, accentTarget)
    val nextRingColor = palette.colorOf(WidgetAccentTarget.NEXT_RING, accentTarget)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(22.dp)
            .padding(horizontal = padding, vertical = padding)
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDate) {
            Text(
                text = content.date,
                style = TextStyle(
                    color = dateColor,
                    fontSize = secondaryFontSize,
                    fontFamily = fontStyle.family,
                    fontWeight = fontWeight.glanceWeight,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
        }
        Text(
            text = content.time,
            style = TextStyle(
                color = timeColor,
                fontSize = timeFontSize,
                fontFamily = fontStyle.family,
                fontWeight = fontWeight.glanceWeight,
            ),
        )
        if (showNextRing && content.nextTime != null) {
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_alarm),
                    contentDescription = null,
                    modifier = GlanceModifier.size(iconSize),
                    colorFilter = ColorFilter.tint(nextRingColor),
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = content.nextTime,
                    style = TextStyle(
                        color = nextRingColor,
                        fontSize = secondaryFontSize,
                        fontFamily = fontStyle.family,
                        fontWeight = fontWeight.glanceWeight,
                    ),
                )
            }
        }
    }
}
