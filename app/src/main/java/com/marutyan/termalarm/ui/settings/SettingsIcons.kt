package com.marutyan.termalarm.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * SVGパス文字列から線画スタイルのImageVectorを動的に構築する内部関数。
 * design/Settings.dc.htmlで定義された正確なアイコンパスを描画するために用いる。
 */
private fun buildSettingsIcon(
    name: String,
    pathString: String,
    strokeWidth: Float = 1.6f,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString(pathString).toNodes(),
    stroke = SolidColor(Color.White),
    strokeLineWidth = strokeWidth,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
).build()

/** ベル（音）の線画アイコン。 */
val SettingsSoundIcon: ImageVector by lazy {
    buildSettingsIcon(
        name = "SettingsSound",
        pathString = "M 18,8 a 6,6 0 1,0 -12,0 c 0,7 -2.5,7 -2.5,9 h 17 C 20.5,15 18,15 18,8 z M 10.2,20.5 a 2,2 0 0,0 3.6,0",
    )
}

/** バイブレーションの線画アイコン。 */
val SettingsVibrationIcon: ImageVector by lazy {
    buildSettingsIcon(
        name = "SettingsVibration",
        pathString = "M 8,4 h 8 a 1.6,1.6 0 0,1 1.6,1.6 v 12.8 a 1.6,1.6 0 0,1 -1.6,1.6 H 8 A 1.6,1.6 0 0,1 6.4,18.4 V 5.6 A 1.6,1.6 0 0,1 8,4 z M 4,9 v 6 M 20,9 v 6",
    )
}

/** 徐々に音量を上げる（音量フェードイン）の線画アイコン。 */
val SettingsFadeInIcon: ImageVector by lazy {
    buildSettingsIcon(
        name = "SettingsFadeIn",
        pathString = "M 3,18 h 3 l 4,3 V 9 l -4,3 H 3 z M 14,9.5 a 4,4 0 0,1 0,5 M 17,7 a 8,8 0 0,1 0,10",
    )
}

/** 消音までの時間（自動消音）の線画アイコン。 */
val SettingsSilenceAfterIcon: ImageVector by lazy {
    buildSettingsIcon(
        name = "SettingsSilenceAfter",
        pathString = "M 3,18 h 3 l 4,3 V 9 l -4,3 H 3 z M 15,10 l 5,5 M 20,10 l -5,5",
    )
}

/** 二度寝チェックまでの線画アイコン。 */
val SettingsWakeCheckIcon: ImageVector by lazy {
    buildSettingsIcon(
        name = "SettingsWakeCheck",
        pathString = "M 3.2,11 a 9,9 0 1,1 3,7.5 M 3,5 v 5 h 5 M 12,8.5 V 12 l 2.6,1.8",
    )
}

/** ミニゲームの線画アイコン。 */
val SettingsMiniGamesIcon: ImageVector by lazy {
    buildSettingsIcon(
        name = "SettingsMiniGames",
        pathString = "M 6,7 h 12 a 3,3 0 0,1 3,3 v 6 a 3,3 0 0,1 -3,3 H 6 a 3,3 0 0,1 -3,-3 V 10 a 3,3 0 0,1 3,-3 z M 7.5,11 v 4 M 5.5,13 h 4 M 15.5,12.5 h 0.01 M 18,15 h 0.01",
    )
}

/** 配色の線画アイコン。 */
val SettingsThemeIcon: ImageVector by lazy {
    buildSettingsIcon(
        name = "SettingsTheme",
        pathString = "M 12,3 a 9,9 0 1,0 0,18 a 9,9 0 1,0 0,-18 z M 12,3 a 9,9 0 0,0 0,18 z",
    )
}

/** プライバシーの線画アイコン。 */
val SettingsPrivacyIcon: ImageVector by lazy {
    buildSettingsIcon(
        name = "SettingsPrivacy",
        pathString = "M 6,10 h 12 a 2,2 0 0,1 2,2 v 7 a 2,2 0 0,1 -2,2 H 6 a 2,2 0 0,1 -2,-2 v -7 a 2,2 0 0,1 2,-2 z M 8,10 V 7 a 4,4 0 0,1 8,0 v 3",
    )
}

/** このアプリについての線画アイコン。 */
val SettingsAboutIcon: ImageVector by lazy {
    buildSettingsIcon(
        name = "SettingsAbout",
        pathString = "M 12,3 a 9,9 0 1,0 0,18 a 9,9 0 1,0 0,-18 z M 12,11 v 5 M 12,8 h 0.01",
    )
}

/** 右矢印（chevron-right）の線画アイコン。 */
val SettingsChevronRightIcon: ImageVector by lazy {
    buildSettingsIcon(
        name = "SettingsChevronRight",
        pathString = "M 9,5 l 7,7 -7,7",
        strokeWidth = 1.8f,
    )
}
