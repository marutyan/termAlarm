package com.marutyan.termalarm.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.BlackOnSurface
import com.marutyan.termalarm.ui.theme.BlackPrimary
import com.marutyan.termalarm.ui.theme.BlackSubtleText
import com.marutyan.termalarm.ui.theme.BlackSurface
import com.marutyan.termalarm.ui.theme.DynamicThemePreviewColors
import com.marutyan.termalarm.ui.theme.LightOnSurface
import com.marutyan.termalarm.ui.theme.LightPrimary
import com.marutyan.termalarm.ui.theme.LightSubtleText
import com.marutyan.termalarm.ui.theme.LightSurface
import com.marutyan.termalarm.ui.theme.NavyOnSurface
import com.marutyan.termalarm.ui.theme.NavyOutline
import com.marutyan.termalarm.ui.theme.NavyPrimary
import com.marutyan.termalarm.ui.theme.NavySubtleText
import com.marutyan.termalarm.ui.theme.NavySurface
import com.marutyan.termalarm.ui.theme.NavySurfaceContainer
import com.marutyan.termalarm.ui.theme.NavySurfaceContainerHigh
import kotlinx.coroutines.launch

/**
 * ウィジェットを置くときに出す設定画面。
 * 配色、色を付ける場所、書体、太さ、背景をここで選ばせ、選んだ内容をウィジェットごとに保存する。
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 画面の端まで描く。ステータスバーの下に見出しが潜って読めなくなっていた
        enableEdgeToEdge()
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 置くのをやめた場合に備え、まず取り消しの結果を入れておく
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            ConfigScreen(
                onDecide = { colorScheme, accentTarget, fontStyle, fontWeight, transparent ->
                    save(appWidgetId, colorScheme, accentTarget, fontStyle, fontWeight, transparent)
                },
            )
        }
    }

    /**
     * 選んだ内容をウィジェットごとの設定へ保存し、ウィジェットを描き直してから画面を閉じる。
     * 設定の変更を即座にウィジェットへ反映するために用いる。
     */
    private fun save(
        appWidgetId: Int,
        colorScheme: WidgetColorScheme,
        accentTarget: WidgetAccentTarget,
        fontStyle: WidgetFontStyle,
        fontWeight: WidgetFontWeight,
        transparent: Boolean,
    ) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                prefs[WIDGET_COLOR_SCHEME_KEY] = colorScheme.name
                prefs[WIDGET_ACCENT_TARGET_KEY] = accentTarget.name
                prefs[WIDGET_FONT_STYLE_KEY] = fontStyle.name
                prefs[WIDGET_FONT_WEIGHT_KEY] = fontWeight.name
                prefs[WIDGET_TRANSPARENT_KEY] = transparent
            }
            TermAlarmWidget().update(this@WidgetConfigActivity, glanceId)
            WidgetUpdateScheduler.scheduleNextTick(this@WidgetConfigActivity)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

/**
 * 配色、色を付ける場所、書体、太さ、背景を選ぶ画面。
 * アプリ本体の設定とは別にウィジェット単体でカスタマイズできるようにするために用いる。
 */
