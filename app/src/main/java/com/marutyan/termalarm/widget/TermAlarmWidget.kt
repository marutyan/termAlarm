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
import androidx.glance.layout.fillMaxHeight
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
import java.util.Locale
import kotlinx.coroutines.flow.first

// ウィジェットに出す内容。次の鳴動の有無で表示が変わるため、画面側で組み立てずここへまとめる
private data class WidgetContent(
    val date: String,
    val time: String,
    val nextTime: String?,
)

// 日付と曜日の書式。「9月16日(水)」のように月日と曜日を並べる。
// 端末の地域設定が日本語以外でも「Wed」等の英字表記になって横幅が約1割広がり文字切れするのを防ぐため、明示的に日本語Localeを指定する。
private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPANESE)

// 曜日の書式。月日のDATE_FORMATと同じ言い回し（E）で曜日を取り出し、次の鳴動の先頭に添えるために用いる。
// 端末言語によらず日本語の曜日表記で統一するため、明示的に日本語Localeを指定する。
private val DAY_OF_WEEK_FORMAT = DateTimeFormatter.ofPattern("E", Locale.JAPANESE)

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
 * 設定画面の見本は固定寸法（40sp）で描画しており本体のような動的サイズ計算は行わないが、太さの変換ルールを共通化するためにここ1か所へまとめている。
 */
fun secondaryFontWeight(weight: WidgetFontWeight): WidgetFontWeight {
    return when (weight) {
        WidgetFontWeight.NORMAL -> WidgetFontWeight.MEDIUM
        WidgetFontWeight.MEDIUM -> WidgetFontWeight.MEDIUM
        WidgetFontWeight.BOLD -> WidgetFontWeight.BOLD
    }
}

/**
 * 添える文字（月日・次の鳴動）の大きさを求める関数。
 * 設定画面の見本は固定寸法（40sp）で描画しており本体のような動的サイズ計算は行わないが、
 * 時刻に対する割合（0.32倍）と下限・上限（11〜30）の共通ルールを共有するために必要となる。
 */
fun secondaryFontSizeSp(timeFontSizeSp: Float): Float =
    (timeFontSizeSp * SECONDARY_FONT_RATIO).coerceIn(MIN_SECONDARY_FONT_SIZE_DP, MAX_SECONDARY_FONT_SIZE_DP)

/**
 * 時計アイコンの大きさを求める関数。
 * 設定画面の見本は固定寸法で描画しており本体のような動的サイズ計算は行わないが、
 * 添える文字に対して0.90倍とするアイコン寸法のルールを共有するために必要となる。
 */
fun nextRingIconSizeDp(secondaryFontSizeSp: Float): Float = secondaryFontSizeSp * ICON_TO_SECONDARY_RATIO

/**
 * 時計アイコンの大きさ比率。
 * 添える文字の大きさに対して0.90倍とし、文字とバランスよくひと回り小さく並べるために用いる。
 * ウィジェット本体および設定画面の見本でアイコンの寸法を決定する役割を持つ。
 */
private const val ICON_TO_SECONDARY_RATIO = 0.90f

/**
 * 縦並びにおける行と行の固定間隔(dp)。
 * 行間隔を文字サイズに比例させると、文字を大きくした際に間隔まで過大に広がり領域の高さを圧迫してしまうため、
 * 視覚的な区切りを一定に保ちつつ時刻を最大限大きく描くために10dpの固定値とする。
 * 縦並びおよび横並び右列の各行の配置位置決めと高さ計算において間隔の基準となる役割を持つ。
 */
private const val VERTICAL_LINE_SPACING_DP = 10f

// 月日と次の鳴動の大きさ。時刻に対するこの割合にする。
// 添える文字を小さくすると、その行が占める高さも下がって時刻と近づき、
// 空いたぶんだけ時刻を大きく解けるようになる
private const val SECONDARY_FONT_RATIO = 0.32f

// 添える文字（月日・次の鳴動）の大きさの下限と上限(dp)。
// 縦並びの解き直しと添える文字の大きさ算出で同じ範囲を共有する
private const val MIN_SECONDARY_FONT_SIZE_DP = 11f
private const val MAX_SECONDARY_FONT_SIZE_DP = 30f

// 時刻の文字の大きさの下限(dp)。
private const val MIN_TIME_FONT_SIZE_DP = 12f

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
 * ウィジェットで表示する各行の識別子。
 */
private enum class RowId { DATE, TIME, NEXT_RING }

/**
 * 実体基準の配置計算に用いる1行分の寸法情報（すべてdp）。
 */
private data class RowLayoutInfo(
    val id: RowId,
    val bodyHeight: Float,
    val topPadding: Float,
    val bottomPadding: Float,
)

