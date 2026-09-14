package com.marutyan.termalarm.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.domain.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * テーマの配色、コントラスト比、フォント設定を検証するテストクラス。
 * WCAG基準を満たすコントラストの維持や各テーマでの色スロット解決の正確性を保証するために用いる。
 */
class ThemeTest {

    /**
     * sRGBカラーチャンネル値から相対輝度計算用のリニア輝度を算出する関数。
     * WCAG 2.xの相対輝度計算式に基づき、ガンマ補正を解除するために用いる。
     */
    private fun channelLuminance(value: Float): Double {
        return if (value <= 0.04045f) {
            (value / 12.92)
        } else {
            ((value + 0.055) / 1.055).pow(2.4)
        }
    }

    /**
     * 単一の色(Color)からWCAG定義の相対輝度(0.0〜1.0)を算出する関数。
     * 背景色と文字色の間の輝度差を評価するために用いる。
     */
    private fun relativeLuminance(color: Color): Double {
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * 2つの色(文字色と背景色)の間のWCAGコントラスト比を算出する関数。
     * (明るい側の相対輝度 + 0.05) / (暗い側の相対輝度 + 0.05) を計算して可読性基準を判定するために用いる。
     */
    private fun contrastRatio(color1: Color, color2: Color): Double {
        val l1 = relativeLuminance(color1)
        val l2 = relativeLuminance(color2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun navyTheme_textColorsHaveAtLeast4_5ContrastAgainstSurface() {
        val bg = NavySurface
        val onSurfaceRatio = contrastRatio(NavyOnSurface, bg)
        val onSurfaceVariantRatio = contrastRatio(NavyOnSurfaceVariant, bg)
        val subtleTextRatio = contrastRatio(NavySubtleText, bg)

        assertTrue("NAVY本文のコントラスト比($onSurfaceRatio)は4.5以上であること", onSurfaceRatio >= 4.5)
        assertTrue("NAVY副次文字のコントラスト比($onSurfaceVariantRatio)は4.5以上であること", onSurfaceVariantRatio >= 4.5)
        assertTrue("NAVY薄い文字のコントラスト比($subtleTextRatio)は4.5以上であること", subtleTextRatio >= 4.5)
    }

    @Test
    fun lightTheme_textColorsHaveAtLeast4_5ContrastAgainstSurface() {
        val bg = LightSurface
        val onSurfaceRatio = contrastRatio(LightOnSurface, bg)
        val onSurfaceVariantRatio = contrastRatio(LightOnSurfaceVariant, bg)
        val subtleTextRatio = contrastRatio(LightSubtleText, bg)

        assertTrue("LIGHT本文のコントラスト比($onSurfaceRatio)は4.5以上であること", onSurfaceRatio >= 4.5)
        assertTrue("LIGHT副次文字のコントラスト比($onSurfaceVariantRatio)は4.5以上であること", onSurfaceVariantRatio >= 4.5)
        assertTrue("LIGHT薄い文字のコントラスト比($subtleTextRatio)は4.5以上であること", subtleTextRatio >= 4.5)
    }

    @Test
    fun blackTheme_textColorsHaveAtLeast4_5ContrastAgainstSurface() {
        val bg = BlackSurface
        val onSurfaceRatio = contrastRatio(BlackOnSurface, bg)
        val onSurfaceVariantRatio = contrastRatio(BlackOnSurfaceVariant, bg)
        val subtleTextRatio = contrastRatio(BlackSubtleText, bg)

        assertTrue("BLACK本文のコントラスト比($onSurfaceRatio)は4.5以上であること", onSurfaceRatio >= 4.5)
        assertTrue("BLACK副次文字のコントラスト比($onSurfaceVariantRatio)は4.5以上であること", onSurfaceVariantRatio >= 4.5)
        assertTrue("BLACK薄い文字のコントラスト比($subtleTextRatio)は4.5以上であること", subtleTextRatio >= 4.5)
    }

    @Test
    fun navyTheme_allRolesHaveCorrectColors() {
        val scheme = resolveColorScheme(AppTheme.NAVY)
        val custom = resolveCustomColors(AppTheme.NAVY, scheme)

        assertEquals("画面の地", Color(0xFF0B1530), scheme.surface)
        assertEquals("カードの地", Color(0xFF142449), scheme.surfaceContainer)
        assertEquals("カードの枠", Color(0xFF1E3163), scheme.outlineVariant)
        assertEquals("弱い枠", Color(0xFF27407C), scheme.outline)
        assertEquals("罫線", Color(0xFF172A55), custom.divider)
        assertEquals("主役", Color(0xFF8CDDC2), scheme.primary)
        assertEquals("主役の上の文字", Color(0xFF0B1530), scheme.onPrimary)
        assertEquals("主役の弱い地", Color(0xFF123A3C), scheme.primaryContainer)
        assertEquals("注意", Color(0xFFFEE48F), scheme.tertiary)
        assertEquals("本文", Color(0xFFE8EEF9), scheme.onSurface)
        assertEquals("副次の文字", Color(0xFFA9C0E4), scheme.onSurfaceVariant)
        assertEquals("薄い文字", Color(0xFF8AA0C9), custom.subtleText)
        assertEquals("鳴り終わった目盛", Color(0xFF2E5A57), custom.scalePast)
        assertEquals("まだ鳴っていない目盛", Color(0xFF16274F), custom.scaleUpcoming)
    }

    @Test
    fun lightTheme_allRolesHaveCorrectColors() {
        val scheme = resolveColorScheme(AppTheme.LIGHT)
        val custom = resolveCustomColors(AppTheme.LIGHT, scheme)

        assertEquals("画面の地", Color(0xFFF5F7FB), scheme.surface)
        assertEquals("カードの地", Color(0xFFFFFFFF), scheme.surfaceContainer)
        assertEquals("カードの枠", Color(0xFFE4E9F2), scheme.outlineVariant)
        assertEquals("弱い枠", Color(0xFFCBD5E6), scheme.outline)
        assertEquals("罫線", Color(0xFFE4E9F2), custom.divider)
        assertEquals("主役", Color(0xFF0B7A64), scheme.primary)
        assertEquals("主役の上の文字", Color(0xFFFFFFFF), scheme.onPrimary)
        assertEquals("主役の弱い地", Color(0xFFE2F4EE), scheme.primaryContainer)
        assertEquals("注意", Color(0xFFB54708), scheme.tertiary)
        assertEquals("本文", Color(0xFF101828), scheme.onSurface)
        assertEquals("副次の文字", Color(0xFF475467), scheme.onSurfaceVariant)
        assertEquals("薄い文字", Color(0xFF5D6B82), custom.subtleText)
        assertEquals("鳴り終わった目盛", Color(0xFF9FD3C4), custom.scalePast)
        assertEquals("まだ鳴っていない目盛", Color(0xFFE4E9F2), custom.scaleUpcoming)
    }

    @Test
    fun blackTheme_allRolesHaveCorrectColors() {
        val scheme = resolveColorScheme(AppTheme.BLACK)
        val custom = resolveCustomColors(AppTheme.BLACK, scheme)

        assertEquals("画面の地", Color(0xFF0A0A0A), scheme.surface)
        assertEquals("カードの地", Color(0xFF111111), scheme.surfaceContainer)
        assertEquals("カードの枠", Color(0xFF1F1F1F), scheme.outlineVariant)
        assertEquals("弱い枠", Color(0xFF262626), scheme.outline)
        assertEquals("罫線", Color(0xFF161616), custom.divider)
        assertEquals("主役", Color(0xFFA3E635), scheme.primary)
        assertEquals("主役の上の文字", Color(0xFF0A0A0A), scheme.onPrimary)
        assertEquals("主役の弱い地", Color(0xFF1C2410), scheme.primaryContainer)
        assertEquals("注意", Color(0xFFF59E0B), scheme.tertiary)
        assertEquals("本文", Color(0xFFE5E5E5), scheme.onSurface)
        assertEquals("副次の文字", Color(0xFFA3A3A3), scheme.onSurfaceVariant)
        assertEquals("薄い文字", Color(0xFF858585), custom.subtleText)
        assertEquals("鳴り終わった目盛", Color(0xFF3F4A1C), custom.scalePast)
        assertEquals("まだ鳴っていない目盛", Color(0xFF1C1C1C), custom.scaleUpcoming)
    }

    @Test
    fun dynamicTheme_mapsAllRolesToExpectedM3Slots() {
        val dynamicScheme = darkColorScheme(
            surface = Color(0xFF101010),
            surfaceContainer = Color(0xFF202020),
            outlineVariant = Color(0xFF303030),
            outline = Color(0xFF404040),
            surfaceContainerHigh = Color(0xFF505050),
            primary = Color(0xFF606060),
            onPrimary = Color(0xFF707070),
            primaryContainer = Color(0xFF808080),
            tertiary = Color(0xFF909090),
            onSurface = Color(0xFFA0A0A0),
            onSurfaceVariant = Color(0xFFB0B0B0),
            surfaceContainerHighest = Color(0xFFC0C0C0),
        )

        val scheme = resolveColorScheme(AppTheme.DYNAMIC, dynamicScheme)
        val custom = resolveCustomColors(AppTheme.DYNAMIC, scheme)

        assertEquals("画面の地 → surface", dynamicScheme.surface, scheme.surface)
        assertEquals("カードの地 → surfaceContainer", dynamicScheme.surfaceContainer, scheme.surfaceContainer)
        assertEquals("カードの枠 → outlineVariant", dynamicScheme.outlineVariant, scheme.outlineVariant)
        assertEquals("弱い枠 → outline", dynamicScheme.outline, scheme.outline)
        assertEquals("罫線 → surfaceContainerHigh", dynamicScheme.surfaceContainerHigh, custom.divider)
        assertEquals("主役 → primary", dynamicScheme.primary, scheme.primary)
        assertEquals("主役の上の文字 → onPrimary", dynamicScheme.onPrimary, scheme.onPrimary)
        assertEquals("主役の弱い地 → primaryContainer", dynamicScheme.primaryContainer, scheme.primaryContainer)
        assertEquals("注意 → tertiary", dynamicScheme.tertiary, scheme.tertiary)
        assertEquals("本文 → onSurface", dynamicScheme.onSurface, scheme.onSurface)
        assertEquals("副次の文字 → onSurfaceVariant", dynamicScheme.onSurfaceVariant, scheme.onSurfaceVariant)
        assertEquals("薄い文字 → onSurfaceVariant", dynamicScheme.onSurfaceVariant, custom.subtleText)
        assertEquals("鳴り終わった目盛 → primaryContainer", dynamicScheme.primaryContainer, custom.scalePast)
        assertEquals("まだ鳴っていない目盛 → surfaceContainerHighest", dynamicScheme.surfaceContainerHighest, custom.scaleUpcoming)
    }

    @Test
    fun typography_timeStyleUsesTabularNumsAndWeight200() {
        assertEquals("tnum", TimeStyle.fontFeatureSettings)
        assertEquals(FontWeight.W200, TimeStyle.fontWeight)
    }

    @Test
    fun typography_captionSizeIsAtLeast11sp() {
        // 画面の文字は13spを下限にしている。11spはAppleのHIGが定める読みやすさの最低線で、
        // 実機で見ると小さすぎたため、こちらで下限を上げた（docs/SPEC.md「読みやすさ」）
        assertTrue(CaptionStyle.fontSize.value >= 13f)
        assertTrue(AppTypography.labelSmall.fontSize.value >= 13f)
        assertTrue(AppTypography.labelMedium.fontSize.value >= 13f)
        assertTrue(AppTypography.labelLarge.fontSize.value >= 11f)
        assertTrue(AppTypography.bodySmall.fontSize.value >= 11f)
    }

    @Test
    fun allThemes_buttonContrastRatioIsAtLeast4_5() {
        val themes = listOf(
            AppTheme.NAVY,
            AppTheme.LIGHT,
            AppTheme.BLACK,
        )

        for (theme in themes) {
            val scheme = resolveColorScheme(theme)
            val custom = resolveCustomColors(theme, scheme)

            // 1. 主役の色で塗ったボタン: 主役の地(primary)と主役の上の文字(onPrimary)
            val primaryButtonRatio = contrastRatio(scheme.onPrimary, scheme.primary)
            assertTrue(
                "${theme.name}: 主役ボタンの文字コントラスト比($primaryButtonRatio)は4.5以上であること",
                primaryButtonRatio >= 4.5,
            )

            // 2. 枠線だけのボタン: カード地(surfaceContainer)や画面地(surface)上の副次文字(onSurfaceVariant)
            val outlinedButtonRatioOnContainer = contrastRatio(scheme.onSurfaceVariant, scheme.surfaceContainer)
            assertTrue(
                "${theme.name}: カード上の枠線ボタンの副次文字コントラスト比($outlinedButtonRatioOnContainer)は4.5以上であること",
                outlinedButtonRatioOnContainer >= 4.5,
            )

            val outlinedButtonRatioOnSurface = contrastRatio(scheme.onSurfaceVariant, scheme.surface)
            assertTrue(
                "${theme.name}: 画面上の枠線ボタンの副次文字コントラスト比($outlinedButtonRatioOnSurface)は4.5以上であること",
                outlinedButtonRatioOnSurface >= 4.5,
            )

            // 選択肢カード地(scaleUpcoming)上の副次文字
            val optionCardRatio = contrastRatio(scheme.onSurfaceVariant, custom.scaleUpcoming)
            assertTrue(
                "${theme.name}: 選択肢カード上の副次文字コントラスト比($optionCardRatio)は4.5以上であること",
                optionCardRatio >= 4.5,
            )

            // 3. 押せない状態のボタン: カード地(surfaceContainer)上の無効文字(onSurfaceVariant)
            val disabledButtonRatio = contrastRatio(scheme.onSurfaceVariant, scheme.surfaceContainer)
            assertTrue(
                "${theme.name}: 押せないボタンの文字コントラスト比($disabledButtonRatio)は4.5以上であること",
                disabledButtonRatio >= 4.5,
            )
        }
    }
}

