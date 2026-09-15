package com.marutyan.termalarm.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceTheme
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import com.marutyan.termalarm.ui.theme.BlackOnSurface
import com.marutyan.termalarm.ui.theme.BlackPrimary
import com.marutyan.termalarm.ui.theme.BlackSubtleText
import com.marutyan.termalarm.ui.theme.BlackSurface
import com.marutyan.termalarm.ui.theme.LightOnSurface
import com.marutyan.termalarm.ui.theme.LightPrimary
import com.marutyan.termalarm.ui.theme.LightSubtleText
import com.marutyan.termalarm.ui.theme.LightSurface

/**
 * ウィジェットの配色の選択肢。
 * アプリ本体のテーマ設定とは独立してウィジェット外観を設定できるようにするために用いる。
 * 白・黒・端末の色の3種類から選ぶ。
 */
enum class WidgetColorScheme {
    /** 白を基調とした明るい配色。明るい画面構成にしたい場合に用いる。 */
    LIGHT,

    /** 黒を基調とした暗色配色。有機EL画面での省電力や落ち着いた表示にしたい場合に用いる。 */
    DARK,

    /** 端末のMaterial Youの動的カラーに連動する配色。システムの壁紙と調和させたい場合に用いる。 */
    SYSTEM,
}

/**
 * アクセント色を適用するウィジェット構成部位の選択肢。
 * ユーザーが最も目立たせたい部位を強調できるようにするために用いる。
 * 時刻・次の鳴動・月日の3部位から選択する。
 */
enum class WidgetAccentTarget {
    /** 時刻表示。現在時刻の数字を強調したい場合に用いる。 */
    TIME,

    /** 次の鳴動時刻。次にアラームが鳴る予定の時刻を最優先で把握したい場合に用いる。 */
    NEXT_RING,

    /** 日付と曜日表示。月日情報を際立たせたい場合に用いる。 */
    DATE,
}

/**
 * 数字と文字に適用する書体の選択肢。
 * RemoteViewsで動くウィジェット上で確実に表示可能なフォント群から選ばせるために用いる。
 * [family]には対応するGlanceのFontFamilyを保持する。
 */
enum class WidgetFontStyle(val family: FontFamily) {
    /** 標準書体。端末標準のゴシック体で、どの端末でも読みやすい表示にするために用いる。 */
    STANDARD(FontFamily.SansSerif),

    /** 等幅書体。文字幅が揃い、1分ごとの時刻更新時でも左右の揺れを防ぐために用いる。 */
    MONOSPACE(FontFamily.Monospace),

    /** 明朝書体。落ち着いた書籍風の佇まいにしたい場合に用いる。 */
    SERIF(FontFamily.Serif),
}

/**
 * 文字の太さの選択肢。
 * ウィジェットの文字にメリハリを付け、視認性や好みに応じた太さを選べるようにするために用いる。
 * [glanceWeight]には対応するGlanceのFontWeightを保持する。
 * GlanceのFontWeightは標準・中間・太字の3段階しか持たないため、細い側は用意しない。
 */
enum class WidgetFontWeight(val glanceWeight: FontWeight) {
    /** 標準の太さ。一般的な読みやすさを確保するために用いる。 */
    NORMAL(FontWeight.Normal),

    /** やや太い。標準では物足りないが太字ほど強くしたくない場合に用いる。 */
    MEDIUM(FontWeight.Medium),

    /** 太字。離れた位置や小さなサイズでもはっきりと読めるようにするために用いる。 */
    BOLD(FontWeight.Bold),
}

/**
 * ウィジェットを描画するための4色のパレット。
 * 背景、本文文字、控えめな文字、アクセントの各色を保持し、固定色と端末色を透過的に扱えるようにするために用いる。
 */
data class WidgetPalette(
    val background: ColorProvider,
    val text: ColorProvider,
    val subtleText: ColorProvider,
    val accent: ColorProvider,
)

/**
 * 部位ごとの文字色を解決する。
 * アクセントの当て先として選ばれている部位にはアクセント色を適用し、それ以外の部位は通常文字色または控えめ文字色を適用するために用いる。
 * TIMEは通常文字色、NEXT_RINGとDATEは控えめ文字色を返す。
 */
fun WidgetPalette.colorOf(part: WidgetAccentTarget, accentTarget: WidgetAccentTarget): ColorProvider {
    if (part == accentTarget) return accent
    return when (part) {
        WidgetAccentTarget.TIME -> text
        WidgetAccentTarget.NEXT_RING,
        WidgetAccentTarget.DATE -> subtleText
    }
}

/** 以前の版で配色を保存していた旧キー。新しい配色キーへの移行を行うために用いる。 */
val WIDGET_THEME_KEY: Preferences.Key<String> = stringPreferencesKey("widget_theme")

/** ウィジェットごとに選んだ配色を保存するキー。白・黒・端末の色を識別するために用いる。 */
val WIDGET_COLOR_SCHEME_KEY: Preferences.Key<String> = stringPreferencesKey("widget_color_scheme")

/** ウィジェットごとに選んだアクセント色の当て先を保存するキー。時刻・次の鳴動・月日のいずれかを識別するために用いる。 */
val WIDGET_ACCENT_TARGET_KEY: Preferences.Key<String> = stringPreferencesKey("widget_accent_target")