@Composable
private fun ConfigScreen(
    onDecide: (WidgetColorScheme, WidgetAccentTarget, WidgetFontStyle, WidgetFontWeight, Boolean) -> Unit,
) {
    var colorScheme by remember { mutableStateOf(WidgetColorScheme.DARK) }
    var accentTarget by remember { mutableStateOf(WidgetAccentTarget.NEXT_RING) }
    var fontStyle by remember { mutableStateOf(WidgetFontStyle.STANDARD) }
    var fontWeight by remember { mutableStateOf(WidgetFontWeight.NORMAL) }
    var transparent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavySurface)
            // ステータスバーとジェスチャーバーを避ける。避けないと見出しと決定ボタンが隠れる
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        // 画面が縦に狭いときでも決定ボタンへ届くよう、選ぶところだけを送れるようにする
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.widget_config_title),
                color = NavyOnSurface,
                fontSize = 24.sp,
            )
            Spacer(Modifier.height(20.dp))

            // 置いたときの見た目をそのまま見せる。選ぶたびに変わるので、決める前に確かめられる
            WidgetPreview(
                colorScheme = colorScheme,
                accentTarget = accentTarget,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                transparent = transparent,
            )
            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.widget_config_color))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ColorChoice(
                    color = LightSurface,
                    contentDescription = stringResource(R.string.widget_config_color_light),
                    selected = colorScheme == WidgetColorScheme.LIGHT,
                ) { colorScheme = WidgetColorScheme.LIGHT }
                ColorChoice(
                    color = BlackSurface,
                    contentDescription = stringResource(R.string.widget_config_color_dark),
                    selected = colorScheme == WidgetColorScheme.DARK,
                ) { colorScheme = WidgetColorScheme.DARK }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    DynamicColorChoice(
                        contentDescription = stringResource(R.string.widget_config_color_system),
                        selected = colorScheme == WidgetColorScheme.SYSTEM,
                    ) { colorScheme = WidgetColorScheme.SYSTEM }
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel(stringResource(R.string.widget_config_accent))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceButton(
                    text = stringResource(R.string.widget_config_accent_time),
                    selected = accentTarget == WidgetAccentTarget.TIME,
                    modifier = Modifier.weight(1f),
                ) { accentTarget = WidgetAccentTarget.TIME }
                ChoiceButton(
                    text = stringResource(R.string.widget_config_accent_next),
                    selected = accentTarget == WidgetAccentTarget.NEXT_RING,
                    modifier = Modifier.weight(1f),
                ) { accentTarget = WidgetAccentTarget.NEXT_RING }
                ChoiceButton(
                    text = stringResource(R.string.widget_config_accent_date),
                    selected = accentTarget == WidgetAccentTarget.DATE,
                    modifier = Modifier.weight(1f),
                ) { accentTarget = WidgetAccentTarget.DATE }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel(stringResource(R.string.widget_config_font))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceButton(
                    text = stringResource(R.string.widget_config_font_standard),
                    selected = fontStyle == WidgetFontStyle.STANDARD,
                    modifier = Modifier.weight(1f),
                ) { fontStyle = WidgetFontStyle.STANDARD }
                ChoiceButton(
                    text = stringResource(R.string.widget_config_font_monospace),
                    selected = fontStyle == WidgetFontStyle.MONOSPACE,
                    modifier = Modifier.weight(1f),
                ) { fontStyle = WidgetFontStyle.MONOSPACE }
                ChoiceButton(
                    text = stringResource(R.string.widget_config_font_serif),
                    selected = fontStyle == WidgetFontStyle.SERIF,
                    modifier = Modifier.weight(1f),
                ) { fontStyle = WidgetFontStyle.SERIF }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel(stringResource(R.string.widget_config_weight))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceButton(
                    text = stringResource(R.string.widget_config_weight_normal),
                    selected = fontWeight == WidgetFontWeight.NORMAL,
                    modifier = Modifier.weight(1f),
                ) { fontWeight = WidgetFontWeight.NORMAL }
                ChoiceButton(
                    text = stringResource(R.string.widget_config_weight_medium),
                    selected = fontWeight == WidgetFontWeight.MEDIUM,
                    modifier = Modifier.weight(1f),
                ) { fontWeight = WidgetFontWeight.MEDIUM }
                ChoiceButton(
                    text = stringResource(R.string.widget_config_weight_bold),
                    selected = fontWeight == WidgetFontWeight.BOLD,
                    modifier = Modifier.weight(1f),
                ) { fontWeight = WidgetFontWeight.BOLD }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel(stringResource(R.string.widget_config_background))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceButton(
                    text = stringResource(R.string.widget_config_solid),
                    selected = !transparent,
                    modifier = Modifier.weight(1f),
                ) { transparent = false }
                ChoiceButton(
                    text = stringResource(R.string.widget_config_transparent),
                    selected = transparent,
                    modifier = Modifier.weight(1f),
                ) { transparent = true }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(NavyPrimary, RoundedCornerShape(26.dp))
                .clickable { onDecide(colorScheme, accentTarget, fontStyle, fontWeight, transparent) },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(R.string.widget_config_decide), color = NavySurface, fontSize = 15.sp)
        }
    }
}

/**
 * 置いたときの見た目の見本。
 *
 * 選んだ配色・色を付ける場所・書体・太さ・背景をそのまま当てて、
 * ウィジェットと同じ3段（月日と曜日・時刻・時計アイコン＋次に鳴る時刻）を描く。
 * 設定画面上で仕上がりを事前に確認できるようにするために用いる。
 *
 * 透明を選んだときは壁紙の上に乗るため、薄い枠線で「背景が無い」ことを示す。
 */
