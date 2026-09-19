package com.marutyan.termalarm.widget

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.marutyan.termalarm.ui.theme.DynamicThemePreviewColors
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun `ふつうの日は翌日の0時00分を返す`() {
        // 2026-09-19 04:31 (Asia/Tokyo) → 2026-09-20 00:00 (Asia/Tokyo)
        val zone = ZoneId.of("Asia/Tokyo")
        val now = ZonedDateTime.of(2026, 9, 19, 4, 31, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 9, 20, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        val actual = WidgetUpdateScheduler.nextMidnightEpochMillis(now)

        assertEquals(expected, actual)
    }

    @Test
    fun `0時00分ちょうどのときは翌日の0時00分を返す`() {
        // 2026-09-20 00:00:00.000 (Asia/Tokyo) → 2026-09-21 00:00 (Asia/Tokyo)
        val zone = ZoneId.of("Asia/Tokyo")
        val now = ZonedDateTime.of(2026, 9, 20, 0, 0, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 9, 21, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        val actual = WidgetUpdateScheduler.nextMidnightEpochMillis(now)

        assertEquals(expected, actual)
    }

    @Test
    fun `23時59分59秒999ミリ秒のときは翌日の0時00分を返す`() {
        // 2026-09-20 23:59:59.999 (Asia/Tokyo) → 2026-09-21 00:00 (Asia/Tokyo)
        val zone = ZoneId.of("Asia/Tokyo")
        val now = ZonedDateTime.of(2026, 9, 20, 23, 59, 59, 999_000_000, zone)
        val expected = ZonedDateTime.of(2026, 9, 21, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        val actual = WidgetUpdateScheduler.nextMidnightEpochMillis(now)

        assertEquals(expected, actual)
    }

    @Test
    fun `夏時間の切り替え日を含む地域で翌日0時までの時間が24時間でないことを確かめる`() {
        // America/New_York の 2026-11-01 は夏時間終了日（秋の切り替えで1日が25時間になる日）
        val zone = ZoneId.of("America/New_York")
        val startOfDay = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)
        val expectedMidnight = ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        val actualMidnight = WidgetUpdateScheduler.nextMidnightEpochMillis(startOfDay)

        assertEquals(expectedMidnight, actualMidnight)

        // 2026-11-01 0:00 から 2026-11-02 0:00 までの実時間が24時間（86,400,000ミリ秒）ではないことを確かめる
        val durationMillis = actualMidnight - startOfDay.toInstant().toEpochMilli()
        val twentyFourHoursMillis = 24L * 60L * 60L * 1000L
        val twentyFiveHoursMillis = 25L * 60L * 60L * 1000L

        assertNotEquals(twentyFourHoursMillis, durationMillis)
        assertEquals(twentyFiveHoursMillis, durationMillis)
    }

    @Test
    fun `夏時間開始日（春の切り替え）で翌日0時までの間隔が23時間であることを確かめる`() {
        // America/New_York の 2026-03-08 00:00 → 2026-03-09 00:00 で、間隔が 23 時間（1380 分）
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, zone)
        val expectedMidnight = ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        val actualMidnight = WidgetUpdateScheduler.nextMidnightEpochMillis(now)

        assertEquals(expectedMidnight, actualMidnight)

        val durationMillis = actualMidnight - now.toInstant().toEpochMilli()
        val expectedTwentyThreeHoursMillis = 82_800_000L // 23時間 = 1380分 = 82,800,000ミリ秒
        assertEquals(expectedTwentyThreeHoursMillis, durationMillis)
    }

    @Test
    fun `夏時間開始により0時が存在しない地域で開始時刻の1時00分を返すことを確かめる`() {
        // America/Santiago の 2026-09-05 23:30 → 2026-09-06 01:00（この地域ではこの日の 0:00 が存在しない）
        val zone = ZoneId.of("America/Santiago")
        val now = ZonedDateTime.of(2026, 9, 5, 23, 30, 0, 0, zone)
        val expectedMidnight = ZonedDateTime.of(2026, 9, 6, 1, 0, 0, 0, zone).toInstant().toEpochMilli()

        val actualMidnight = WidgetUpdateScheduler.nextMidnightEpochMillis(now)

        assertEquals(expectedMidnight, actualMidnight)
    }

    @Test
    fun `次の鳴動が0時より前にあるときは鳴動時刻プラス1秒を予約時刻とする`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val now = ZonedDateTime.of(2026, 9, 19, 12, 0, 0, 0, zone)
        val nextAlarm = ZonedDateTime.of(2026, 9, 19, 21, 30, 0, 0, zone)
        val expected = nextAlarm.toInstant().toEpochMilli() + 1_000L

        val actual = WidgetUpdateScheduler.calculateNextRefreshEpochMillis(now, nextAlarm)

        assertEquals(expected, actual)
    }

    @Test
    fun `次の鳴動が0時より後または無いときは0時を予約時刻とする`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val now = ZonedDateTime.of(2026, 9, 19, 12, 0, 0, 0, zone)
        val nextAlarmTomorrow = ZonedDateTime.of(2026, 9, 20, 7, 0, 0, 0, zone)
        val expectedMidnight = ZonedDateTime.of(2026, 9, 20, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        // 翌朝鳴動の場合は0:00が先に来るため0:00が選ばれる
        val actualTomorrow = WidgetUpdateScheduler.calculateNextRefreshEpochMillis(now, nextAlarmTomorrow)
        assertEquals(expectedMidnight, actualTomorrow)

        // 次の鳴動予定が無い（null）場合も0:00が選ばれる
        val actualNull = WidgetUpdateScheduler.calculateNextRefreshEpochMillis(now, null)
        assertEquals(expectedMidnight, actualNull)
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
