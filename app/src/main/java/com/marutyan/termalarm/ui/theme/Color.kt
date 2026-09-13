package com.marutyan.termalarm.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Material3のColorSchemeに収まらないアプリ固有の色を保持するデータクラス。
 * 目盛や罫線、薄い文字などアプリ独自コンポーネントの色を一元管理するために用いる。
 */
@Immutable
data class CustomColors(
    /** 罫線の色。セクションやリストの区切り線描画に用いる。 */
    val divider: Color,
    /** 薄い文字の色。秒数や注記など優先度の低いテキスト描画に用いる。 */
    val subtleText: Color,
    /** 鳴り終わった目盛の色。経過したタームのスロットを塗り分けるために用いる。 */
    val scalePast: Color,
    /** まだ鳴っていない目盛の色。未到来のタームスロットを塗り分けるために用いる。 */
    val scaleUpcoming: Color,
)

// --- NAVY 配色定数 ---

/** NAVY配色の画面背景色。アプリの基調となる濃紺画面を構成するために用いる。 */
val NavySurface = Color(0xFF0B1530)

/** NAVY配色のカード背景色。画面背景より一段明るいカード領域を表現するために用いる。 */
val NavySurfaceContainer = Color(0xFF142449)

/** NAVY配色のカード枠線色。カードの外枠をくっきりと区切るために用いる。 */
val NavyOutlineVariant = Color(0xFF1E3163)

/** NAVY配色の弱い枠線色。控えめな境界線を引くために用いる。 */
val NavyOutline = Color(0xFF27407C)

/** NAVY配色の罫線色。リストや区切り線の描画に用いる。 */
val NavyDivider = Color(0xFF172A55)

/** NAVY配色の主役色。次の鳴動時刻など最も強調したい要素を際立たせるミントグリーンとして用いる。 */
val NavyPrimary = Color(0xFF8CDDC2)

/** NAVY配色の主役色上の文字色。主役色の塗り潰し上で可読性を確保するために用いる。 */
val NavyOnPrimary = Color(0xFF0B1530)

/** NAVY配色の主役の弱い背景色。主役色に関連する強調背景を控えめに示すために用いる。 */
val NavyPrimaryContainer = Color(0xFF123A3C)

/** NAVY配色の注意色。警告や注意を喚起する淡い黄色として用いる。 */
val NavyTertiary = Color(0xFFFEE48F)

/** NAVY配色の本文文字色。十分なコントラストで主要テキストを読ませるために用いる。 */
val NavyOnSurface = Color(0xFFE8EEF9)

/** NAVY配色の副次文字色。本文より一段控えめな補助テキストを表示するために用いる。 */
val NavyOnSurfaceVariant = Color(0xFFA9C0E4)

/** NAVY配色の薄い文字色。秒表示やラベルなど補助的な文字を控えめに示すために用いる。 */
val NavySubtleText = Color(0xFF8AA0C9)

/** NAVY配色の鳴り終わった目盛色。経過したタームスロットを落ち着いた色で示すために用いる。 */
val NavyScalePast = Color(0xFF2E5A57)

/** NAVY配色のまだ鳴っていない目盛色。未来のタームスロットを暗い色で示すために用いる。 */
val NavyScaleUpcoming = Color(0xFF16274F)

// --- LIGHT 配色定数 ---

/** LIGHT配色の画面背景色。明るく清潔感のある背景領域を構成するために用いる。 */
val LightSurface = Color(0xFFF5F7FB)

/** LIGHT配色のカード背景色。画面背景に対して白く浮かび上がるカード領域を表現するために用いる。 */
val LightSurfaceContainer = Color(0xFFFFFFFF)

/** LIGHT配色のカード枠線色。明るい背景上でカードの外枠を区切るために用いる。 */
val LightOutlineVariant = Color(0xFFE4E9F2)

/** LIGHT配色の弱い枠線色。控えめな境界線を引くために用いる。 */
val LightOutline = Color(0xFFCBD5E6)

/** LIGHT配色の罫線色。明るい画面での区切り線描画に用いる。 */
val LightDivider = Color(0xFFE4E9F2)

/** LIGHT配色の主役色。白地でのコントラスト基準(4.5:1)を満たすよう調整された深緑として用いる。 */
val LightPrimary = Color(0xFF0B7A64)

/** LIGHT配色の主役色上の文字色。深緑の主役色上で十分な可読性を確保する白として用いる。 */
val LightOnPrimary = Color(0xFFFFFFFF)

/** LIGHT配色の主役の弱い背景色。主役色の淡いハイライト領域を表現するために用いる。 */
val LightPrimaryContainer = Color(0xFFE2F4EE)

/** LIGHT配色の注意色。明るい背景でも視認性の高い注意喚起色として用いる。 */
val LightTertiary = Color(0xFFB54708)

/** LIGHT配色の本文文字色。高い視認性を保つ濃色テキストとして用いる。 */
val LightOnSurface = Color(0xFF101828)

/** LIGHT配色の副次文字色。本文より一段控えめな文字色として用いる。 */
val LightOnSurfaceVariant = Color(0xFF475467)

/** LIGHT配色の薄い文字色。補助的な情報を控えめに表示するために用いる。 */
val LightSubtleText = Color(0xFF5D6B82)

/** LIGHT配色の鳴り終わった目盛色。経過したタームスロットを示す明るい青緑として用いる。 */
val LightScalePast = Color(0xFF9FD3C4)

/** LIGHT配色のまだ鳴っていない目盛色。未到来のタームスロットを示す淡いグレーとして用いる。 */
val LightScaleUpcoming = Color(0xFFE4E9F2)

// --- BLACK 配色定数 ---

