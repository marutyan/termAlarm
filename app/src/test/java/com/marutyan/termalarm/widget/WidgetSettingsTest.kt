package com.marutyan.termalarm.widget

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.marutyan.termalarm.ui.theme.DynamicThemePreviewColors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ウィジェットの設定値の読み出しと、更新タイマーの時刻計算を確かめるテスト。
 *
 * 設定は「背景」「時刻の色」「書体」「太さ」の4つだけで、月日と次の鳴動の色は白で固定する。
 * 未保存や壊れた値でも必ず既定へ戻ることを押さえておかないと、
 * 置いた直後や設定が壊れたときにウィジェットが描けなくなる。
 */
class WidgetSettingsTest {

    @Test
    fun `未保存のときは既定値になる`() {
        val prefs = emptyPreferences()
        assertEquals(WidgetBackgroundStyle.FILLED, widgetBackgroundStyle(prefs))
        assertEquals(WidgetTimeColor.WHITE, widgetTimeColor(prefs))
        assertEquals(WidgetFontStyle.STANDARD, widgetFontStyle(prefs))
        assertEquals(WidgetFontWeight.NORMAL, widgetFontWeight(prefs))
    }

    @Test
    fun `保存された値がそのまま返る`() {
        val prefs = preferencesOf(
            WIDGET_BACKGROUND_STYLE_KEY to WidgetBackgroundStyle.TRANSPARENT.name,
            WIDGET_TIME_COLOR_KEY to WidgetTimeColor.SYSTEM.name,
            WIDGET_FONT_STYLE_KEY to WidgetFontStyle.MONOSPACE.name,
            WIDGET_FONT_WEIGHT_KEY to WidgetFontWeight.BOLD.name,
        )
        assertEquals(WidgetBackgroundStyle.TRANSPARENT, widgetBackgroundStyle(prefs))
        assertEquals(WidgetTimeColor.SYSTEM, widgetTimeColor(prefs))
        assertEquals(WidgetFontStyle.MONOSPACE, widgetFontStyle(prefs))
        assertEquals(WidgetFontWeight.BOLD, widgetFontWeight(prefs))
    }

    @Test
    fun `知らない値が入っていても既定へ戻る`() {
        // 版が変わって選択肢の名前が変わったときでも、描けなくならないようにする
        val prefs = preferencesOf(
            WIDGET_BACKGROUND_STYLE_KEY to "UNKNOWN",
            WIDGET_TIME_COLOR_KEY to "UNKNOWN",
            WIDGET_FONT_STYLE_KEY to "UNKNOWN",
            WIDGET_FONT_WEIGHT_KEY to "UNKNOWN",
        )
        assertEquals(WidgetBackgroundStyle.FILLED, widgetBackgroundStyle(prefs))
        assertEquals(WidgetTimeColor.WHITE, widgetTimeColor(prefs))
        assertEquals(WidgetFontStyle.STANDARD, widgetFontStyle(prefs))
        assertEquals(WidgetFontWeight.NORMAL, widgetFontWeight(prefs))
    }

    @Test
    fun `選択肢の並びを固定する`() {
        // 画面のボタンの数と順番をこの並びに合わせてあるため、増減に気付けるようにする
        assertEquals(
            listOf(WidgetBackgroundStyle.TRANSPARENT, WidgetBackgroundStyle.FILLED),
            WidgetBackgroundStyle.entries,
        )
        assertEquals(
            listOf(WidgetTimeColor.WHITE, WidgetTimeColor.BLACK, WidgetTimeColor.SYSTEM),
            WidgetTimeColor.entries,
        )
        assertEquals(
            listOf(WidgetFontStyle.STANDARD, WidgetFontStyle.MONOSPACE, WidgetFontStyle.SERIF),
            WidgetFontStyle.entries,
        )
        // Glanceが持つ太さはこの3段階しかない
        assertEquals(
            listOf(WidgetFontWeight.NORMAL, WidgetFontWeight.MEDIUM, WidgetFontWeight.BOLD),
            WidgetFontWeight.entries,
        )
    }

    @Test
    fun `以前の版の設定は読まない`() {
        // 作り直しが続いたため引き継がない。古い鍵しか無いときは既定から始める
        val legacy = preferencesOf(
            stringPreferencesKey("widget_theme") to "LIGHT",
            stringPreferencesKey("widget_time_color") to "ACCENT",
            booleanPreferencesKey("widget_transparent") to true,
        )
        assertEquals(WidgetTimeColor.WHITE, widgetTimeColor(legacy))
        assertEquals(WidgetBackgroundStyle.FILLED, widgetBackgroundStyle(legacy))
    }

    @Test
    fun `次の分の頭へ更新を予約する`() {
        // 00:00:01.000 の場合、次の分の頭は 00:01:00.000 (60,000ms)
        assertEquals(60_000L, WidgetUpdateScheduler.nextMinuteEpochMillis(1_000L))
        // 00:00:59.999 でも同じ
        assertEquals(60_000L, WidgetUpdateScheduler.nextMinuteEpochMillis(59_999L))
        // ちょうど分の頭のときは、次の分へ送る
        assertEquals(120_000L, WidgetUpdateScheduler.nextMinuteEpochMillis(60_000L))
    }

    /**
     * 端末の色を表す見本の3色が変わっていないことを確かめる。
     * 設定画面の「システム」の丸に使っており、意図せず色が変わるのを防ぐ。
     */
    @Test
    fun `端末の色の見本は3色で固定する`() {
        val expected = listOf(
            Color(0xFFB79CE8),
            Color(0xFFE8A0B4),
            Color(0xFFF0C48A),
        )
        assertEquals(expected, DynamicThemePreviewColors)
    }
}
