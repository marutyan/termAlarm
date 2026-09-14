package com.marutyan.termalarm.widget

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ウィジェットの設定値の読み出しと更新タイマー時刻計算を検証するテスト。
 */
class WidgetSettingsTest {

    @Test
    fun widgetFontStyle_whenUnset_returnsStandard() {
        val style = widgetFontStyle(emptyPreferences())
        assertEquals(WidgetFontStyle.STANDARD, style)
    }

    @Test
    fun widgetFontStyle_whenMonospace_returnsMonospace() {
        val prefs = preferencesOf(WIDGET_FONT_STYLE_KEY to WidgetFontStyle.MONOSPACE.name)
        val style = widgetFontStyle(prefs)
        assertEquals(WidgetFontStyle.MONOSPACE, style)
    }

    @Test
    fun widgetFontStyle_whenInvalidValue_fallsBackToStandard() {
        val prefs = preferencesOf(WIDGET_FONT_STYLE_KEY to "UNKNOWN_FONT")
        val style = widgetFontStyle(prefs)
        assertEquals(WidgetFontStyle.STANDARD, style)
    }

    @Test
    fun nextMinuteEpochMillis_atOneSecond_advancesToNextMinuteHead() {
        // 00:00:01.000 の場合、次の分の頭は 00:01:00.000 (60,000ms)
        val result = WidgetUpdateScheduler.nextMinuteEpochMillis(1_000L)
        assertEquals(60_000L, result)
    }

    @Test
    fun nextMinuteEpochMillis_atFiftyNineSeconds_advancesToNextMinuteHead() {
        // 00:00:59.999 の場合、次の分の頭は 00:01:00.000 (60,000ms)
        val result = WidgetUpdateScheduler.nextMinuteEpochMillis(59_999L)
        assertEquals(60_000L, result)
    }

    @Test
    fun nextMinuteEpochMillis_exactlyAtMinuteBoundary_advancesToNextMinuteHead() {
        // 00:01:00.000 ちょうどのときは、次の分の頭 00:02:00.000 (120,000ms) へ予約する
        val result = WidgetUpdateScheduler.nextMinuteEpochMillis(60_000L)
        assertEquals(120_000L, result)
    }
}
