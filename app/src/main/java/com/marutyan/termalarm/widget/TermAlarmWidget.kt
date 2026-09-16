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

// 曜日の書式。月日のDATE_FORMATと同じ言い回し（E）で曜日を取り出し、次の鳴動の先頭に添えるために用いる
private val DAY_OF_WEEK_FORMAT = DateTimeFormatter.ofPattern("E")

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
        val nextTime = next?.second?.let {
            "${it.format(TIME_FORMAT)}（${it.format(DAY_OF_WEEK_FORMAT)}）"
        }
        return WidgetContent(
            date = LocalDateTime.now().format(DATE_FORMAT),
            time = LocalDateTime.now().format(TIME_FORMAT),
            nextTime = nextTime,
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
    (timeFontSizeSp * SECONDARY_FONT_RATIO).coerceIn(MIN_SECONDARY_FONT_SIZE_SP, MAX_SECONDARY_FONT_SIZE_SP)

/**
 * 描画領域の幅を考慮して、添える文字（月日・次の鳴動）の大きさをspの数値で求める関数。
 * 時刻に対する割合に基づく大きさに加え、月日および次の鳴動が利用可能な横幅に収まる上限サイズを掛け合わせることで、
 * 狭いウィジェット領域でも文字の途切れを防ぐために必要となる。
 * 幅による上限計算を行い、下限（11sp）と上限（30sp）の範囲内で最適なフォントサイズを決定する役割を持つ。
 */
fun secondaryFontSizeSp(
    timeFontSizeSp: Float,
    availableWidth: Float,
    dateWidthRatio: Float,
    nextRingWidthRatio: Float,
): Float {
    val baseSize = timeFontSizeSp * SECONDARY_FONT_RATIO
    val dateLimit = availableWidth / dateWidthRatio
    val availableForNextRing = (availableWidth - NEXT_RING_ICON_SPACING_DP).coerceAtLeast(0f)
    val nextRingLimit = availableForNextRing / (nextRingWidthRatio + ICON_TO_SECONDARY_RATIO)
    val maxAllowed = minOf(MAX_SECONDARY_FONT_SIZE_SP, dateLimit, nextRingLimit)
    return minOf(baseSize, maxAllowed).coerceAtLeast(MIN_SECONDARY_FONT_SIZE_SP)
}

/**
 * 時計アイコンの大きさをdpの数値で求める関数。
 * 添える文字に対して0.90倍とする決め方を、本体と見本で同じにするために必要となる。
 */
fun nextRingIconSizeDp(secondaryFontSizeSp: Float): Float = secondaryFontSizeSp * ICON_TO_SECONDARY_RATIO

/**
 * 時計アイコンの大きさ比率。
 * 添える文字の大きさに対して0.90倍とし、文字とバランスよくひと回り小さく並べるために用いる。
 * ウィジェット本体および設定画面の見本でアイコンの寸法を決定する役割を持つ。
 */
private const val ICON_TO_SECONDARY_RATIO = 0.90f

/**
 * 数字だけの行（時刻「9:16」や次の鳴動「9:17」）が占める高さの比率。
 * 数字中心の行は英数字用に行高を抑えられるため1.17倍で見積もり、ウィジェット内で時刻の文字を最大限大きく描くために必要となる。
 * 縦並びの時刻および次の鳴動の行高計算や、横並びの時刻の高さ上限算出において、必要な行高を解く係数の役割を持つ。
 */
private const val DIGIT_LINE_HEIGHT_RATIO = 1.17f

/**
 * 漢字を含む行（月日「9月16日(水)」）が占める高さの比率。
 * 日本語フォントの漢字行は英数字よりも上下に広い行高を要するため1.45倍を確保し、行の重なりや文字切れを防ぐために必要となる。
 * 縦並びの月日行の行高計算や、横並びの右列の高さ上限算出において、必要な行高を解く係数の役割を持つ。
 */
private const val KANJI_LINE_HEIGHT_RATIO = 1.45f

/**
 * 月日「9月16日(水)」26spにおける文字実体の高さ比率。
 * 実機で描画された月日行（全高1.45em）のうち、上下の余白を除いた実際の文字部分の高さ0.864emを実測した値である。
 * 縦並びで文字の実体を基準に配置位置と必要な高さを解くために必要となり、月日行の実体高さの計算に用いる役割を持つ。
 */
private const val KANJI_BODY_HEIGHT_RATIO = 0.864f

/**
 * 月日「9月16日(水)」26spにおける上の余白比率。
 * 実機で描画された月日行（全高1.45em）のうち、行上端から文字実体上端までの余白0.4165emを実測した値である。
 * 縦並びで文字実体を行の上端へ詰めて配置するためのpadding(top)算出と、全体の高さ見積もりに用いる役割を持つ。
 */
private const val KANJI_TOP_PADDING_RATIO = 0.4165f

/**
 * 時刻「9:52」145.4sp（Roboto）における文字実体の高さ比率。
 * 実機で描画された時刻行（全高1.17em）のうち、上下の余白を除いた実際の数字実体の高さ0.624emを実測した値である。
 * 縦並びで文字の実体を基準に配置位置と必要な高さを解くために必要となり、時刻の実体高さの計算に用いる役割を持つ。
 */
private const val DIGIT_BODY_HEIGHT_RATIO = 0.624f

/**
 * 時刻「9:52」145.4sp（Roboto）における上の余白比率。
 * 実機で描画された時刻行（全高1.17em）のうち、行上端から数字実体上端までの余白0.285emを実測した値である。
 * 縦並びで数字実体を行の上端へ詰めて配置するためのpadding(top)算出に用いる役割を持つ。
 */
private const val DIGIT_TOP_PADDING_RATIO = 0.285f

/**
 * 次の鳴動の行の高さ比率。
 * アイコン（0.90em）と文字の実体（0.624em）のうち高い方であるアイコンの高さ（0.90em）を採用する。
 * 縦並びにおいて次の鳴動行の実体としての高さを定義し、全体の高さ見積もりや位置計算に用いる役割を持つ。
 */
private const val NEXT_RING_ROW_HEIGHT_RATIO = ICON_TO_SECONDARY_RATIO

/**
 * 次の鳴動の行の上下余白比率。
 * 数字行全体の高さ1.17emの中で、高さ0.90emのアイコンと文字を縦中央揃えにした際に生じる上下余白 (1.17 - 0.90) / 2 = 0.135em を表す。
 * 縦並びにおいて次の鳴動行を配置する際のpadding(top)算出および下部の余白見積もりに用いる役割を持つ。
 */
private const val NEXT_RING_ROW_PADDING_RATIO = (DIGIT_LINE_HEIGHT_RATIO - NEXT_RING_ROW_HEIGHT_RATIO) / 2f

/**
 * 縦並びで添える文字（月日と次の鳴動）が占める高さの総係数。
 * 月日の上の余白(0.4165) + 月日実体(0.864) + 次の鳴動行(0.90) + 次の鳴動の下の余白(0.135) を合算した値である。
 * 縦並びの高さ制約式から時刻サイズtを解く際に、添える文字sに掛かる係数として計算を一元化する役割を持つ。
 */
private const val VERTICAL_SECONDARY_HEIGHT_COEFFICIENT =
    KANJI_TOP_PADDING_RATIO + KANJI_BODY_HEIGHT_RATIO + NEXT_RING_ROW_HEIGHT_RATIO + NEXT_RING_ROW_PADDING_RATIO

/**
 * 縦並びにおける行と行の固定間隔(dp)。
 * 行間隔を文字サイズに比例させると、文字を大きくした際に間隔まで過大に広がり領域の高さを圧迫してしまうため、
 * 視覚的な区切りを一定に保ちつつ時刻を最大限大きく描くために10dpの固定値とする。
 * 縦並びの各行の配置位置決めと高さ計算において間隔の基準となる役割を持つ。
 */
private const val VERTICAL_LINE_SPACING_DP = 10f

// 月日と次の鳴動の大きさ。時刻に対するこの割合にする。
// 添える文字を小さくすると、その行が占める高さも下がって時刻と近づき、
// 空いたぶんだけ時刻を大きく解けるようになる
private const val SECONDARY_FONT_RATIO = 0.32f

// 月日と次の鳴動の大きさの下限と上限(sp)。
// 縦並びの解き直しと添える文字の大きさ算出で同じ範囲を共有する
private const val MIN_SECONDARY_FONT_SIZE_SP = 11f
private const val MAX_SECONDARY_FONT_SIZE_SP = 30f

/**
 * 端末差を考慮して文字の横幅比に掛ける安全係数。
 * 実機で測った値は文字の送り幅そのものであるため大きな安全代は不要だが、
 * 端末やOSバージョンによるフォント描画の微妙な個体差を考慮して比に1.02を掛け、
 * 文字切れを確実に防ぐ役割を持つ。
 */
private const val FONT_WIDTH_SAFETY_FACTOR = 1.02f

/**
 * 時刻が横方向に使ってよい幅の割合。
 * 幅いっぱいまで使うと左右に余白が無く、時刻だけが大きすぎて見えるため、
 * 少し内側に収めて左右の余白を残す役割を持つ。
 */
private const val TIME_WIDTH_USAGE = 0.85f

/**
 * 次の鳴動表示における時計アイコンと時刻テキストの間の余白(dp)。
 * アイコンと文字が密着するのを防ぎ、行全体の横幅見積もりと実際のレイアウト描画で間隔の整合性を保つために必要となる。
 * 次の鳴動行に必要な横幅の計算およびSpacerの幅設定に用いる役割を持つ。
 */
private const val NEXT_RING_ICON_SPACING_DP = 4f
private val NEXT_RING_ICON_SPACING = NEXT_RING_ICON_SPACING_DP.dp

// 横並びのときに左の時刻と右の列（月日・次の鳴動）の間にあける空白(dp)。
// 左右がくっついて読みにくくなるのを防ぎ、右の列で使える幅の計算と行間の余白描画で同じ間隔を共有する役割を持つ。
private const val HORIZONTAL_SPACING_DP = 10f
private val HORIZONTAL_SPACING = HORIZONTAL_SPACING_DP.dp

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
 * 縦並び配置において、利用可能な高さから時刻文字の大きさの上限(sp)を算出する関数。
 * 文字の実体の位置で置くレイアウトに合わせ、使える高さ ≧ 月日の上の余白 + 0.864s + G + 0.624t + G + 0.90s + 次の鳴動の下の余白 を満たすtを求める。
 * 月日と次の鳴動の文字サイズsが上限や下限に当たった場合は、上限値または下限値で固定した上で時刻の大きさを解き直す。
 * 縦並び表示で描画領域の高さを最大限に活かし、文字がはみ出すことなく時刻をできる限り大きく表示する役割を持つ。
 */
private fun calculateVerticalTimeHeightLimit(availableHeight: Float): Float {
    val availableForContent = availableHeight - 2f * VERTICAL_LINE_SPACING_DP

    // (a) まず上限・下限を考慮せず、月日と次の鳴動が時刻の0.32倍になる前提で解く
    val unconstrainedRatio = DIGIT_BODY_HEIGHT_RATIO + VERTICAL_SECONDARY_HEIGHT_COEFFICIENT * SECONDARY_FONT_RATIO
    val t0 = availableForContent / unconstrainedRatio

    // (b) t0から求めた月日の大きさs0が上限・下限に当たるか確認する
    val s0 = t0 * SECONDARY_FONT_RATIO
    return when {
        // (c) 上限を超える場合は、月日と次の鳴動を上限値で固定して時刻を解き直す
        s0 > MAX_SECONDARY_FONT_SIZE_SP -> {
            (availableForContent - MAX_SECONDARY_FONT_SIZE_SP * VERTICAL_SECONDARY_HEIGHT_COEFFICIENT) / DIGIT_BODY_HEIGHT_RATIO
        }
        // (d) 下限を下回る場合は、月日と次の鳴動を下限値で固定して時刻を解き直す
        s0 < MIN_SECONDARY_FONT_SIZE_SP -> {
            (availableForContent - MIN_SECONDARY_FONT_SIZE_SP * VERTICAL_SECONDARY_HEIGHT_COEFFICIENT) / DIGIT_BODY_HEIGHT_RATIO
        }
        // 上限にも下限にも当たらなければt0をそのまま使う
        else -> t0
    }
}

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
    val padding = minOf(4f, size.height.value * 0.035f).dp
    val h = (size.height.value - padding.value * 2f - WIDGET_SHADOW_OFFSET_DP)
    val w = (size.width.value - padding.value * 2f - WIDGET_SHADOW_OFFSET_DP)
    // 幅が高さに比べて2倍以上の横長形状であれば横並びにする
    val isHorizontal = (w / h) >= 2.0f

    val timeWidthRatio = fontStyle.timeWidthRatio * FONT_WIDTH_SAFETY_FACTOR
    val dateWidthRatio = fontStyle.dateWidthRatio * FONT_WIDTH_SAFETY_FACTOR
    val nextRingWidthRatio = fontStyle.nextRingWidthRatio * FONT_WIDTH_SAFETY_FACTOR

    val showDate: Boolean
    val showNextRing: Boolean
    val secondaryAvailableWidth: Float
    val timeFontSize = if (isHorizontal) {
        // 横に並べる場合：左に時刻、右に月日と次の鳴動を縦へ積む
        val leftAvailableWidth = w * 0.58f
        val rightAvailableWidth = w - leftAvailableWidth - HORIZONTAL_SPACING_DP
        secondaryAvailableWidth = rightAvailableWidth
        // 右側は、h >= 40f なら月日と次の鳴動の両方、そうでなければ次の鳴動だけを出す。
        // 次に鳴る時刻が無い場合は月日だけを出す。
        showDate = content.nextTime == null || h >= 40f
        showNextRing = content.nextTime != null
        // 時刻の大きさは、右の列（月日＋次の鳴動）が常に2行あるものとして解く。
        // 左側の時刻1行は数字行の見積もり（1.17倍）を用いる
        val heightLimit = h / DIGIT_LINE_HEIGHT_RATIO
        val rightHeightLimit = h / ((KANJI_LINE_HEIGHT_RATIO + DIGIT_LINE_HEIGHT_RATIO) * SECONDARY_FONT_RATIO)
        val leftWidthLimit = leftAvailableWidth * TIME_WIDTH_USAGE / timeWidthRatio
        val rightWidthLimit = rightAvailableWidth / (dateWidthRatio * SECONDARY_FONT_RATIO)
        val maxTimeFontSize = minOf(heightLimit, rightHeightLimit, leftWidthLimit, rightWidthLimit)
        maxTimeFontSize.coerceAtLeast(12f).sp
    } else {
        secondaryAvailableWidth = w
        // 縦に並べる場合（既定）：上から月日、時刻、次の鳴動の順で並べる
        showDate = h >= 56f
        showNextRing = h >= 84f && content.nextTime != null
        // 実際に出すかどうかにかかわらず、常に3行ぶん（時刻＋月日＋次の鳴動）で必要な高さを計算する。
        // 上限・下限に当たることを踏まえて時刻の大きさを解き直す
        val heightLimit = calculateVerticalTimeHeightLimit(h)
        val widthLimit = w * TIME_WIDTH_USAGE / timeWidthRatio
        minOf(heightLimit, widthLimit).coerceAtLeast(12f).sp
    }

    // 時刻の大きさに合わせて添える文字（月日・次の鳴動）と時計アイコンの大きさを決定する
    val secondaryFontSize = secondaryFontSizeSp(
        timeFontSizeSp = timeFontSize.value,
        availableWidth = secondaryAvailableWidth,
        dateWidthRatio = dateWidthRatio,
        nextRingWidthRatio = nextRingWidthRatio,
    ).sp
    val iconSize = nextRingIconSizeDp(secondaryFontSize.value).dp
    val secondaryWeight = secondaryFontWeight(fontWeight)

    // 第4版の共通契約に基づき、背景色・時刻の色・月日と次の鳴動の色を取得する
    val backgroundColor = widgetBackgroundProvider(widgetBackgroundStyle(prefs))
    val selectedTimeColor = widgetTimeColor(prefs)
    val timeColor = widgetTimeColorProvider(selectedTimeColor)
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
    // 影の層は、本体と同じ中身を黒一色で描く。ただし時刻が黒のときは同じ色で重なり滲むため透明にする
    val shadowTimeColor = if (selectedTimeColor == WidgetTimeColor.BLACK) {
        ColorProvider(Color.Transparent)
    } else {
        SHADOW_COLOR
    }
    val shadowStyle = baseStyle.copy(timeColor = shadowTimeColor, secondaryColor = SHADOW_COLOR)

    // 背景色・角丸・余白・タップ時のアプリ起動アクションを設定した共通修飾子
    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(backgroundColor)
        .cornerRadius(22.dp)
        .padding(horizontal = padding, vertical = padding)
        .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))

    // 影は文字ごとではなく画面全体を2枚重ねて出す。
    // 文字1つずつをBoxで包むと、その行が縦並びの中で押し出されて出なくなるため
    // ただし、同じ文字列を持つ層が2枚重なるため、TalkBackなどの読み上げが月日・時刻・次の鳴動を2回読む費用がある。
    Box(modifier = rootModifier) {
        // 2枚の層は同じ大きさのまま、置く位置だけが影のぶんずれるようにする。
        // 片方へ左上の余白、もう片方へ右下の余白を同じだけ入れると、ずれが正確にSHADOW_OFFSETになる
        WidgetLayer(
            content = content,
            style = shadowStyle,
            isHorizontal = isHorizontal,
            showDate = showDate,
            showNextRing = showNextRing,
            availableHeight = h,
            modifier = GlanceModifier.fillMaxSize()
                .padding(start = SHADOW_OFFSET, top = SHADOW_OFFSET),
        )
        WidgetLayer(
            content = content,
            style = baseStyle,
            isHorizontal = isHorizontal,
            showDate = showDate,
            showNextRing = showNextRing,
            availableHeight = h,
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
    availableHeight: Float,
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
        // 横並び配置：左に時刻、右に月日と次の鳴動を配置し、左右の間をあける
        Row(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = content.time, style = timeStyle, maxLines = 1)
            Spacer(GlanceModifier.width(HORIZONTAL_SPACING))
            Column(
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
            ) {
                if (showDate) {
                    Text(text = content.date, style = dateStyle, maxLines = 1)
                }
                if (showNextRing && content.nextTime != null) {
                    NextRingRow(time = content.nextTime, style = dateStyle, iconSize = style.iconSize)
                }
            }
        }
    } else {
        // 縦並び配置（既定）：文字の実体位置に基づき、各行の上部パディングを計算してBoxへ配置する
        val s = style.secondaryFontSize.value
        val t = style.timeFontSize.value
        val g = VERTICAL_LINE_SPACING_DP

        // 3つの行の実体を間隔gで並べた全体の高さ（月日実体 + 間隔 + 時刻実体 + 間隔 + 次の鳴動行）
        val totalContentHeight =
            KANJI_BODY_HEIGHT_RATIO * s + g + DIGIT_BODY_HEIGHT_RATIO * t + g + NEXT_RING_ROW_HEIGHT_RATIO * s

        // 全体を使える高さの中で上下中央に配置するための上端オフセット
        val verticalOffset = (availableHeight - totalContentHeight) / 2f

        // 各行の実体を置きたい位置
        val dateBodyTop = verticalOffset
        val timeBodyTop = dateBodyTop + KANJI_BODY_HEIGHT_RATIO * s + g
        val nextRingBodyTop = timeBodyTop + DIGIT_BODY_HEIGHT_RATIO * t + g

        // 各行が持つ上の余白
        val dateTopPadding = KANJI_TOP_PADDING_RATIO * s
        val timeTopPadding = DIGIT_TOP_PADDING_RATIO * t
        val nextRingTopPadding = NEXT_RING_ROW_PADDING_RATIO * s

        // 各行の配置位置（実体を置きたい位置 − その行が持つ上の余白）。負の場合は0にする
        val dateTop = (dateBodyTop - dateTopPadding).coerceAtLeast(0f).dp
        val timeTop = (timeBodyTop - timeTopPadding).coerceAtLeast(0f).dp
        val nextRingTop = (nextRingBodyTop - nextRingTopPadding).coerceAtLeast(0f).dp

        Box(
            modifier = modifier,
            contentAlignment = Alignment.TopCenter,
        ) {
            if (showDate) {
                Text(
                    text = content.date,
                    style = dateStyle,
                    modifier = GlanceModifier.padding(top = dateTop),
                    maxLines = 1,
                )
            }
            Text(
                text = content.time,
                style = timeStyle,
                modifier = GlanceModifier.padding(top = timeTop),
                maxLines = 1,
            )
            if (showNextRing && content.nextTime != null) {
                NextRingRow(
                    time = content.nextTime,
                    style = dateStyle,
                    iconSize = style.iconSize,
                    modifier = GlanceModifier.padding(top = nextRingTop),
                )
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
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_alarm),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(style.color),
        )
        Spacer(GlanceModifier.width(NEXT_RING_ICON_SPACING))
        Text(text = time, style = style, maxLines = 1)
    }
}
