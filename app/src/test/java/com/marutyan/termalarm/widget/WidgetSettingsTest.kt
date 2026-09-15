package com.marutyan.termalarm.widget

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.unit.ColorProvider
import com.marutyan.termalarm.ui.theme.DynamicThemePreviewColors
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

    /**
     * 設定未保存時に視認性の高い暗色配色を既定とし、配置直後のウィジェットで表示崩れや読みにくさを防ぐことを守るためのテスト。
     */
    @Test
    fun widgetColorScheme_whenUnset_returnsDark() {
        val scheme = widgetColorScheme(emptyPreferences())
        assertEquals(WidgetColorScheme.DARK, scheme)
    }

    /**
     * 設定未保存時に最重要情報である「次の鳴動」にアクセントを当て、初期状態でもアラーム時刻を直感的に把握できる体験を守るためのテスト。
     */
    @Test
    fun widgetAccentTarget_whenUnset_returnsNextRing() {
        val target = widgetAccentTarget(emptyPreferences())
        assertEquals(WidgetAccentTarget.NEXT_RING, target)
    }

    /**
     * 設定未保存時に標準の文字の太さを既定とし、意図しない太字化によるレイアウト圧迫を防ぐことを守るためのテスト。
     */
    @Test
    fun widgetFontWeight_whenUnset_returnsNormal() {
        val weight = widgetFontWeight(emptyPreferences())
        assertEquals(WidgetFontWeight.NORMAL, weight)
    }

    /**
     * 設定未保存時に無地背景を既定とし、さまざまな壁紙の上でも文字コントラストを確保して時刻が読めることを守るためのテスト。
     */
    @Test
    fun widgetTransparent_whenUnset_returnsFalse() {
        val transparent = widgetTransparent(emptyPreferences())
        assertEquals(false, transparent)
    }

    /**
     * 不正または未知の配色名が保存されていた場合に安全に既定の暗色へフォールバックし、ウィジェットの描画エラーを防ぐことを守るためのテスト。
     */
    @Test
    fun widgetColorScheme_whenInvalidValue_fallsBackToDark() {
        val prefs = preferencesOf(WIDGET_COLOR_SCHEME_KEY to "UNKNOWN_SCHEME")
        val scheme = widgetColorScheme(prefs)
        assertEquals(WidgetColorScheme.DARK, scheme)
    }

    /**
     * 不正または未知のアクセント部位名が保存されていた場合に安全に既定の次の鳴動時刻へフォールバックし、アクセント色の消失を防ぐことを守るためのテスト。
     */
    @Test
    fun widgetAccentTarget_whenInvalidValue_fallsBackToNextRing() {
        val prefs = preferencesOf(WIDGET_ACCENT_TARGET_KEY to "UNKNOWN_TARGET")
        val target = widgetAccentTarget(prefs)
        assertEquals(WidgetAccentTarget.NEXT_RING, target)
    }

    /**
     * 不正または未知の太さ名が保存されていた場合に安全に既定の標準の太さへフォールバックし、Glanceの描画失敗を防ぐことを守るためのテスト。
     */
    @Test
    fun widgetFontWeight_whenInvalidValue_fallsBackToNormal() {
        val prefs = preferencesOf(WIDGET_FONT_WEIGHT_KEY to "UNKNOWN_WEIGHT")
        val weight = widgetFontWeight(prefs)
        assertEquals(WidgetFontWeight.NORMAL, weight)
    }

    /**
     * 文字の太さの選択肢がGlanceの描画制約である3段階に厳密に一致していることを固定し、非対応の太さ追加による表示の破綻を防ぐことを守るためのテスト。
     */
    @Test
    fun widgetFontWeight_hasExpectedThreeWeights() {
        val expected = listOf(
            WidgetFontWeight.NORMAL,
            WidgetFontWeight.MEDIUM,
            WidgetFontWeight.BOLD,
        )
        assertEquals(expected, WidgetFontWeight.entries)
    }

    /**
     * 旧バージョンのライトテーマ設定を引き継ぎ、アプリ更新後も明るい外観設定が維持されることを守るためのテスト。
     */
    @Test
    fun widgetColorScheme_whenLegacyThemeLight_migratesToLight() {
        val prefs = preferencesOf(WIDGET_THEME_KEY to "LIGHT")
        val scheme = widgetColorScheme(prefs)
        assertEquals(WidgetColorScheme.LIGHT, scheme)
    }

    /**
     * 旧バージョンのダイナミックカラー設定を新体系の端末色設定へと移行し、壁紙連動表示が維持されることを守るためのテスト。
     */
    @Test
    fun widgetColorScheme_whenLegacyThemeDynamic_migratesToSystem() {
        val prefs = preferencesOf(WIDGET_THEME_KEY to "DYNAMIC")
        val scheme = widgetColorScheme(prefs)
        assertEquals(WidgetColorScheme.SYSTEM, scheme)
    }

    /**
     * 旧バージョンのネイビー設定を新体系のダーク配色へ統合し、更新後も暗色系の落ち着いた外観が維持されることを守るためのテスト。
     */
    @Test
    fun widgetColorScheme_whenLegacyThemeNavy_migratesToDark() {
        val prefs = preferencesOf(WIDGET_THEME_KEY to "NAVY")
        val scheme = widgetColorScheme(prefs)
        assertEquals(WidgetColorScheme.DARK, scheme)
    }

    /**
     * 旧バージョンのブラック設定を新体系のダーク配色へ移行し、更新後も黒を基調とした表示が維持されることを守るためのテスト。
     */
    @Test
    fun widgetColorScheme_whenLegacyThemeBlack_migratesToDark() {
        val prefs = preferencesOf(WIDGET_THEME_KEY to "BLACK")
        val scheme = widgetColorScheme(prefs)
        assertEquals(WidgetColorScheme.DARK, scheme)
    }

    /**
     * 新旧両方の設定キーが存在する場合に新しい設定を優先し、旧キーの残存値によるユーザー設定の上書きを防ぐことを守るためのテスト。
     */
    @Test
    fun widgetColorScheme_whenBothNewAndLegacyKeysPresent_prefersNewKey() {
        val prefs = preferencesOf(
            WIDGET_COLOR_SCHEME_KEY to WidgetColorScheme.LIGHT.name,
            WIDGET_THEME_KEY to "BLACK",
        )
        val scheme = widgetColorScheme(prefs)
        assertEquals(WidgetColorScheme.LIGHT, scheme)
    }

    /**
     * 時刻がアクセント対象に指定された際、時刻のみにアクセント色が適用され、次の鳴動と日付は控えめな色で表示されるメリハリを守るためのテスト。
     */
    @Test
    fun colorOf_whenAccentTargetIsTime_appliesAccentToTimeAndSubtleToOthers() {
        val background = ColorProvider(Color(0xFF000001))
        val text = ColorProvider(Color(0xFF000002))
        val subtleText = ColorProvider(Color(0xFF000003))
        val accent = ColorProvider(Color(0xFF000004))
        val palette = WidgetPalette(
            background = background,
            text = text,
            subtleText = subtleText,
            accent = accent,
        )

        assertEquals(accent, palette.colorOf(WidgetAccentTarget.TIME, WidgetAccentTarget.TIME))
        assertEquals(subtleText, palette.colorOf(WidgetAccentTarget.NEXT_RING, WidgetAccentTarget.TIME))
        assertEquals(subtleText, palette.colorOf(WidgetAccentTarget.DATE, WidgetAccentTarget.TIME))
    }

    /**
     * 次の鳴動がアクセント対象に指定された際、次の鳴動のみにアクセント色が適用され、時刻は通常色、日付は控えめな色で表示される情報優先度を守るためのテスト。
     */
    @Test
    fun colorOf_whenAccentTargetIsNextRing_appliesAccentToNextRingAndFallbacksForOthers() {
        val background = ColorProvider(Color(0xFF000001))
        val text = ColorProvider(Color(0xFF000002))
        val subtleText = ColorProvider(Color(0xFF000003))
        val accent = ColorProvider(Color(0xFF000004))
        val palette = WidgetPalette(
            background = background,
            text = text,
            subtleText = subtleText,
            accent = accent,
        )

        assertEquals(text, palette.colorOf(WidgetAccentTarget.TIME, WidgetAccentTarget.NEXT_RING))
        assertEquals(accent, palette.colorOf(WidgetAccentTarget.NEXT_RING, WidgetAccentTarget.NEXT_RING))
        assertEquals(subtleText, palette.colorOf(WidgetAccentTarget.DATE, WidgetAccentTarget.NEXT_RING))
    }

    /**
     * 日付がアクセント対象に指定された際、日付のみにアクセント色が適用され、時刻は通常色、次の鳴動は控えめな色で表示される階層構造を守るためのテスト。
     */
    @Test
    fun colorOf_whenAccentTargetIsDate_appliesAccentToDateAndFallbacksForOthers() {
        val background = ColorProvider(Color(0xFF000001))
        val text = ColorProvider(Color(0xFF000002))
        val subtleText = ColorProvider(Color(0xFF000003))
        val accent = ColorProvider(Color(0xFF000004))
        val palette = WidgetPalette(
            background = background,
            text = text,
            subtleText = subtleText,
            accent = accent,
        )

        assertEquals(text, palette.colorOf(WidgetAccentTarget.TIME, WidgetAccentTarget.DATE))
        assertEquals(subtleText, palette.colorOf(WidgetAccentTarget.NEXT_RING, WidgetAccentTarget.DATE))
        assertEquals(accent, palette.colorOf(WidgetAccentTarget.DATE, WidgetAccentTarget.DATE))
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

    /**
     * 端末の色（ダイナミックカラー）プレビュー用3色の固定値が仕様通り定義されているかを検証するテスト。
     * 設定画面やウィジェットプレビューの表示色が意図せず変わるのを防ぐために用いる。
     */
    @Test
    fun dynamicThemePreviewColors_containsExpectedThreeColors() {
        val expected = listOf(
            Color(0xFFB79CE8),
            Color(0xFFE8A0B4),
            Color(0xFFF0C48A),
        )
        assertEquals(expected, DynamicThemePreviewColors)
    }
}

