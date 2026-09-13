package com.marutyan.termalarm.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R

/**
 * IBM Plex Monoの太さをvariationSettingsで指定したFontを生成する。
 * 可変フォントの太さ(200〜500)を単一ファイルから柔軟に取り出すために用いる。
 */
fun ibmPlexMonoFont(weight: Int, fontWeight: FontWeight = FontWeight(weight)): Font = Font(
    resId = R.font.ibm_plex_mono_var,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/**
 * IBM Plex Monoの太さ200から500までを束ねたFontFamily。
 * 時刻や数字の描画において、ウェイトに応じた可変フォントグリフを適用するために用いる。
 */
val IbmPlexMono = FontFamily(
    ibmPlexMonoFont(200, FontWeight.W200),
    ibmPlexMonoFont(300, FontWeight.W300),
    ibmPlexMonoFont(400, FontWeight.W400),
    ibmPlexMonoFont(500, FontWeight.W500),
)

/**
 * 指定した太さ(200〜500)のIBM Plex Monoフォントファミリーを生成する。
 * 画面側で特定のウェイトをピンポイントで指定して描画したい場合に用いる。
 */
fun ibmPlexMonoFontFamily(weight: Int): FontFamily = FontFamily(
    ibmPlexMonoFont(weight),
)

// --- 基本テキストスタイル ---

/**
 * 時刻表示用の基本スタイル。
 * 可変フォントIBM Plex Monoの太さ200と等幅数字(tnum)を適用して時刻のちらつきを防ぐために用いる。
 */
val TimeStyle = TextStyle(
    fontFamily = IbmPlexMono,
    fontWeight = FontWeight.W200,
    fontFeatureSettings = "tnum",
    fontSize = 44.sp,
    lineHeight = 52.sp,
)

/**
 * 見出し用の基本スタイル。
 * 日本語文章を正しくレンダリングするため端末標準フォントを適用して画面の各見出しに用いる。
 */
val HeadlineStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 28.sp,
    lineHeight = 36.sp,
)

/**
 * 本文用の基本スタイル。
 * 日本語文章の可読性を確保するため端末標準フォントを適用して通常の説明文や項目テキストに用いる。
 */
val BodyStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
)

/**
 * 補助文字用の基本スタイル。
 * 端末標準フォントを適用し、最小可読サイズである11spを下限とする補助注記テキストに用いる。
 */
val CaptionStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 16.sp,
)

// --- Typography 拡張プロパティ ---

/** Typographyから時刻用の基本スタイルを取得する拡張プロパティ。 */
val Typography.time: TextStyle get() = TimeStyle

/** Typographyから見出し用の基本スタイルを取得する拡張プロパティ。 */
val Typography.headline: TextStyle get() = HeadlineStyle

/** Typographyから本文用の基本スタイルを取得する拡張プロパティ。 */
val Typography.body: TextStyle get() = BodyStyle

/** Typographyから補助文字用の基本スタイルを取得する拡張プロパティ。 */
val Typography.caption: TextStyle get() = CaptionStyle

/**
 * TermAlarm全体で使うTypography。
 * 時刻用には可変フォント(太さ200、等幅数字)を用い、文章には端末標準フォントを適用する。最小文字サイズは11spを保つ。
 */
val AppTypography = Typography(
    displayLarge = TimeStyle.copy(fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TimeStyle.copy(fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TimeStyle.copy(fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = HeadlineStyle.copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = HeadlineStyle.copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = HeadlineStyle.copy(fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = HeadlineStyle.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = HeadlineStyle.copy(fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = HeadlineStyle.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = BodyStyle.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = BodyStyle.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = BodyStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = CaptionStyle.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = CaptionStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = CaptionStyle.copy(fontSize = 11.sp, lineHeight = 16.sp),
)

// --- 画面互換用 TextStyle 拡張関数 ---

/**
 * 等幅数字(tabular figures)を有効にする拡張関数。
 * 桁の切り替わりによる文字幅の変動と画面のちらつきを防ぐために用いる。
 */
fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * 画面の主役となる特大の時刻表示スタイルを適用する拡張関数。
 * 太さ200の可変フォントIBM Plex Monoと等幅数字を反映するために用いる。
 */
fun TextStyle.heroClock(): TextStyle = copy(
    fontFamily = IbmPlexMono,
    fontWeight = FontWeight.W200,
    fontSize = 87.sp,
    lineHeight = 96.sp,
    fontFeatureSettings = "tnum",
)

/**
 * 円環などの図形内に収める時刻表示スタイルを適用する拡張関数。
 * 太さ200の可変フォントIBM Plex Monoと等幅数字を反映するために用いる。
 */
fun TextStyle.subHeroClock(): TextStyle = copy(
    fontFamily = IbmPlexMono,
    fontWeight = FontWeight.W200,
    fontSize = 79.sp,
    lineHeight = 88.sp,
    fontFeatureSettings = "tnum",
)

/**
 * アラーム一覧やタームカードに表示する時刻スタイルを適用する拡張関数。
 * 太さ200の可変フォントIBM Plex Monoと等幅数字を反映するために用いる。
 */
fun TextStyle.alarmCardClock(): TextStyle = copy(
    fontFamily = IbmPlexMono,
    fontWeight = FontWeight.W200,
    fontSize = 44.sp,
    lineHeight = 52.sp,
    fontFeatureSettings = "tnum",
)

/**
 * キーパッドでの時間入力中に数値を表示するスタイルを適用する拡張関数。
 * 1行に収まるサイズに調整しつつ太さ200のIBM Plex Monoと等幅数字を反映するために用いる。
 */
fun TextStyle.keypadInput(): TextStyle = copy(
    fontFamily = IbmPlexMono,
    fontWeight = FontWeight.W200,
    fontSize = 72.sp,
    lineHeight = 80.sp,
    fontFeatureSettings = "tnum",
)
