package com.marutyan.termalarm.ui.alarmedit

import androidx.compose.ui.graphics.vector.ImageVector
import com.marutyan.termalarm.ui.navigation.addCircleFill
import com.marutyan.termalarm.ui.navigation.addCircleStroke
import com.marutyan.termalarm.ui.navigation.addStrokePath
import com.marutyan.termalarm.ui.navigation.buildVectorIcon
import com.marutyan.termalarm.ui.navigation.buildVectorIconBuilder

/**
  * タグを表す線画＋塗りつぶし円アイコン。
  * タームのラベル設定行およびラベル入力ダイアログの見出しアイコンとして用いる。
  */
val TermTagIcon: ImageVector by lazy {
    buildVectorIconBuilder("TermTag")
        .addStrokePath(
            pathString = "M 20.6,13.4 l -7.2,7.2 a 2,2 0 0,1 -2.8,0 l -7.2,-7.2 A 2,2 0 0,1 3,12 V 5 a 2,2 0 0,1 2,-2 h 7 a 2,2 0 0,1 1.4,0.6 l 7.2,7.2 a 2,2 0 0,1 0,2.6 z",
            strokeWidth = 1.6f,
        )
        .addCircleFill(
            centerX = 7.8f,
            centerY = 7.8f,
            radius = 1.3f,
        )
        .build()
}

/**
  * 4本縦線の間隔を表す線画アイコン。
  * 間隔設定行および間隔設定画面の見出しアイコンとして用いる。
  */
val TermIntervalBarsIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "TermIntervalBars",
        pathString = "M 4,6 v 12 M 10,6 v 12 M 15,6 v 12 M 19,6 v 12",
        strokeWidth = 1.7f,
    )
}

/**
  * 丸囲みクエスチョンを表す線画＋塗りつぶし点アイコン。
  * 解除チャレンジ設定行およびポップアップの見出しアイコンとして用い、識別しやすい太さで疑問符と点を構成する。
  */
val TermQuestionCircleIcon: ImageVector by lazy {
    buildVectorIconBuilder("TermQuestionCircle")
        .addCircleStroke(
            centerX = 12f,
            centerY = 12f,
            radius = 9f,
            strokeWidth = 1.8f,
        )
        .addStrokePath(
            pathString = "M 9.2,8.8 a 3,3 0 0,1 5.6,1.5 c 0,1.8 -2.8,2.2 -2.8,4.2",
            strokeWidth = 2.2f,
        )
        .addCircleFill(
            centerX = 12f,
            centerY = 17.2f,
            radius = 1.3f,
        )
        .build()
}

/**
  * 二度寝チェック用時計リピート線画アイコン。
  * ターム編集画面の二度寝チェック設定行のアイコンとして用いる。
  */
val TermWakeCheckIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "TermWakeCheck",
        pathString = "M 3.2,11 a 9,9 0 1,1 3,7.5 M 3,5 v 5 h 5 M 12,8.5 V 12 l 2.6,1.8",
        strokeWidth = 1.6f,
    )
}

/**
  * 右シェブロン(>)線画アイコン。
  * 設定行の右端に配置して別画面・ダイアログへの遷移を示唆するために用いる。
  */
val TermChevronRightIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "TermChevronRight",
        pathString = "M 9,5 l 7,7 -7,7",
        strokeWidth = 1.8f,
    )
}

/**
  * 右矢印(→)線画アイコン。
  * 間隔設定画面で開始から終了への変化方向を示すために用いる。
  */
val TermArrowRightIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "TermArrowRight",
        pathString = "M 4,12 h 15 M 14,7 l 5,5 -5,5",
        strokeWidth = 1.6f,
    )
}

/**
  * 丸に斜線の終了線画アイコン。
  * ターム終了確認ダイアログや鳴動中画面の見出しアイコンとして用いる。
  */
val TermEndIcon: ImageVector by lazy {
    buildVectorIcon(
        name = "TermEnd",
        pathString = "M 3,12 a 9,9 0 1,0 18,0 a 9,9 0 1,0 -18,0 M 8.5,8.5 l 7,7",
        strokeWidth = 1.7f,
    )
}
