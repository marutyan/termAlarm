package com.marutyan.termalarm.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
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
                WidgetBody(
                    content = content,
                    prefs = prefs,
                    fontStyle = widgetFontStyle(prefs),
                    fontWeight = widgetFontWeight(prefs),
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

/**
 * 月日や次の鳴動時刻に適用する文字の太さを決める関数。
 * 選ばれた太さが標準のときだけ見やすさのためにMEDIUMへ上げ、MEDIUMとBOLDはそのまま使う。
 * ウィジェット本体と設定画面の見本で同じ決め方を使うため、ここ1か所へまとめている。
 */
fun secondaryFontWeight(weight: WidgetFontWeight): WidgetFontWeight {
    return when (weight) {
        WidgetFontWeight.NORMAL -> WidgetFontWeight.MEDIUM
        WidgetFontWeight.MEDIUM -> WidgetFontWeight.MEDIUM
        WidgetFontWeight.BOLD -> WidgetFontWeight.BOLD
    }
}

/**
 * 添える文字（月日・次の鳴動）の大きさをspの数値で求める関数。
 * 時刻に対する割合と下限・上限を、ウィジェット本体と設定画面の見本で同じにするために必要となる。
 */
fun secondaryFontSizeSp(timeFontSizeSp: Float): Float =
    (timeFontSizeSp * SECONDARY_FONT_RATIO).coerceIn(11f, 26f)

/**
 * 時計アイコンの大きさをdpの数値で求める関数。
 * 添える文字よりほんの少し大きくする決め方を、本体と見本で同じにするために必要となる。
 */
fun nextRingIconSizeDp(secondaryFontSizeSp: Float): Float = secondaryFontSizeSp * ICON_TO_SECONDARY_RATIO

// 時計アイコンは添える文字より少しだけ大きくすると、文字と並べたときに釣り合って見える
private const val ICON_TO_SECONDARY_RATIO = 1.05f

// 1行が占める高さは、その文字の大きさのおよそ1.45倍になる。入る大きさを解くときに使う。
// 日本語の書体は英数字より行が高く、1.2倍で見積もっていたときは合計が入りきらなかった
private const val LINE_HEIGHT_RATIO = 1.45f

// 月日と次の鳴動の大きさ。時刻に対するこの割合にする
private const val SECONDARY_FONT_RATIO = 0.38f

// 時刻「12:34」の横幅は、その文字の大きさのおよそ2.75倍になる
private const val TIME_WIDTH_RATIO = 2.75f

// 文字の影をずらす量(dp)と濃さ。純正の時計アプリがごく薄い影を落としているのに合わせる。
// 設定画面の見本も同じ影にするため、外へ公開している
const val WIDGET_SHADOW_OFFSET_DP = 1f
const val WIDGET_SHADOW_ALPHA = 0.55f

private val SHADOW_OFFSET = WIDGET_SHADOW_OFFSET_DP.dp

// 影の色。本体の文字と同じ形を黒で敷くために使う
private val SHADOW_COLOR = ColorProvider(Color.Black.copy(alpha = WIDGET_SHADOW_ALPHA))

/**
 * ウィジェット1枚ぶんの描画に必要な、大きさと色をまとめた型。
 * 影の層と本体の層で色だけを差し替えて同じ中身を2回描くため、
 * 引数の数を増やさずに両方の層へ同じ寸法を渡す役割を持つ。
 */
private data class WidgetLayerStyle(
    val timeFontSize: TextUnit,
    val secondaryFontSize: TextUnit,
    val iconSize: Dp,
    val fontStyle: WidgetFontStyle,
    val timeWeight: WidgetFontWeight,
    val secondaryWeight: WidgetFontWeight,
    val timeColor: ColorProvider,
    val secondaryColor: ColorProvider,
)

/**
 * ウィジェットの中身を描画するComposable。
 * 押すとアプリ本体が開く。置かれた領域の幅と高さに応じて横並び・縦並びを切り替え、
 * 余白と文字の大きさを滑らかに調整する。
 */
@Composable
private fun WidgetBody(
    content: WidgetContent,
    prefs: Preferences,
    fontStyle: WidgetFontStyle,
    fontWeight: WidgetFontWeight,
) {
    // アプリ起動用Intentの生成に用いるContextを取得する
    val context = LocalContext.current

    // ウィジェットが配置された領域の寸法を取得し、外枠余白と有効な描画領域を計算する
    val size = LocalSize.current
    val padding = minOf(10f, size.height.value * 0.08f).dp
    val h = (size.height.value - padding.value * 2f)
    val w = (size.width.value - padding.value * 2f)
    // 幅が高さに比べて2倍以上の横長形状であれば横並びにする
    val isHorizontal = (w / h) >= 2.0f

    val showDate: Boolean
    val showNextRing: Boolean
    val timeFontSize = if (isHorizontal) {
        // 横に並べる場合：左に時刻、右に月日と次の鳴動を縦へ積む
        val leftAvailableWidth = w * 0.58f
        // 右側は、h >= 40f なら月日と次の鳴動の両方、そうでなければ次の鳴動だけを出す。
        // 次に鳴る時刻が無い場合は月日だけを出す。
        showDate = content.nextTime == null || h >= 40f
        showNextRing = content.nextTime != null
        // 左は時刻1行だけなので、高さをそのまま1行ぶんとして使う
        minOf(h / LINE_HEIGHT_RATIO, leftAvailableWidth / TIME_WIDTH_RATIO)
            .coerceIn(12f, 160f).sp
    } else {
        // 縦に並べる場合（既定）：上から月日、時刻、次の鳴動の順で並べる
        showDate = h >= 56f
        showNextRing = h >= 84f && content.nextTime != null
        // 3つの行がちょうど収まる時刻の大きさを解いて求める。
        // 月日と次の鳴動は時刻のSECONDARY_FONT_RATIO倍なので、必要な高さは
        // 時刻の大きさ × 行の高さの比 × (1 + 添える行のぶん) になる
        val rows = 1f +
            (if (showDate) SECONDARY_FONT_RATIO else 0f) +
            (if (showNextRing) SECONDARY_FONT_RATIO else 0f)
        minOf(h / (LINE_HEIGHT_RATIO * rows), w / TIME_WIDTH_RATIO).coerceIn(12f, 160f).sp
    }

    // 時刻の大きさに合わせて添える文字（月日・次の鳴動）と時計アイコンの大きさを決定する
    val secondaryFontSize = secondaryFontSizeSp(timeFontSize.value).sp
    val iconSize = nextRingIconSizeDp(secondaryFontSize.value).dp
    val secondaryWeight = secondaryFontWeight(fontWeight)

    // 第4版の共通契約に基づき、背景色・時刻の色・月日と次の鳴動の色を取得する
    val backgroundColor = widgetBackgroundProvider(widgetBackgroundStyle(prefs))
    val timeColor = widgetTimeColorProvider(widgetTimeColor(prefs))
    val secondaryColor = widgetSecondaryColorProvider()

    val baseStyle = WidgetLayerStyle(
        timeFontSize = timeFontSize,
        secondaryFontSize = secondaryFontSize,
        iconSize = iconSize,
        fontStyle = fontStyle,
        timeWeight = fontWeight,
        secondaryWeight = secondaryWeight,
        timeColor = timeColor,
        secondaryColor = secondaryColor,
    )
    // 影の層は、本体と同じ中身を黒一色で描く
    val shadowStyle = baseStyle.copy(timeColor = SHADOW_COLOR, secondaryColor = SHADOW_COLOR)

    // 背景色・角丸・余白・タップ時のアプリ起動アクションを設定した共通修飾子
    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(backgroundColor)
        .cornerRadius(22.dp)
        .padding(horizontal = padding, vertical = padding)
        .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))

    // 影は文字ごとではなく画面全体を2枚重ねて出す。
    // 文字1つずつをBoxで包むと、その行が縦並びの中で押し出されて出なくなるため
    Box(modifier = rootModifier) {
        // 2枚の層は同じ大きさのまま、置く位置だけが影のぶんずれるようにする。
        // 片方へ左上の余白、もう片方へ右下の余白を同じだけ入れると、ずれが正確にSHADOW_OFFSETになる
        WidgetLayer(
            content = content,
            style = shadowStyle,
            isHorizontal = isHorizontal,
            showDate = showDate,
            showNextRing = showNextRing,
            modifier = GlanceModifier.fillMaxSize()
                .padding(start = SHADOW_OFFSET, top = SHADOW_OFFSET),
        )
        WidgetLayer(
            content = content,
            style = baseStyle,
            isHorizontal = isHorizontal,
            showDate = showDate,
            showNextRing = showNextRing,
            modifier = GlanceModifier.fillMaxSize()
                .padding(end = SHADOW_OFFSET, bottom = SHADOW_OFFSET),
        )
    }
}

