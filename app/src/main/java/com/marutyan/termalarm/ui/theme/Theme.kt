package com.marutyan.termalarm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
)

/**
 * TermAlarm全体のMaterial3テーマ。
 * Android 12(API31)以降は壁紙由来のDynamic Colorを既定で使い、純正時計アプリと同じ見た目に揃える。
 * それ未満の端末では固定のライト/ダーク配色にフォールバックする。
 * MaterialExpressiveThemeを使い、スイッチやToggleButtonの動きにMaterial3 Expressiveの弾むようなモーションを適用する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TermAlarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.withSystemErrorColors(darkTheme)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        content = content,
    )
}

/**
 * 壁紙由来の配色にも、端末が持つ注意色(error)を反映させる。
 *
 * Composeの動的配色はerrorだけ壁紙を反映せず、既定の淡いピンクのままになる。
 * 一方、純正の時計アプリはストップウォッチの「停止」などに端末のerror色をそのまま使うため、
 * このままでは同じ端末でも色が食い違う。Android 14以降は端末のerror色を資源から読めるので、
 * 読めるときだけ差し替えて純正と同じ見た目に揃える。
 */
@Composable
private fun ColorScheme.withSystemErrorColors(darkTheme: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return this
    return copy(
        error = colorResource(if (darkTheme) android.R.color.system_error_dark else android.R.color.system_error_light),
        onError = colorResource(if (darkTheme) android.R.color.system_on_error_dark else android.R.color.system_on_error_light),
        errorContainer = colorResource(
            if (darkTheme) android.R.color.system_error_container_dark else android.R.color.system_error_container_light,
        ),
        onErrorContainer = colorResource(
            if (darkTheme) android.R.color.system_on_error_container_dark else android.R.color.system_on_error_container_light,
        ),
    )
}
