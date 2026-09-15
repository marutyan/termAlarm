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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AppTheme
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
 * 配色、書体、背景をここで選ばせ、選んだ内容をウィジェットごとに保存する。
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
                onDecide = { theme, fontStyle, transparent ->
                    save(appWidgetId, theme, fontStyle, transparent)
                },
            )
        }
    }

    // 選んだ内容を保存し、ウィジェットを描き直してから画面を閉じる
    private fun save(appWidgetId: Int, theme: AppTheme, fontStyle: WidgetFontStyle, transparent: Boolean) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                prefs[WIDGET_THEME_KEY] = theme.name
                prefs[WIDGET_FONT_STYLE_KEY] = fontStyle.name
                prefs[WIDGET_TRANSPARENT_KEY] = transparent
            }
            TermAlarmWidget().update(this@WidgetConfigActivity, glanceId)
            WidgetUpdateScheduler.scheduleNextTick(this@WidgetConfigActivity)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

// 配色、書体、背景を選ぶ画面。アプリ本体とは別に選べるようにするため、ここで完結させる
@Composable
private fun ConfigScreen(onDecide: (AppTheme, WidgetFontStyle, Boolean) -> Unit) {
    var theme by remember { mutableStateOf(AppTheme.NAVY) }
    var fontStyle by remember { mutableStateOf(WidgetFontStyle.STANDARD) }
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
        WidgetPreview(theme = theme, fontStyle = fontStyle, transparent = transparent)
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.widget_config_color))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ColorChoice(NavySurface, theme == AppTheme.NAVY) { theme = AppTheme.NAVY }
            ColorChoice(LightSurface, theme == AppTheme.LIGHT) { theme = AppTheme.LIGHT }
            ColorChoice(BlackSurface, theme == AppTheme.BLACK) { theme = AppTheme.BLACK }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                DynamicColorChoice(selected = theme == AppTheme.DYNAMIC) { theme = AppTheme.DYNAMIC }
            }
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
        SectionLabel(stringResource(R.string.widget_config_background))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ChoiceButton(stringResource(R.string.widget_config_solid), !transparent, Modifier.weight(1f)) { transparent = false }
            ChoiceButton(stringResource(R.string.widget_config_transparent), transparent, Modifier.weight(1f)) { transparent = true }
        }

      }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(NavyPrimary, RoundedCornerShape(26.dp))
                .clickable { onDecide(theme, fontStyle, transparent) },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(R.string.widget_config_decide), color = NavySurface, fontSize = 15.sp)
        }
    }
}

/**
 * 置いたときの見た目の見本。
 *
 * 選んだ配色・書体・背景をそのまま当てて、ウィジェットと同じ並び（日付・時刻・次の鳴動）を描く。
 * 選択肢だけを並べていたときは、決めるまでどう見えるのか分からなかった。
 *
 * 透明を選んだときは壁紙の上に乗るため、ここでは市松模様ではなく薄い枠で「背景が無い」ことを示す。
 */
@Composable
private fun WidgetPreview(theme: AppTheme, fontStyle: WidgetFontStyle, transparent: Boolean) {
    val colors = previewColors(theme, transparent)
    val family = when (fontStyle) {
        WidgetFontStyle.STANDARD -> FontFamily.SansSerif
        WidgetFontStyle.MONOSPACE -> FontFamily.Monospace
        WidgetFontStyle.SERIF -> FontFamily.Serif
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
                color = colors.subtleText,
                fontSize = 13.sp,
                fontFamily = family,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.widget_preview_time),
                color = colors.text,
                fontSize = 40.sp,
                fontFamily = family,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    text = stringResource(R.string.widget_preview_next_time),
                    color = colors.accent,
                    fontSize = 14.sp,
                    fontFamily = family,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.widget_preview_remaining),
                    color = colors.subtleText,
                    fontSize = 13.sp,
                    fontFamily = family,
                )
            }
        }
    }
}

// 見本に使う4色。ウィジェット本体のwidgetPaletteと同じ決め方にする
private data class PreviewColors(
    val background: Color,
    val text: Color,
    val subtleText: Color,
    val accent: Color,
)

@Composable
private fun previewColors(theme: AppTheme, transparent: Boolean): PreviewColors {
    val base = when (theme) {
        AppTheme.LIGHT -> PreviewColors(LightSurface, LightOnSurface, LightSubtleText, LightPrimary)
        AppTheme.BLACK -> PreviewColors(BlackSurface, BlackOnSurface, BlackSubtleText, BlackPrimary)
        AppTheme.DYNAMIC -> {
            // ウィジェット本体はGlanceTheme経由で端末の色を使う。見本も同じ色から作る
            val context = LocalContext.current
            val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                null
            }
            if (scheme != null) {
                PreviewColors(scheme.surface, scheme.onSurface, scheme.onSurfaceVariant, scheme.primary)
            } else {
                PreviewColors(NavySurface, NavyOnSurface, NavySubtleText, NavyPrimary)
            }
        }
        else -> PreviewColors(NavySurface, NavyOnSurface, NavySubtleText, NavyPrimary)
    }
    if (!transparent) return base
    // 壁紙の明るさは分からないため、透明のときは明るい配色でも白系の文字にする(本体と同じ)
    return base.copy(background = Color.Transparent, text = NavyOnSurface, subtleText = NavyOnSurface)
}

// まとまりの見出し。項目の意味を短く示すために用いる
@Composable
private fun SectionLabel(text: String) {
    Text(text = text, color = NavySubtleText, fontSize = 13.sp)
}

// 配色の選択肢。色そのものを丸で見せ、選んでいるものに輪を付ける
@Composable
private fun ColorChoice(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) NavyPrimary else NavyOutline, CircleShape)
            .clickable(onClick = onClick),
    )
}

/**
 * 端末の色（ダイナミックカラー）を表す虹色グラデーションの選択肢。
 * Android 12以降でのみ表示し、壁紙連動の配色を選択できるようにするために用いる。
 */
@Composable
private fun DynamicColorChoice(selected: Boolean, onClick: () -> Unit) {
    val gradient = Brush.linearGradient(colors = DynamicThemePreviewColors)
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(gradient, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) NavyPrimary else NavyOutline, CircleShape)
            .clickable(onClick = onClick),
    )
}

// 背景や書体の選択肢。押せる高さを確保するため48dpとする
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
