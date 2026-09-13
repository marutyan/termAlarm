package com.marutyan.termalarm.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import android.content.Intent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.domain.nextTrigger
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import com.marutyan.termalarm.ui.theme.NavyOnSurface
import com.marutyan.termalarm.ui.theme.NavyPrimary
import com.marutyan.termalarm.ui.theme.NavySubtleText
import com.marutyan.termalarm.ui.theme.NavySurface
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

// ウィジェットに出す内容。次の鳴動の有無で表示が変わるため、画面側で組み立てずここへまとめる
private data class WidgetContent(
    val date: String,
    val time: String,
    val nextTime: String?,
    val remaining: Int,
)

// 日付と曜日の書式。「09.11 金」のように、月日を先に置いて曜日を後ろへ添える
private val DATE_FORMAT = DateTimeFormatter.ofPattern("MM.dd E")

// 時刻の書式。秒はウィジェットでは出さない。1分ごとの更新で足りるため
private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm")

/**
 * ホーム画面へ置く時計ウィジェット。
 * 現在時刻を大きく出し、次に鳴る時刻と残り回数を小さく添える。
 * 小さい大きさのときは残り回数を省き、時刻と次の鳴動時刻だけにする。
 */
class TermAlarmWidget : GlanceAppWidget() {

    // 置かれた大きさに応じて中身を変えるため、システムに実際の大きさを渡してもらう
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadContent(context)
        provideContent {
            GlanceTheme {
                WidgetBody(content)
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
            remaining = next?.let { remainingOccurrenceCount(it.first, now) } ?: 0,
        )
    }
}

// ウィジェットの中身。押すとアプリが開く
@androidx.compose.runtime.Composable
private fun WidgetBody(content: WidgetContent) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(NavySurface)
            .cornerRadius(22.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = content.date,
            style = TextStyle(color = androidx.glance.unit.ColorProvider(NavySubtleText), fontSize = 13.sp),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = content.time,
            style = TextStyle(
                color = androidx.glance.unit.ColorProvider(NavyOnSurface),
                fontSize = 44.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
        if (content.nextTime != null) {
            Spacer(GlanceModifier.height(4.dp))
            NextRingRow(content.nextTime, content.remaining)
        }
    }
}

// 次に鳴る時刻と残り回数の行。次の鳴動が無いタームしか無い場合は呼ばれない
@androidx.compose.runtime.Composable
private fun NextRingRow(nextTime: String, remaining: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = nextTime,
            style = TextStyle(color = androidx.glance.unit.ColorProvider(NavyPrimary), fontSize = 14.sp),
        )
        if (remaining > 0) {
            Text(
                text = "  残り${remaining}回",
                style = TextStyle(color = androidx.glance.unit.ColorProvider(NavySubtleText), fontSize = 12.sp),
            )
        }
    }
}