/**
 * 月日・時刻・次の鳴動を並べる1枚ぶんの層を描くComposable。
 * 影の層と本体の層でまったく同じ並びを使う必要があるため、並べ方をここ1か所へまとめている。
 */
@Composable
private fun WidgetLayer(
    content: WidgetContent,
    style: WidgetLayerStyle,
    isHorizontal: Boolean,
    showDate: Boolean,
    showNextRing: Boolean,
    modifier: GlanceModifier,
) {
    val dateStyle = TextStyle(
        color = style.secondaryColor,
        fontSize = style.secondaryFontSize,
        fontFamily = style.fontStyle.family,
        fontWeight = style.secondaryWeight.glanceWeight,
    )
    val timeStyle = TextStyle(
        color = style.timeColor,
        fontSize = style.timeFontSize,
        fontFamily = style.fontStyle.family,
        fontWeight = style.timeWeight.glanceWeight,
    )

    if (isHorizontal) {
        // 横並び配置：左に時刻、右に月日と次の鳴動を配置し、左右の間を10dpあける
        Row(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = content.time, style = timeStyle)
            Spacer(GlanceModifier.width(10.dp))
            Column(
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
            ) {
                if (showDate) {
                    Text(text = content.date, style = dateStyle)
                }
                if (showNextRing && content.nextTime != null) {
                    NextRingRow(time = content.nextTime, style = dateStyle, iconSize = style.iconSize)
                }
            }
        }
    } else {
        // 縦並び配置（既定）：上から月日、時刻、次の鳴動の順で並べ、余白を詰める
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDate) {
                Text(text = content.date, style = dateStyle)
            }
            Text(text = content.time, style = timeStyle)
            if (showNextRing && content.nextTime != null) {
                NextRingRow(time = content.nextTime, style = dateStyle, iconSize = style.iconSize)
            }
        }
    }
}

/**
 * 時計アイコンと次に鳴る時刻を横へ並べるComposable。
 * 縦並びと横並びの両方で同じ見た目にするため、1か所へまとめている。
 */
@Composable
private fun NextRingRow(
    time: String,
    style: TextStyle,
    iconSize: Dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_alarm),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(style.color ?: ColorProvider(Color.White)),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(text = time, style = style)
    }
}
