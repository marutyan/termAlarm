package com.marutyan.termalarm.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 24x24dp（viewport 24x24）のアイコン用ImageVectorビルダーを初期化して返す共通関数。
 * 線画パスや塗りつぶし、円など複数の描画要素を組み合わせて複雑なベクターアイコンを構築するために用いる。
 */
fun buildVectorIconBuilder(
    name: String,
): ImageVector.Builder = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
)

/**
 * 指定された中心座標と半径の正円を表すSVGパス文字列を生成する補助関数。
 * ベクターアイコンの内部で円の線画や塗りつぶしを定義するために用いる。
 */
fun circlePathData(centerX: Float, centerY: Float, radius: Float): String =
    "M ${centerX - radius},$centerY a $radius,$radius 0 1,0 ${2 * radius},0 a $radius,$radius 0 1,0 ${-2 * radius},0"

/**
 * SVGパス文字列から線画（stroke）要素をImageVectorに追加する拡張関数。
 * 複数の異なる太さや形状を持つ線画パスを同一アイコンビルダーに逐次追加するために用いる。
 */
fun ImageVector.Builder.addStrokePath(
    pathString: String,
    strokeWidth: Float = 1.6f,
    strokeCap: StrokeCap = StrokeCap.Round,
    strokeJoin: StrokeJoin = StrokeJoin.Round,
): ImageVector.Builder = addPath(
    pathData = PathParser().parsePathString(pathString).toNodes(),
    stroke = SolidColor(Color.White),
    strokeLineWidth = strokeWidth,
    strokeLineCap = strokeCap,
    strokeLineJoin = strokeJoin,
)

/**
 * SVGパス文字列から塗りつぶし（fill）要素をImageVectorに追加する拡張関数。
 * 閉じたパスで囲まれた領域をアイコン内で白（tint適用対象）で塗りつぶすために用いる。
 */
fun ImageVector.Builder.addFillPath(
    pathString: String,
): ImageVector.Builder = addPath(
    pathData = PathParser().parsePathString(pathString).toNodes(),
    fill = SolidColor(Color.White),
)

/**
 * 指定された中心座標と半径の円（線画）をImageVectorに追加する拡張関数。
 * 丸囲みアイコンなどの外周円を正確な線幅で描くために用いる。
 */
fun ImageVector.Builder.addCircleStroke(
    centerX: Float,
    centerY: Float,
    radius: Float,
    strokeWidth: Float = 1.6f,
    strokeCap: StrokeCap = StrokeCap.Round,
    strokeJoin: StrokeJoin = StrokeJoin.Round,
): ImageVector.Builder = addStrokePath(
    pathString = circlePathData(centerX, centerY, radius),
    strokeWidth = strokeWidth,
    strokeCap = strokeCap,
    strokeJoin = strokeJoin,
)

/**
 * 指定された中心座標と半径の塗りつぶし円（点）をImageVectorに追加する拡張関数。
 * タグの穴や疑問符の下部の点など、小さな円形ドットを忠実に再現するために用いる。
 */
fun ImageVector.Builder.addCircleFill(
    centerX: Float,
    centerY: Float,
    radius: Float,
): ImageVector.Builder = addFillPath(
    pathString = circlePathData(centerX, centerY, radius),
)

/**
 * SVGパス文字列から線画スタイルのImageVectorを動的に構築する共通関数。
 * アプリのアイコンを外部画像に依存せず、インラインのベクターデータとして描画するために用いる。
 */
fun buildVectorIcon(
    name: String,
    pathString: String,
    strokeWidth: Float = 1.6f,
    strokeCap: StrokeCap = StrokeCap.Round,
    strokeJoin: StrokeJoin = StrokeJoin.Round,
): ImageVector = buildVectorIconBuilder(name)
    .addStrokePath(pathString, strokeWidth, strokeCap, strokeJoin)
    .build()


/**
 * ターム（ホーム）を表す目覚まし時計アイコン。
 * 左ナビ第1項目の選択・非選択状態を表現するために用いる。
 */
val AlarmClockIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "AlarmClock",
        pathString = "M 4,13 a 8,8 0 1,0 16,0 a 8,8 0 1,0 -16,0 M 12,9 v 4 l 3,2 M 5,3 L 2,6 M 19,3 l 3,3",
        strokeWidth = 1.9f,
    )
}

/**
 * 通常アラームを表すベルアイコン。
 * 左ナビ第2項目の導線アイコンとして用いる。
 */
val BellIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "Bell",
        pathString = "M 18,8 a 6,6 0 1,0 -12,0 c 0,7 -2.5,7 -2.5,9 h 17 C 20.5,15 18,15 18,8 z M 10.2,20.5 a 2,2 0 0,0 3.6,0",
        strokeWidth = 1.6f,
    )
}

/**
 * 記録を表す折れ線グラフアイコン。
 * 左ナビ第3項目の導線アイコンとして用いる。
 */
val ChartLineIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "ChartLine",
        pathString = "M 3,12 h 4 l 3,-8 4,16 3,-8 h 4",
        strokeWidth = 1.6f,
    )
}

/**
 * タイマーを表す砂時計アイコン。
 * 左ナビ第4項目の導線アイコンとして用いる。
 */
val HourglassIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "Hourglass",
        pathString = "M 6,2 h 12 M 6,22 h 12 M 6.5,2 c 0,5 5.5,6 5.5,10 s -5.5,5 -5.5,10 M 17.5,2 c 0,5 -5.5,6 -5.5,10 s 5.5,5 5.5,10",
        strokeWidth = 1.6f,
    )
}

/**
 * ストップウォッチを表す計測器アイコン。
 * 左ナビ第5項目の導線アイコンとして用いる。
 */
val StopwatchNavIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "StopwatchNav",
        pathString = "M 4.5,13.5 a 7.5,7.5 0 1,0 15,0 a 7.5,7.5 0 1,0 -15,0 M 12,13.5 V 9.5 M 9.5,2 h 5 M 19.4,6.2 l 1.4,-1.4",
        strokeWidth = 1.6f,
    )
}

/**
 * 設定を表す歯車アイコン。
 * 左ナビ最下端の導線アイコンとして用いる。
 */
val SettingsGearIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "SettingsGear",
        pathString = "M 8.8,12 a 3.2,3.2 0 1,0 6.4,0 a 3.2,3.2 0 1,0 -6.4,0 M 12,2.5 v 3 M 12,18.5 v 3 M 2.5,12 h 3 M 18.5,12 h 3 M 5.2,5.2 l 2.1,2.1 M 16.7,16.7 l 2.1,2.1 M 18.8,5.2 l -2.1,2.1 M 7.3,16.7 l -2.1,2.1",
        strokeWidth = 1.6f,
    )
}

/**
 * 「このタームを終了」を表す停止円アイコン。
 * ホーム画面において現在進行中のタームを終了させるボタンのアイコンとして用いる。
 */
val EndTermIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "EndTerm",
        pathString = "M 3,12 a 9,9 0 1,0 18,0 a 9,9 0 1,0 -18,0 M 8.5,8.5 l 7,7",
        strokeWidth = 1.7f,
    )
}

/**
 * 「タームを追加」を表すプラス記号アイコン。
 * ホーム画面下部の新規ターム追加枠内のアイコンとして用いる。
 */
val AddPlusIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "AddPlus",
        pathString = "M 12,5 v 14 M 5,12 h 14",
        strokeWidth = 1.8f,
    )
}
