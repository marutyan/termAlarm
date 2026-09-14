package com.marutyan.termalarm.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceTheme
import androidx.glance.text.FontFamily
import androidx.glance.unit.ColorProvider
import com.marutyan.termalarm.domain.AppTheme
import com.marutyan.termalarm.ui.theme.BlackOnSurface
import com.marutyan.termalarm.ui.theme.BlackPrimary
import com.marutyan.termalarm.ui.theme.BlackSubtleText
import com.marutyan.termalarm.ui.theme.BlackSurface
import com.marutyan.termalarm.ui.theme.LightOnSurface
import com.marutyan.termalarm.ui.theme.LightPrimary
import com.marutyan.termalarm.ui.theme.LightSubtleText
import com.marutyan.termalarm.ui.theme.LightSurface
import com.marutyan.termalarm.ui.theme.NavyOnSurface
import com.marutyan.termalarm.ui.theme.NavyPrimary
import com.marutyan.termalarm.ui.theme.NavySubtleText
import com.marutyan.termalarm.ui.theme.NavySurface

/** ウィジェットごとに選んだ配色を保存する鍵。アプリ本体の配色とは別に選べる。 */
val WIDGET_THEME_KEY = stringPreferencesKey("widget_theme")

/** ウィジェットごとに選んだ背景の有無を保存する鍵。trueなら透明で、壁紙の上に文字だけが乗る。 */
val WIDGET_TRANSPARENT_KEY = booleanPreferencesKey("widget_transparent")

/** ウィジェットごとに選んだ数字の書体を保存する鍵。ウィジェットごとに別の書体を選べるようにするために用いる。 */
val WIDGET_FONT_STYLE_KEY = stringPreferencesKey("widget_font_style")

/**
 * ウィジェットの数字に用いる書体の選択肢。
 * ウィジェットはRemoteViewsの上に描かれ、アプリに同梱したフォントを読み込めない。
 * そのため端末に必ずある3種類の中から選ばせ、[family]をGlanceのFontFamilyへそのまま渡す。
 */
enum class WidgetFontStyle(val family: FontFamily) {
    /** 既定。端末の標準的な書体で、どの端末でも読みやすい */
    STANDARD(FontFamily.SansSerif),

    /** 等幅。数字の幅が揃うため、1分ごとに数字が変わっても左右に揺れない */
    MONOSPACE(FontFamily.Monospace),

    /** 明朝。落ち着いた見た目にしたい場合に選ぶ */
    SERIF(FontFamily.Serif),
}

/**
 * 保存された設定から、ウィジェットの数字に適用する書体を解決する。
 * 設定が無い場合や、以前の版で保存した未知の値だった場合は標準(STANDARD)へ戻す。
 */
fun widgetFontStyle(preferences: Preferences): WidgetFontStyle {
    val name = preferences[WIDGET_FONT_STYLE_KEY] ?: WidgetFontStyle.STANDARD.name
    return runCatching { WidgetFontStyle.valueOf(name) }.getOrDefault(WidgetFontStyle.STANDARD)
}

/**
 * ウィジェットを描くのに必要な色をまとめたもの。
 * 配色ごとに4色のColorProviderを持ち、ウィジェット側が固定色とダイナミックカラーを意識せずに描けるようにする。
 */
data class WidgetPalette(
    val background: ColorProvider,
    val text: ColorProvider,
    val subtleText: ColorProvider,
    val accent: ColorProvider,
)

/** 透明な背景を表す色。壁紙をそのまま透かすために用いる。 */
private val Transparent = Color(0x00000000)

/**
 * 保存された設定から、ウィジェットを描く色を決める。
 * 端末の色(DYNAMIC)が選ばれているときはGlanceTheme.colorsからダイナミックカラーを割り当て、
 * 透明を選んでいる場合は背景を透かし、壁紙の上でも読めるよう文字色を白系に寄せる。
 */
@Composable
fun widgetPalette(preferences: Preferences): WidgetPalette {
    val theme = runCatching { AppTheme.valueOf(preferences[WIDGET_THEME_KEY] ?: AppTheme.NAVY.name) }
        .getOrDefault(AppTheme.NAVY)
    val transparent = preferences[WIDGET_TRANSPARENT_KEY] ?: false
    val base = when {
        theme == AppTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val colors = GlanceTheme.colors
            WidgetPalette(
                background = colors.surface,
                text = colors.onSurface,
                subtleText = colors.onSurfaceVariant,
                accent = colors.primary,
            )
        }
        theme == AppTheme.LIGHT -> WidgetPalette(
            background = ColorProvider(LightSurface),
            text = ColorProvider(LightOnSurface),
            subtleText = ColorProvider(LightSubtleText),
            accent = ColorProvider(LightPrimary),
        )
        theme == AppTheme.BLACK -> WidgetPalette(
            background = ColorProvider(BlackSurface),
            text = ColorProvider(BlackOnSurface),
            subtleText = ColorProvider(BlackSubtleText),
            accent = ColorProvider(BlackPrimary),
        )
        else -> WidgetPalette(
            background = ColorProvider(NavySurface),
            text = ColorProvider(NavyOnSurface),
            subtleText = ColorProvider(NavySubtleText),
            accent = ColorProvider(NavyPrimary),
        )
    }
    if (!transparent) return base
    // 壁紙の明るさは分からないため、透明のときは明るい配色でも白系の文字にして読めるようにする
    return base.copy(
        background = ColorProvider(Transparent),
        text = ColorProvider(NavyOnSurface),
        subtleText = ColorProvider(NavyOnSurface),
    )
}
