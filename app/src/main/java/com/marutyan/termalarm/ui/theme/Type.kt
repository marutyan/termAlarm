package com.marutyan.termalarm.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
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
 * 純正の時計アプリを実機で撮って数字の高さを測ったところ、世界時計とストップウォッチが44dp、
 * タイマーの残り時間が40dpだった。Material 3で最も大きいdisplayLarge(57sp)でも数字は41dp
 * にしかならず、displayMedium(45sp)では32dpと純正の3/4以下になる。実測に合わせて専用の
 * 大きさを持つ。
 *
 * 数字の高さは文字サイズのおよそ0.72倍になるため、44dpには61sp、40dpには56spが要る。
 */
fun TextStyle.heroClock(): TextStyle = copy(
    fontSize = 61.sp,
    lineHeight = 68.sp,
    fontFeatureSettings = "tnum",
)

/** 主役の時刻より一段小さい表示。タイマーの残り時間など */
fun TextStyle.subHeroClock(): TextStyle = copy(
    fontSize = 56.sp,
    lineHeight = 62.sp,
    fontFeatureSettings = "tnum",
)
