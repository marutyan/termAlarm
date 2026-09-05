package com.marutyan.termalarm.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.marutyan.termalarm.R

/**
 * Google Sans Flexの太さをvariationSettingsで指定したFontエントリを1つ作る。
 * Google Sans Flexは単一の可変フォントファイル(wght軸 1..1000)なので、FontWeightごとに
 * 同じファイルへ異なるvariationSettingsを結び付けてFontFamilyへ束ねる(docs/SPEC.md「フォント」)。
 */
private fun flexWeight(weight: Int, fontWeight: FontWeight) = Font(
    resId = R.font.google_sans_flex,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/**
 * アプリ全体で使うラテン文字・数字用フォント。Google Sans Flexは日本語グリフを持たないため、
 * 日本語の文字はAndroidのフォントフォールバック機構により自動的に端末標準の日本語フォント
 * (Noto Sans CJK JP)へ切り替わる。日本語用のフォールバックFontFamilyをここで明示する必要はない。
 */
val GoogleSansFlex = FontFamily(
    flexWeight(300, FontWeight.Light),
    flexWeight(400, FontWeight.Normal),
    flexWeight(500, FontWeight.Medium),
    flexWeight(600, FontWeight.SemiBold),
    flexWeight(700, FontWeight.Bold),
)

// Material3既定のタイポグラフィスケール(サイズ・行間・字間)はそのまま使い、フォントだけ差し替える基準
private val baseline = Typography()

/**
 * TermAlarm全体で使うTypography。Material3の既定スケールを保ったまま、
 * フォントファミリーだけをGoogle Sans Flexへ差し替えている。
 */
val AppTypography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = GoogleSansFlex),
    displayMedium = baseline.displayMedium.copy(fontFamily = GoogleSansFlex),
    displaySmall = baseline.displaySmall.copy(fontFamily = GoogleSansFlex),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = GoogleSansFlex),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = GoogleSansFlex),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = GoogleSansFlex),
    titleLarge = baseline.titleLarge.copy(fontFamily = GoogleSansFlex),
    titleMedium = baseline.titleMedium.copy(fontFamily = GoogleSansFlex),
    titleSmall = baseline.titleSmall.copy(fontFamily = GoogleSansFlex),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = GoogleSansFlex),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = GoogleSansFlex),
    bodySmall = baseline.bodySmall.copy(fontFamily = GoogleSansFlex),
    labelLarge = baseline.labelLarge.copy(fontFamily = GoogleSansFlex),
    labelMedium = baseline.labelMedium.copy(fontFamily = GoogleSansFlex),
    labelSmall = baseline.labelSmall.copy(fontFamily = GoogleSansFlex),
)

/**
 * 時刻の数字表示に使う等幅数字(tabular figures)を有効にする。
 * 桁数が変わるたびに文字幅が動いてアラーム一覧・編集画面の時刻表示がちらつくのを防ぐ(docs/SPEC.md「フォント」)。
 */
fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * 画面の主役になる時刻の文字。
 *
 * 純正の時計アプリを実機で撮り、数字の帯の高さを測って合わせている。
 * ストップウォッチの経過時間と世界時計の時刻はどちらも44dpだった。
 *
 * 61spで試したところ31dpにしかならず、純正の70%の大きさだった。
 * 実測から逆算して87spにしている。Material 3で最も大きいdisplayLarge(57sp)
 * でも足りないため、専用の大きさを持つ。
 */
fun TextStyle.heroClock(): TextStyle = copy(
    fontSize = 87.sp,
    lineHeight = 96.sp,
    fontFeatureSettings = "tnum",
)

/**
 * リングの中に収める時刻。純正のタイマーは高さ40dpだったので、それに合わせる。
 */
fun TextStyle.subHeroClock(): TextStyle = copy(
    fontSize = 79.sp,
    lineHeight = 88.sp,
    fontFeatureSettings = "tnum",
)

/**
 * アラーム一覧のカードに出す時刻。
 * このアプリは「7:00 – 9:00」のように時刻を2つ並べるため、画面いっぱいの大きさは使えない。
 * 純正のアラームは約33spだが、利用者の希望でひと回り大きくしている。
 */
fun TextStyle.alarmCardClock(): TextStyle = copy(
    fontSize = 44.sp,
    lineHeight = 52.sp,
    fontFeatureSettings = "tnum",
)

/**
 * タイマーの追加画面で、入力中の時間を出す文字。
 * 「00h 00m 00s」を1行に収める必要があるため、画面の主役の時刻ほどは大きくできない。
 * 数字6つと単位3つで、幅360dpの端末でもぎりぎり収まる値にしている。
 */
fun TextStyle.keypadInput(): TextStyle = copy(
    fontSize = 72.sp,
    lineHeight = 80.sp,
    fontFeatureSettings = "tnum",
)

/**
 * 使える幅に収まる最大の大きさで時刻を出すための文字。
 * 桁数は12/24時制や秒の有無で変わるので、決め打ちの大きさだと
 * ある時間帯だけはみ出したり、逆に空きが目立ったりする。
 *
 * 数字1文字の幅はおよそ文字サイズの0.55倍、コロンは0.28倍として見積もる。
 * 実測(純正は「6:31:20」7文字で幅180dp、高さ44dp)とほぼ一致する。
 */
fun TextStyle.fittingClock(availableWidth: Dp, charCount: Int): TextStyle {
    // コロンは数字より細い。7文字なら2つ、5文字なら1つ入る
    val colonCount = (charCount - 1) / 3
    val widthPerSp = (charCount - colonCount) * 0.55f + colonCount * 0.28f
    val size = (availableWidth.value / widthPerSp).coerceIn(48f, 120f).sp
    return copy(fontSize = size, lineHeight = size * 1.1f, fontFeatureSettings = "tnum")
}
