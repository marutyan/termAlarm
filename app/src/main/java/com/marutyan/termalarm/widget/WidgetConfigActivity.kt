package com.marutyan.termalarm.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AppTheme
import com.marutyan.termalarm.ui.theme.BlackSurface
import com.marutyan.termalarm.ui.theme.LightSurface
import com.marutyan.termalarm.ui.theme.NavyOnSurface
import com.marutyan.termalarm.ui.theme.NavyOutline
import com.marutyan.termalarm.ui.theme.NavyPrimary
import com.marutyan.termalarm.ui.theme.NavySubtleText
import com.marutyan.termalarm.ui.theme.NavySurface
import com.marutyan.termalarm.ui.theme.NavySurfaceContainer
import kotlinx.coroutines.launch

/**
 * ウィジェットを置くときに出す設定画面。
 * 配色、書体、背景をここで選ばせ、選んだ内容をウィジェットごとに保存する。
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            .padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.widget_config_title),
            color = NavyOnSurface,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(28.dp))

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

        Spacer(Modifier.weight(1f))
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
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFB79CE8),
            Color(0xFFE8A0B4),
            Color(0xFFF0C48A),
        ),
    )
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