@Composable
private fun WidgetPreview(
    colorScheme: WidgetColorScheme,
    accentTarget: WidgetAccentTarget,
    fontStyle: WidgetFontStyle,
    fontWeight: WidgetFontWeight,
    transparent: Boolean,
) {
    val colors = previewColors(colorScheme, transparent)
    val family = when (fontStyle) {
        WidgetFontStyle.STANDARD -> FontFamily.SansSerif
        WidgetFontStyle.MONOSPACE -> FontFamily.Monospace
        WidgetFontStyle.SERIF -> FontFamily.Serif
    }
    val weight = when (fontWeight) {
        WidgetFontWeight.MEDIUM -> FontWeight.Medium
        WidgetFontWeight.NORMAL -> FontWeight.Normal
        WidgetFontWeight.BOLD -> FontWeight.Bold
    }
    // 見本の後ろは、壁紙の代わりの控えめな斜めのぼかし。
    // 背景に「透明」を選んだときに、後ろが透けることが伝わるようにする
    val wallpaperLike = Brush.linearGradient(
        colors = listOf(NavySurfaceContainerHigh, NavySurfaceContainer),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(wallpaperLike, RoundedCornerShape(20.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(176.dp)
                .height(118.dp)
                .background(colors.background, RoundedCornerShape(22.dp))
                .border(
                    width = 1.dp,
                    color = if (transparent) NavyOutline else Color.Transparent,
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.widget_preview_date),
                color = colors.colorOf(WidgetAccentTarget.DATE, accentTarget),
                fontSize = 13.sp,
                fontFamily = family,
                fontWeight = weight,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.widget_preview_time),
                color = colors.colorOf(WidgetAccentTarget.TIME, accentTarget),
                fontSize = 40.sp,
                fontFamily = family,
                fontWeight = weight,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val nextRingColor = colors.colorOf(WidgetAccentTarget.NEXT_RING, accentTarget)
                Icon(
                    painter = painterResource(R.drawable.ic_widget_alarm),
                    contentDescription = null,
                    tint = nextRingColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.widget_preview_next_time),
                    color = nextRingColor,
                    fontSize = 14.sp,
                    fontFamily = family,
                    fontWeight = weight,
                )
            }
        }
    }
}

/**
 * 見本に使う4色。
 * ウィジェット本体のWidgetPaletteと同じ構成を保持し、設定画面の見本表示に用いる。
 */
private data class PreviewColors(
    val background: Color,
    val text: Color,
    val subtleText: Color,
    val accent: Color,
)

/**
 * 見本の部位ごとの文字色を解決する。
 * 選ばれているアクセント色適用の部位にアクセント色を割り当て、それ以外は通常色または控えめな色を割り当てるために用いる。
 */
private fun PreviewColors.colorOf(part: WidgetAccentTarget, accentTarget: WidgetAccentTarget): Color {
    if (part == accentTarget) return accent
    return when (part) {
        WidgetAccentTarget.TIME -> text
        WidgetAccentTarget.NEXT_RING,
        WidgetAccentTarget.DATE -> subtleText
    }
}

/**
 * 選んだ配色と背景透明度から見本用の色群を生成する。
 * ウィジェット本体のwidgetPaletteと同じ色の決め方を再現し、見本と実物の見た目を一致させるために用いる。
 */
@Composable
private fun previewColors(colorScheme: WidgetColorScheme, transparent: Boolean): PreviewColors {
    val base = when {
        colorScheme == WidgetColorScheme.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            PreviewColors(
                background = scheme.surface,
                text = scheme.onSurface,
                subtleText = scheme.onSurfaceVariant,
                accent = scheme.primary,
            )
        }
        colorScheme == WidgetColorScheme.LIGHT -> PreviewColors(
            background = LightSurface,
            text = LightOnSurface,
            subtleText = LightSubtleText,
            accent = LightPrimary,
        )
        else -> PreviewColors(
            background = BlackSurface,
            text = BlackOnSurface,
            subtleText = BlackSubtleText,
            accent = BlackPrimary,
        )
    }
    if (!transparent) return base
    // 壁紙の明るさは分からないため、透明のときは明るい配色でも白系の文字にする（本体と同じ）
    return base.copy(
        background = Color.Transparent,
        text = BlackOnSurface,
        subtleText = BlackOnSurface,
    )
}

/**
 * まとまりの見出し。
 * 各設定項目の意味を簡潔に示すために用いる。
 */
@Composable
private fun SectionLabel(text: String) {
    Text(text = text, color = NavySubtleText, fontSize = 13.sp)
}

/**
 * 配色の選択肢。
 * 固定色そのものを丸で見せ、選択中の項目に輪を付けるとともに、読み上げ用のラベルを提供するために用いる。
 */
@Composable
private fun ColorChoice(
    color: Color,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) NavyPrimary else NavyOutline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    )
}

/**
 * 端末の色（ダイナミックカラー）を表す虹色グラデーションの選択肢。
 * Android 12以降でのみ表示し、壁紙連動の配色を選択できるようにするとともに、読み上げ用のラベルを提供するために用いる。
 */
@Composable
private fun DynamicColorChoice(
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val gradient = Brush.linearGradient(colors = DynamicThemePreviewColors)
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(gradient, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) NavyPrimary else NavyOutline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    )
}

/**
 * 色を付ける場所、書体、太さ、背景の選択肢ボタン。
 * タップ可能な領域（高さ48dp）を確保し、選択状態を色と枠線で表すために用いる。
 */
@Composable
private fun ChoiceButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(if (selected) NavySurfaceContainer else NavySurface, RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) NavyPrimary else NavyOutline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = if (selected) NavyPrimary else NavySubtleText, fontSize = 14.sp)
    }
}