/** ウィジェットごとに選んだ書体を保存するキー。標準・等幅・明朝を識別するために用いる。 */
val WIDGET_FONT_STYLE_KEY: Preferences.Key<String> = stringPreferencesKey("widget_font_style")

/** ウィジェットごとに選んだ文字の太さを保存するキー。細め・標準・太字を識別するために用いる。 */
val WIDGET_FONT_WEIGHT_KEY: Preferences.Key<String> = stringPreferencesKey("widget_font_weight")

/** ウィジェットごとに選んだ背景の透明設定を保存するキー。trueなら透明、falseなら無地背景を適用するために用いる。 */
val WIDGET_TRANSPARENT_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("widget_transparent")

/**
 * 保存された設定からウィジェットの配色を解決する。
 * 新しいキーを優先し、未設定の場合は旧バージョンの設定値から移行する。未保存や未知の値は既定値のDARK（黒）へ戻す。
 */
fun widgetColorScheme(preferences: Preferences): WidgetColorScheme {
    val newSchemeName = preferences[WIDGET_COLOR_SCHEME_KEY]
    if (newSchemeName != null) {
        return runCatching { WidgetColorScheme.valueOf(newSchemeName) }
            .getOrDefault(WidgetColorScheme.DARK)
    }
    val legacyTheme = preferences[WIDGET_THEME_KEY]
    if (legacyTheme != null) {
        return when (legacyTheme) {
            "LIGHT" -> WidgetColorScheme.LIGHT
            "DYNAMIC" -> WidgetColorScheme.SYSTEM
            else -> WidgetColorScheme.DARK
        }
    }
    return WidgetColorScheme.DARK
}

/**
 * 保存された設定からアクセント色の当て先部位を解決する。
 * 未保存や未知の値の場合は既定値のNEXT_RING（次の鳴動）へ戻す。
 */
fun widgetAccentTarget(preferences: Preferences): WidgetAccentTarget {
    val name = preferences[WIDGET_ACCENT_TARGET_KEY] ?: return WidgetAccentTarget.NEXT_RING
    return runCatching { WidgetAccentTarget.valueOf(name) }.getOrDefault(WidgetAccentTarget.NEXT_RING)
}

/**
 * 保存された設定からウィジェットの書体を解決する。
 * 未保存や未知の値の場合は既定値のSTANDARD（標準）へ戻す。
 */
fun widgetFontStyle(preferences: Preferences): WidgetFontStyle {
    val name = preferences[WIDGET_FONT_STYLE_KEY] ?: return WidgetFontStyle.STANDARD
    return runCatching { WidgetFontStyle.valueOf(name) }.getOrDefault(WidgetFontStyle.STANDARD)
}

/**
 * 保存された設定から文字の太さを解決する。
 * 未保存や未知の値の場合は既定値のNORMAL（標準）へ戻す。
 */
fun widgetFontWeight(preferences: Preferences): WidgetFontWeight {
    val name = preferences[WIDGET_FONT_WEIGHT_KEY] ?: return WidgetFontWeight.NORMAL
    return runCatching { WidgetFontWeight.valueOf(name) }.getOrDefault(WidgetFontWeight.NORMAL)
}

/**
 * 保存された設定から背景の透明設定を解決する。
 * 未保存の場合は既定値のfalse（無地背景）を返す。
 */
fun widgetTransparent(preferences: Preferences): Boolean {
    return preferences[WIDGET_TRANSPARENT_KEY] ?: false
}

/** 透明な背景色。壁紙をそのまま透かすために用いる。 */
private val Transparent = Color(0x00000000)

/**
 * 保存された設定からウィジェットを描画する配色パレットを生成する。
 * 白・黒・端末の色の各配色に応じた色群を割り当て、背景透明設定時は壁紙の上でも読めるよう文字色を白系へ寄せる。
 * 端末の色はAndroid 12以降でのみ利用可能とし、それ未満では黒へフォールバックする。
 */
@Composable
fun widgetPalette(preferences: Preferences): WidgetPalette {
    val scheme = widgetColorScheme(preferences)
    val transparent = widgetTransparent(preferences)
    val base = when {
        scheme == WidgetColorScheme.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val colors = GlanceTheme.colors
            WidgetPalette(
                background = colors.surface,
                text = colors.onSurface,
                subtleText = colors.onSurfaceVariant,
                accent = colors.primary,
            )
        }
        scheme == WidgetColorScheme.LIGHT -> WidgetPalette(
            background = ColorProvider(LightSurface),
            text = ColorProvider(LightOnSurface),
            subtleText = ColorProvider(LightSubtleText),
            accent = ColorProvider(LightPrimary),
        )
        else -> WidgetPalette(
            background = ColorProvider(BlackSurface),
            text = ColorProvider(BlackOnSurface),
            subtleText = ColorProvider(BlackSubtleText),
            accent = ColorProvider(BlackPrimary),
        )
    }
    if (!transparent) return base
    // 壁紙の明るさは分からないため、透明のときは明るい配色でも白系の文字にして読めるようにする
    return base.copy(
        background = ColorProvider(Transparent),
        text = ColorProvider(BlackOnSurface),
        subtleText = ColorProvider(BlackOnSurface),
    )
}