/** BLACK配色の画面背景色。有機EL画面で省電力となる純黒に近い背景を構成するために用いる。 */
val BlackSurface = Color(0xFF0A0A0A)

/** BLACK配色のカード背景色。背景とわずかに差を付けた暗色カード領域を表現するために用いる。 */
val BlackSurfaceContainer = Color(0xFF111111)

/** BLACK配色のカード枠線色。暗色カードの輪郭を明瞭にするために用いる。 */
val BlackOutlineVariant = Color(0xFF1F1F1F)

/** BLACK配色の弱い枠線色。控えめな境界線を引くために用いる。 */
val BlackOutline = Color(0xFF262626)

/** BLACK配色の罫線色。暗色画面での区切り線描画に用いる。 */
val BlackDivider = Color(0xFF161616)

/** BLACK配色の主役色。漆黒に鮮明に映える黄緑色として主要要素を強調するために用いる。 */
val BlackPrimary = Color(0xFFA3E635)

/** BLACK配色の主役色上の文字色。明るい黄緑の上で視認性を保つ暗色文字として用いる。 */
val BlackOnPrimary = Color(0xFF0A0A0A)

/** BLACK配色の主役の弱い背景色。主役色の系統で控えめな領域を塗り分けるために用いる。 */
val BlackPrimaryContainer = Color(0xFF1C2410)

/** BLACK配色の注意色。黒背景上で明瞭に注意を促すオレンジ色として用いる。 */
val BlackTertiary = Color(0xFFF59E0B)

/** BLACK配色の本文文字色。暗色背景上でまぶしさを抑えつつ読みやすい淡いグレーとして用いる。 */
val BlackOnSurface = Color(0xFFE5E5E5)

/** BLACK配色の副次文字色。本文より一段控えめな補助テキストを表示するために用いる。 */
val BlackOnSurfaceVariant = Color(0xFFA3A3A3)

/** BLACK配色の薄い文字色。秒表示やラベルなど補助的な文字を控えめに示すために用いる。 */
val BlackSubtleText = Color(0xFF858585)

/** BLACK配色の鳴り終わった目盛色。経過したタームスロットを落ち着いた黄緑系暗色で示すために用いる。 */
val BlackScalePast = Color(0xFF3F4A1C)

/** BLACK配色のまだ鳴っていない目盛色。未到来のタームスロットを暗いグレーで示すために用いる。 */
val BlackScaleUpcoming = Color(0xFF1C1C1C)

// --- ColorScheme & CustomColors 定義 ---

/** NAVY配色のMaterial3 ColorScheme。標準Materialコンポーネントへ濃紺テーマを適用するために用いる。 */
val NavyColorScheme: ColorScheme = darkColorScheme(
    primary = NavyPrimary,
    onPrimary = NavyOnPrimary,
    primaryContainer = NavyPrimaryContainer,
    tertiary = NavyTertiary,
    background = NavySurface,
    surface = NavySurface,
    surfaceContainer = NavySurfaceContainer,
    outline = NavyOutline,
    outlineVariant = NavyOutlineVariant,
    onSurface = NavyOnSurface,
    onSurfaceVariant = NavyOnSurfaceVariant,
)

/** NAVY配色のアプリ固有色。目盛や罫線などの独自コンポーネントへ色を提供するために用いる。 */
val NavyCustomColors = CustomColors(
    divider = NavyDivider,
    subtleText = NavySubtleText,
    scalePast = NavyScalePast,
    scaleUpcoming = NavyScaleUpcoming,
)

/** LIGHT配色のMaterial3 ColorScheme。標準Materialコンポーネントへ明るいテーマを適用するために用いる。 */
val LightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    tertiary = LightTertiary,
    background = LightSurface,
    surface = LightSurface,
    surfaceContainer = LightSurfaceContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
)

/** LIGHT配色のアプリ固有色。目盛や罫線などの独自コンポーネントへ色を提供するために用いる。 */
val LightCustomColors = CustomColors(
    divider = LightDivider,
    subtleText = LightSubtleText,
    scalePast = LightScalePast,
    scaleUpcoming = LightScaleUpcoming,
)

/** BLACK配色のMaterial3 ColorScheme。標準Materialコンポーネントへ黒基調テーマを適用するために用いる。 */
val BlackColorScheme: ColorScheme = darkColorScheme(
    primary = BlackPrimary,
    onPrimary = BlackOnPrimary,
    primaryContainer = BlackPrimaryContainer,
    tertiary = BlackTertiary,
    background = BlackSurface,
    surface = BlackSurface,
    surfaceContainer = BlackSurfaceContainer,
    outline = BlackOutline,
    outlineVariant = BlackOutlineVariant,
    onSurface = BlackOnSurface,
    onSurfaceVariant = BlackOnSurfaceVariant,
)

/** BLACK配色のアプリ固有色。目盛や罫線などの独自コンポーネントへ色を提供するために用いる。 */
val BlackCustomColors = CustomColors(
    divider = BlackDivider,
    subtleText = BlackSubtleText,
    scalePast = BlackScalePast,
    scaleUpcoming = BlackScaleUpcoming,
)

/**
 * Dynamic ColorのColorSchemeからアプリ固有色を生成する。
 * 壁紙由来の色と調和するよう、仕様で定められた対応スロットの色をCustomColorsへ割り当てる。
 */
fun dynamicCustomColors(colorScheme: ColorScheme): CustomColors = CustomColors(
    divider = colorScheme.surfaceContainerHigh,
    subtleText = colorScheme.onSurfaceVariant,
    scalePast = colorScheme.primaryContainer,
    scaleUpcoming = colorScheme.surfaceContainerHighest,
)
