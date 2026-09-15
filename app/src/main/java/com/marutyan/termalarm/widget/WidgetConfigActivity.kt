package com.marutyan.termalarm.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.marutyan.termalarm.R
import com.marutyan.termalarm.ui.theme.BlackSurface
import com.marutyan.termalarm.ui.theme.DynamicThemePreviewColors
import com.marutyan.termalarm.ui.theme.NavyOutline
import com.marutyan.termalarm.ui.theme.NavyPrimary
import com.marutyan.termalarm.ui.theme.NavySubtleText
import com.marutyan.termalarm.ui.theme.NavySurface
import com.marutyan.termalarm.ui.theme.NavySurfaceContainer
import com.marutyan.termalarm.ui.theme.NavySurfaceContainerHigh
import kotlinx.coroutines.launch

/**
 * ウィジェットを置くときに出す設定画面。
 * 背景、時刻の色、書体、太さをここで選ばせ、選んだ内容をウィジェットごとに保存する。
 * ウィジェット追加時にシステムから起動され、初期設定を確定させる役割を持つ。
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
                onDecide = { backgroundStyle, timeColor, fontStyle, fontWeight ->
                    save(appWidgetId, backgroundStyle, timeColor, fontStyle, fontWeight)
                },
            )
        }
    }

    /**
     * 選んだ内容をウィジェットごとの設定へ保存し、ウィジェットを描き直してから画面を閉じる。
     * 設定の変更を即座にウィジェットへ反映するために用いる。
     * 設定値の永続化とウィジェット更新スケジュールの起動を行う役割を持つ。
     */
    private fun save(
        appWidgetId: Int,
        backgroundStyle: WidgetBackgroundStyle,
        timeColor: WidgetTimeColor,
        fontStyle: WidgetFontStyle,
        fontWeight: WidgetFontWeight,
    ) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                prefs[WIDGET_BACKGROUND_STYLE_KEY] = backgroundStyle.name
                prefs[WIDGET_TIME_COLOR_KEY] = timeColor.name
                prefs[WIDGET_FONT_STYLE_KEY] = fontStyle.name
                prefs[WIDGET_FONT_WEIGHT_KEY] = fontWeight.name
            }
            TermAlarmWidget().update(this@WidgetConfigActivity, glanceId)
            WidgetUpdateScheduler.scheduleNextTick(this@WidgetConfigActivity)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

/**
 * 背景、時刻の色、書体、太さを選ぶ画面。
 * アプリ本体の設定とは別にウィジェット単体で外観を調整できるようにするために用いる。
 * 各項目の選択状態を保持し、見本と決定ボタンへの受け渡しを仲介する役割を持つ。
 */
@Composable
private fun ConfigScreen(
    onDecide: (
        WidgetBackgroundStyle,
        WidgetTimeColor,
        WidgetFontStyle,
        WidgetFontWeight,
    ) -> Unit,
) {
    var backgroundStyle by remember { mutableStateOf(WidgetBackgroundStyle.FILLED) }
    var timeColor by remember { mutableStateOf(WidgetTimeColor.WHITE) }
    var fontStyle by remember { mutableStateOf(WidgetFontStyle.STANDARD) }
    var fontWeight by remember { mutableStateOf(WidgetFontWeight.NORMAL) }

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
            // 置いたときの見た目をそのまま見せる。選ぶたびに変わるので、決める前に確かめられる
            WidgetPreview(
                backgroundStyle = backgroundStyle,
                timeColor = timeColor,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
            )

            Spacer(Modifier.height(32.dp))

            // 背景のボタン2つ（透過 / 背景付き）
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceButton(
                    text = stringResource(R.string.widget_config_background_transparent),
                    selected = backgroundStyle == WidgetBackgroundStyle.TRANSPARENT,
                    modifier = Modifier.weight(1f),
                ) { backgroundStyle = WidgetBackgroundStyle.TRANSPARENT }
                ChoiceButton(
                    text = stringResource(R.string.widget_config_background_filled),
                    selected = backgroundStyle == WidgetBackgroundStyle.FILLED,
                    modifier = Modifier.weight(1f),
                ) { backgroundStyle = WidgetBackgroundStyle.FILLED }
            }

            Spacer(Modifier.height(32.dp))

            // 時刻の色の丸3つ（白 / 黒 / システム）
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ColorChoice(
                    color = Color.White,
                    contentDescription = stringResource(R.string.widget_config_time_color_white),
                    selected = timeColor == WidgetTimeColor.WHITE,
                ) { timeColor = WidgetTimeColor.WHITE }
                ColorChoice(
                    color = Color.Black,
                    contentDescription = stringResource(R.string.widget_config_time_color_black),
                    selected = timeColor == WidgetTimeColor.BLACK,
                ) { timeColor = WidgetTimeColor.BLACK }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    DynamicColorChoice(
                        contentDescription = stringResource(R.string.widget_config_time_color_system),
                        selected = timeColor == WidgetTimeColor.SYSTEM,
                    ) { timeColor = WidgetTimeColor.SYSTEM }
                }
            }

            Spacer(Modifier.height(32.dp))

            // 書体のボタン3つ（Sans Serif / Monospace / Serif）
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceButton(
                    text = stringResource(R.string.widget_config_font_sans),
                    selected = fontStyle == WidgetFontStyle.STANDARD,
                    modifier = Modifier.weight(1f),
                ) { fontStyle = WidgetFontStyle.STANDARD }
                ChoiceButton(
                    text = stringResource(R.string.widget_config_font_mono),
                    selected = fontStyle == WidgetFontStyle.MONOSPACE,
                    modifier = Modifier.weight(1f),
                ) { fontStyle = WidgetFontStyle.MONOSPACE }
                ChoiceButton(
                    text = stringResource(R.string.widget_config_font_serif),
                    selected = fontStyle == WidgetFontStyle.SERIF,
                    modifier = Modifier.weight(1f),
                ) { fontStyle = WidgetFontStyle.SERIF }
            }

            Spacer(Modifier.height(32.dp))

            // 太さのボタン3つ（Normal / Medium / Bold）
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
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(NavyPrimary, RoundedCornerShape(26.dp))
                .clickable {
                    onDecide(
                        backgroundStyle,
                        timeColor,
                        fontStyle,
                        fontWeight,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(R.string.widget_config_decide), color = NavySurface, fontSize = 15.sp)
        }
    }
}

