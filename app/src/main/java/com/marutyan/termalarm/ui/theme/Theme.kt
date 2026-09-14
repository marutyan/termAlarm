package com.marutyan.termalarm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.marutyan.termalarm.domain.AppTheme

/**
 * アプリ固有色(CustomColors)を提供・取得するためのCompositionLocal。
 * テーマ内で階層下のComposableへ独自配色を行き渡らせるために用いる。
 */
val LocalCustomColors = staticCompositionLocalOf<CustomColors> {
    NavyCustomColors
}

/**
 * MaterialThemeからアプリ固有のCustomColorsへアクセスするための拡張プロパティ。
 * 画面部品の実装において目盛や罫線、薄い文字の色を取得するために用いる。
 */
val MaterialTheme.customColors: CustomColors
    @Composable
    get() = LocalCustomColors.current

/**
 * 指定されたAppThemeに対応するMaterial3 ColorSchemeを解決する関数。
 * DYNAMIC指定時は動的生成配色またはフォールバック配色を選択してテーマに提供するために用いる。
 */
fun resolveColorScheme(
    appTheme: AppTheme,
    dynamicColors: ColorScheme? = null,
): ColorScheme = when (appTheme) {
    AppTheme.NAVY -> NavyColorScheme
    AppTheme.LIGHT -> LightColorScheme
    AppTheme.BLACK -> BlackColorScheme
    AppTheme.DYNAMIC -> dynamicColors ?: NavyColorScheme
}

/**
 * 指定されたAppThemeとColorSchemeからアプリ固有のCustomColorsを解決する関数。
 * DYNAMIC指定時はColorSchemeのスロットから対応する色を導出して提供するために用いる。
 */
fun resolveCustomColors(
    appTheme: AppTheme,
    colorScheme: ColorScheme,
): CustomColors = when (appTheme) {
    AppTheme.NAVY -> NavyCustomColors
    AppTheme.LIGHT -> LightCustomColors
    AppTheme.BLACK -> BlackCustomColors
    AppTheme.DYNAMIC -> dynamicCustomColors(colorScheme)
}

/**
 * TermAlarm全体のテーマを適用するComposable関数。
 * AppThemeに応じたMaterial3 ColorSchemeとCustomColorsを設定し、タイポグラフィやモーションとともに下位コンポーネントへ提供する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TermAlarmTheme(
    appTheme: AppTheme = AppTheme.NAVY,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isDynamic = appTheme == AppTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicColors = if (isDynamic) {
        val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.withSystemErrorColors(darkTheme)
    } else {
        null
    }

    val colorScheme = remember(appTheme, darkTheme, dynamicColors) {
        resolveColorScheme(appTheme, dynamicColors)
    }
    val customColors = remember(appTheme, colorScheme) {
        resolveCustomColors(appTheme, colorScheme)
    }

    CompositionLocalProvider(
        LocalCustomColors provides customColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = AppTypography,
            content = content,
        )
    }
}

/**
 * 動的配色利用時にシステムの注意色(error)を補正して反映する拡張関数。
 * ComposeのDynamic Colorで反映されない端末本来のエラー色をAndroid 14以降の資源から読み取って適用するために用いる。
 */
@Composable
private fun ColorScheme.withSystemErrorColors(darkTheme: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return this
    val errorColor = colorResource(if (darkTheme) android.R.color.system_error_dark else android.R.color.system_error_light)
    val onErrorColor = colorResource(if (darkTheme) android.R.color.system_on_error_dark else android.R.color.system_on_error_light)
    val errorContainerColor = colorResource(
        if (darkTheme) android.R.color.system_error_container_dark else android.R.color.system_error_container_light,
    )
    val onErrorContainerColor = colorResource(
        if (darkTheme) android.R.color.system_on_error_container_dark else android.R.color.system_on_error_container_light,
    )
    val base = this
    return remember(base, errorColor, onErrorColor, errorContainerColor, onErrorContainerColor) {
        base.copy(
            error = errorColor,
            onError = onErrorColor,
            errorContainer = errorContainerColor,
            onErrorContainer = onErrorContainerColor,
        )
    }
}
