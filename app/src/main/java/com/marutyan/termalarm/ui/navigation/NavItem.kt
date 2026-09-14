package com.marutyan.termalarm.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.R

/**
 * ナビゲーションに配置される各画面項目の定義。
 * ルートパス、表示用アイコン、描画サイズ、アクセシビリティラベルを一元管理するために用いる。
 */
enum class NavItem(
    val route: String,
    val icon: ImageVector,
    val iconSize: Dp,
    @StringRes val labelRes: Int,
) {
    /** ターム一覧（ホーム画面）。 */
    TERMS(
        route = "terms",
        icon = AlarmClockIcon,
        iconSize = 23.dp,
        labelRes = R.string.nav_terms,
    ),

    /** 通常アラーム画面。 */
    STANDARD_ALARM(
        route = "standard_alarm",
        icon = BellIcon,
        iconSize = 23.dp,
        labelRes = R.string.nav_standard_alarm,
    ),

    /** 記録画面。 */
    RECORD(
        route = "record",
        icon = ChartLineIcon,
        iconSize = 23.dp,
        labelRes = R.string.nav_record,
    ),

    /** タイマー画面。 */
    TIMER(
        route = "timer",
        icon = HourglassIcon,
        iconSize = 23.dp,
        labelRes = R.string.nav_timer,
    ),

    /** ストップウォッチ画面。 */
    STOPWATCH(
        route = "stopwatch",
        icon = StopwatchNavIcon,
        iconSize = 23.dp,
        labelRes = R.string.nav_stopwatch,
    ),

    /** 設定画面。 */
    SETTINGS(
        route = "settings",
        icon = SettingsGearIcon,
        iconSize = 23.dp,
        labelRes = R.string.nav_settings,
    ),
}