/**
 * 縦並び配置において、利用可能な高さから時刻文字の大きさの上限(dp)を算出する関数。
 * 実機で実測した書体ごとの行高比率および文字実体の高さ比率に基づき、
 * 月日・時刻・次の鳴動の3行が利用可能な高さに収まる時刻の最大サイズ(dp)を求める。
 * 「次の鳴動の有無で時刻の大きさが変わらない」仕様を守るため、実際の表示行数にかかわらず常に3行分を前提として解く。
 * 添える文字のサイズが上限(30dp)や下限(11dp)に達した場合は、その限界値で固定して時刻サイズを解き直す。
 */
private fun calculateVerticalTimeHeightLimit(
    availableHeight: Float,
    fontStyle: WidgetFontStyle,
): Float {
    val availableForContent = availableHeight - 2f * VERTICAL_LINE_SPACING_DP
    val nextRingRowBodyHeightRatio = maxOf(fontStyle.nextRingBodyHeightRatio, ICON_TO_SECONDARY_RATIO)
    val secondaryHeightCoeff = fontStyle.dateTopPaddingRatio + fontStyle.dateBodyHeightRatio +
        fontStyle.lineHeightRatio - fontStyle.nextRingTopPaddingRatio
    val timeHeightCoeff = fontStyle.timeBodyHeightRatio

    // (a) まず上限・下限を考慮せず、月日と次の鳴動が時刻の0.32倍になる前提で解く
    val unconstrainedRatio = timeHeightCoeff + secondaryHeightCoeff * SECONDARY_FONT_RATIO
    val t0 = availableForContent / unconstrainedRatio

    // (b) t0から求めた月日の大きさs0が上限・下限に当たるか確認する
    val s0 = t0 * SECONDARY_FONT_RATIO
    return when {
        // (c) 上限を超える場合は、月日と次の鳴動を上限値で固定して時刻を解き直す
        s0 > MAX_SECONDARY_FONT_SIZE_DP -> {
            (availableForContent - MAX_SECONDARY_FONT_SIZE_DP * secondaryHeightCoeff) / timeHeightCoeff
        }
        // (d) 下限を下回る場合は、月日と次の鳴動を下限値で固定して時刻を解き直す
        s0 < MIN_SECONDARY_FONT_SIZE_DP -> {
            (availableForContent - MIN_SECONDARY_FONT_SIZE_DP * secondaryHeightCoeff) / timeHeightCoeff
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

    // 端末の文字サイズ設定（fontScale）の取得。
    // ウィジェットの描画領域は物理的なdpで決まっているため、文字サイズ設定をそのまま反映すると
    // 利用者が文字を大きく設定している端末では計算よりも大きく描画されて確実に領域からはみ出してしまう。
    // そのため、すべての寸法・配置計算をdp基準で行い、最後に文字サイズを (解いたdp / fontScale) でspへ変換する。
    // これにより、端末のfontScaleが何倍であっても、実際に描画される文字の大きさ（sp × fontScale）が解いたdpと完全に一致し、
    // どんな文字サイズ設定の端末でも確実に領域内に収めることができる。
    // なお、システムから0以下や極端に大きな異常値が返った場合に備え、0.5〜2.0の安全な範囲へ丸めて使用する。
    val rawFontScale = context.resources.configuration.fontScale
    val fontScale = rawFontScale.coerceIn(0.5f, 2.0f)

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

    val secondaryAvailableWidth: Float
    val timeFontSizeDp: Float

    if (isHorizontal) {
        // 横並びの場合：左に時刻、右に月日と次の鳴動を縦へ積む
        val leftAvailableWidth = w * 0.58f
        val rightAvailableWidth = w - leftAvailableWidth - HORIZONTAL_SPACING_DP
        secondaryAvailableWidth = rightAvailableWidth

        // 時刻の大きさ(dp)を解く。実体基準に基づき、左側の時刻行の全高比率はfontStyle.lineHeightRatioとなる
        val heightLimit = h / fontStyle.lineHeightRatio
        val leftWidthLimit = leftAvailableWidth * TIME_WIDTH_USAGE / timeWidthRatio
        val rightWidthLimit = rightAvailableWidth / (dateWidthRatio * SECONDARY_FONT_RATIO)
        val otherLimits = minOf(heightLimit, rightWidthLimit)
        // 時刻は必ず表示するため、下限12dpでも幅に入らない場合は幅に収まる大きさまで下げる
        timeFontSizeDp = minOf(otherLimits.coerceAtLeast(MIN_TIME_FONT_SIZE_DP), leftWidthLimit)
    } else {
        secondaryAvailableWidth = w
        // 縦に並べる場合（既定）：上から月日、時刻、次の鳴動の順で並べる
        // 実際に出すかどうかにかかわらず、常に3行ぶん（時刻＋月日＋次の鳴動）で必要な高さを計算する
        val heightLimit = calculateVerticalTimeHeightLimit(h, fontStyle)
        val widthLimit = w * TIME_WIDTH_USAGE / timeWidthRatio
        // 時刻は必ず表示するため、下限12dpでも幅に入らない場合は幅に収まる大きさまで下げる
        timeFontSizeDp = minOf(heightLimit.coerceAtLeast(MIN_TIME_FONT_SIZE_DP), widthLimit)
    }

    // 月日・次の鳴動の下限必要幅を算出し、下限の大きさでも幅に入らない行は出さない
    val minDateWidth = MIN_SECONDARY_FONT_SIZE_DP * dateWidthRatio
    val minNextRingWidth =
        MIN_SECONDARY_FONT_SIZE_DP * (nextRingWidthRatio + ICON_TO_SECONDARY_RATIO) + NEXT_RING_ICON_SPACING_DP
    val dateFitsWidth = secondaryAvailableWidth >= minDateWidth
    val nextRingFitsWidth = secondaryAvailableWidth >= minNextRingWidth

    val showDate: Boolean
    val showNextRing: Boolean
    if (isHorizontal) {
        // 横並びでは、高さ40dp以上なら月日と次の鳴動の両方、未満なら次の鳴動のみを基本としつつ幅判定を適用する
        showDate = (content.nextTime == null || h >= 40f) && dateFitsWidth
        showNextRing = (content.nextTime != null) && nextRingFitsWidth
    } else {
        // 縦並びでは、高さ56dp以上で月日、84dp以上で次の鳴動を表示可能としつつ幅判定を適用する
        showDate = (h >= 56f) && dateFitsWidth
        showNextRing = (h >= 84f && content.nextTime != null) && nextRingFitsWidth
    }

    // 添える文字（月日・次の鳴動）の大きさをdpで決定する。表示する行の幅制限に収める
    val baseSecondary =
        (timeFontSizeDp * SECONDARY_FONT_RATIO).coerceIn(MIN_SECONDARY_FONT_SIZE_DP, MAX_SECONDARY_FONT_SIZE_DP)
    val widthLimits = mutableListOf<Float>()
    if (showDate) {
        widthLimits.add(secondaryAvailableWidth / dateWidthRatio)
    }
    if (showNextRing && content.nextTime != null) {
        widthLimits.add(
            (secondaryAvailableWidth - NEXT_RING_ICON_SPACING_DP).coerceAtLeast(0f) /
                (nextRingWidthRatio + ICON_TO_SECONDARY_RATIO),
        )
    }
    val secondaryFontSizeDp = if (widthLimits.isEmpty()) {
        baseSecondary
    } else {
        minOf(baseSecondary, widthLimits.minOrNull() ?: baseSecondary)
    }

    val iconSizeDp = nextRingIconSizeDp(secondaryFontSizeDp)

    // dpで解いた大きさをfontScaleで割り、Glanceへ渡すspへ変換する
    val timeFontSize = (timeFontSizeDp / fontScale).sp
    val secondaryFontSize = (secondaryFontSizeDp / fontScale).sp
    val iconSize = iconSizeDp.dp
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

    // 実機計測した比率に基づいて各行の実体高と余白（すべてdp）を計算する
    val dateBodyHeight = fontStyle.dateBodyHeightRatio * secondaryFontSizeDp
    val dateTopPadding = fontStyle.dateTopPaddingRatio * secondaryFontSizeDp
    val dateBottomPadding =
        (fontStyle.lineHeightRatio - fontStyle.dateTopPaddingRatio - fontStyle.dateBodyHeightRatio) * secondaryFontSizeDp

    val timeBodyHeight = fontStyle.timeBodyHeightRatio * timeFontSizeDp
    val timeTopPadding = fontStyle.timeTopPaddingRatio * timeFontSizeDp
    val timeBottomPadding =
        (fontStyle.lineHeightRatio - fontStyle.timeTopPaddingRatio - fontStyle.timeBodyHeightRatio) * timeFontSizeDp

    val nextRingRowBodyHeightRatio = maxOf(fontStyle.nextRingBodyHeightRatio, ICON_TO_SECONDARY_RATIO)
    val nextRingBodyHeight = nextRingRowBodyHeightRatio * secondaryFontSizeDp
    val nextRingTopPadding = fontStyle.nextRingTopPaddingRatio * secondaryFontSizeDp
    val nextRingBottomPadding =
        (fontStyle.lineHeightRatio - fontStyle.nextRingTopPaddingRatio - nextRingRowBodyHeightRatio) * secondaryFontSizeDp

    var dateTop = 0.dp
    var timeTop = 0.dp
    var nextRingTop = 0.dp

    if (isHorizontal) {
        // 横並び配置：時刻（左列）は1行を実体基準で上下中央に配置
        val timeRequiredHeight = timeTopPadding + timeBodyHeight + timeBottomPadding
        timeTop = ((h - timeRequiredHeight) / 2f).coerceAtLeast(0f).dp

        // 右列：実際に描画する行だけを積み、実体間隔10dpで上下中央に配置
        val rightRows = mutableListOf<RowLayoutInfo>()
        if (showDate) {
            rightRows.add(RowLayoutInfo(RowId.DATE, dateBodyHeight, dateTopPadding, dateBottomPadding))
        }
        if (showNextRing && content.nextTime != null) {
            rightRows.add(RowLayoutInfo(RowId.NEXT_RING, nextRingBodyHeight, nextRingTopPadding, nextRingBottomPadding))
        }

        if (rightRows.isNotEmpty()) {
            val firstRRow = rightRows.first()
            val lastRRow = rightRows.last()
            val totalRBodiesHeight = rightRows.sumOf { it.bodyHeight.toDouble() }.toFloat() +
                (rightRows.size - 1) * VERTICAL_LINE_SPACING_DP
            val actualRRequiredHeight = firstRRow.topPadding + totalRBodiesHeight + lastRRow.bottomPadding
            val rContentTopOffset = (h - actualRRequiredHeight) / 2f

            var currentRBodyTop = rContentTopOffset + firstRRow.topPadding
            for (row in rightRows) {
                val top = (currentRBodyTop - row.topPadding).coerceAtLeast(0f).dp
                when (row.id) {
                    RowId.DATE -> dateTop = top
                    RowId.NEXT_RING -> nextRingTop = top
                    else -> {}
                }
                currentRBodyTop += row.bodyHeight + VERTICAL_LINE_SPACING_DP
            }
        }
    } else {
        // 縦並び配置：実際に描画する行だけを積み、実体間隔10dpで上下中央に配置
        val verticalRows = mutableListOf<RowLayoutInfo>()
        if (showDate) {
            verticalRows.add(RowLayoutInfo(RowId.DATE, dateBodyHeight, dateTopPadding, dateBottomPadding))
        }
        verticalRows.add(RowLayoutInfo(RowId.TIME, timeBodyHeight, timeTopPadding, timeBottomPadding))
        if (showNextRing && content.nextTime != null) {
            verticalRows.add(RowLayoutInfo(RowId.NEXT_RING, nextRingBodyHeight, nextRingTopPadding, nextRingBottomPadding))
        }

        val firstVRow = verticalRows.first()
        val lastVRow = verticalRows.last()
        val totalVBodiesHeight = verticalRows.sumOf { it.bodyHeight.toDouble() }.toFloat() +
            (verticalRows.size - 1) * VERTICAL_LINE_SPACING_DP
        val actualVRequiredHeight = firstVRow.topPadding + totalVBodiesHeight + lastVRow.bottomPadding
        val vContentTopOffset = (h - actualVRequiredHeight) / 2f

        var currentVBodyTop = vContentTopOffset + firstVRow.topPadding
        for (row in verticalRows) {
            val top = (currentVBodyTop - row.topPadding).coerceAtLeast(0f).dp
            when (row.id) {
                RowId.DATE -> dateTop = top
                RowId.TIME -> timeTop = top
                RowId.NEXT_RING -> nextRingTop = top
            }
            currentVBodyTop += row.bodyHeight + VERTICAL_LINE_SPACING_DP
        }
    }

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
            dateTop = dateTop,
            timeTop = timeTop,
            nextRingTop = nextRingTop,
            modifier = GlanceModifier.fillMaxSize()
                .padding(start = SHADOW_OFFSET, top = SHADOW_OFFSET),
        )
        WidgetLayer(
            content = content,
            style = baseStyle,
            isHorizontal = isHorizontal,
            showDate = showDate,
            showNextRing = showNextRing,
            dateTop = dateTop,
            timeTop = timeTop,
            nextRingTop = nextRingTop,
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
    dateTop: Dp,
    timeTop: Dp,
    nextRingTop: Dp,
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
        // 横並び配置：左に時刻、右に月日と次の鳴動を配置し、実体基準で上下中央に揃える
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = content.time,
                style = timeStyle,
                modifier = GlanceModifier.padding(top = timeTop),
                maxLines = 1,
            )
            if (showDate || (showNextRing && content.nextTime != null)) {
                Spacer(GlanceModifier.width(HORIZONTAL_SPACING))
                Box(
                    modifier = GlanceModifier.fillMaxHeight(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    if (showDate) {
                        Text(
                            text = content.date,
                            style = dateStyle,
                            modifier = GlanceModifier.padding(top = dateTop),
                            maxLines = 1,
                        )
                    }
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
    } else {
        // 縦並び配置（既定）：文字の実体位置に基づき、計算済みの各行の上部パディングでBoxへ配置する
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
