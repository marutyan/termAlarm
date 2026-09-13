package com.marutyan.termalarm.widget

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

/**
 * ウィジェットを描くのに必要な色をまとめたもの。
 * 配色ごとに4色だけを持ち、ウィジェット側が配色を意識せずに描けるようにする。
 */
data class WidgetPalette(
    val background: Color,
    val text: Color,
    val subtleText: Color,
    val accent: Color,
)

/** 透明な背景を表す色。壁紙をそのまま透かすために用いる。 */
private val Transparent = Color(0x00000000)

/**
 * 保存された設定から、ウィジェットを描く色を決める。
 * 透明を選んでいる場合は背景を透かし、壁紙の上でも読めるよう文字を明るい側へ寄せる。
 */
fun widgetPalette(preferences: Preferences): WidgetPalette {
    val theme = runCatching { AppTheme.valueOf(preferences[WIDGET_THEME_KEY] ?: AppTheme.NAVY.name) }
        .getOrDefault(AppTheme.NAVY)
    val transparent = preferences[WIDGET_TRANSPARENT_KEY] ?: false
    val base = when (theme) {
        AppTheme.LIGHT -> WidgetPalette(LightSurface, LightOnSurface, LightSubtleText, LightPrimary)
        AppTheme.BLACK -> WidgetPalette(BlackSurface, BlackOnSurface, BlackSubtleText, BlackPrimary)
        else -> WidgetPalette(NavySurface, NavyOnSurface, NavySubtleText, NavyPrimary)
    }
    if (!transparent) return base
    // 壁紙の明るさは分からないため、透明のときは明るい配色でも白系の文字にして読めるようにする
    return base.copy(
        background = Transparent,
        text = NavyOnSurface,
        subtleText = NavyOnSurface,
    )
}