/**
 * 置いたときの見た目の見本。
 *
 * 選んだ背景の出し方・時刻の色・書体・太さをそのまま当てて、
 * ウィジェットと同じ3段（月日と曜日・時刻・時計アイコン＋次に鳴る時刻）を描く。
 * 設定画面上で仕上がりを事前に確認できるようにするために用いる。
 * 選択状態の即時フィードバックを提供する役割を持つ。
 */
@Composable
private fun WidgetPreview(
    backgroundStyle: WidgetBackgroundStyle,
    timeColor: WidgetTimeColor,
    fontStyle: WidgetFontStyle,
    fontWeight: WidgetFontWeight,
) {
    val family = when (fontStyle) {
        WidgetFontStyle.STANDARD -> FontFamily.SansSerif
        WidgetFontStyle.MONOSPACE -> FontFamily.Monospace
        WidgetFontStyle.SERIF -> FontFamily.Serif
    }
    val timeWeight = when (fontWeight) {
        WidgetFontWeight.NORMAL -> FontWeight.Normal
        WidgetFontWeight.MEDIUM -> FontWeight.Medium
        WidgetFontWeight.BOLD -> FontWeight.Bold
    }
    val secondaryWeight = when (secondaryFontWeight(fontWeight)) {
        WidgetFontWeight.NORMAL -> FontWeight.Normal
        WidgetFontWeight.MEDIUM -> FontWeight.Medium
        WidgetFontWeight.BOLD -> FontWeight.Bold
    }
    val timeFontSize = 40.sp
    val secondaryFontSize = secondaryFontSizeSp(timeFontSize.value).sp
    val iconSize = nextRingIconSizeDp(secondaryFontSize.value).dp
    // ウィジェット本体と同じずらし方・濃さの影にする
    val shadowOffsetPx = with(LocalDensity.current) { WIDGET_SHADOW_OFFSET_DP.dp.toPx() }
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = WIDGET_SHADOW_ALPHA),
        offset = Offset(shadowOffsetPx, shadowOffsetPx),
        blurRadius = 0f,
    )
    val context = LocalContext.current
    val timeTextColor = when (timeColor) {
        WidgetTimeColor.WHITE -> Color.White
        WidgetTimeColor.BLACK -> Color.Black
        WidgetTimeColor.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
            } else {
                Color.White
            }
        }
    }
    val backgroundColor = when (backgroundStyle) {
        WidgetBackgroundStyle.TRANSPARENT -> Color.Transparent
        WidgetBackgroundStyle.FILLED -> BlackSurface
    }
    val borderColor = when (backgroundStyle) {
        WidgetBackgroundStyle.TRANSPARENT -> NavyOutline
        WidgetBackgroundStyle.FILLED -> Color.Transparent
    }

    // 見本の後ろは、壁紙の代わりの控えめな斜めのぼかし。
    // 背景が透過のときに後ろが透けることが伝わるようにする
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
                .background(backgroundColor, RoundedCornerShape(22.dp))
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.widget_preview_date),
                color = Color.White,
                fontSize = secondaryFontSize,
                fontFamily = family,
                fontWeight = secondaryWeight,
                style = TextStyle(shadow = textShadow),
            )
            Text(
                text = stringResource(R.string.widget_preview_time),
                color = timeTextColor,
                fontSize = timeFontSize,
                fontFamily = family,
                fontWeight = timeWeight,
                style = TextStyle(shadow = textShadow),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_widget_alarm),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.widget_preview_next_time),
                    color = Color.White,
                    fontSize = secondaryFontSize,
                    fontFamily = family,
                    fontWeight = secondaryWeight,
                    style = TextStyle(shadow = textShadow),
                )
            }
        }
    }
}

/**
 * 固定色の選択肢の丸。
 * 白や黒などの単色を円形で表示し、選択状態の枠線と読み上げ用ラベルを提供するために用いる。
 * 時刻の色の選択肢としてユーザーに視覚的な候補を提示する役割を持つ。
 */
@Composable
private fun ColorChoice(
    color: Color,
    contentDescription: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) NavyPrimary else NavyOutline, CircleShape)
            .clickable(onClick = onClick)
            .then(semanticsModifier),
    )
}

/**
 * 端末の色（ダイナミックカラー）を表す虹色グラデーションの選択肢の丸。
 * Android 12以降でのみ表示し、壁紙連動の時刻色を選択できるようにするとともに、読み上げ用のラベルを提供するために用いる。
 * システムカラーを時刻の色として選ばせるための選択肢を提供する役割を持つ。
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
 * 背景、書体、太さの選択肢ボタン。
 * タップ可能な領域（高さ48dp）を確保し、選択状態を色と枠線で表すために用いる。
 * 複数の選択肢から1つを選ばせるボタングループの各要素を表示する役割を持つ。
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
