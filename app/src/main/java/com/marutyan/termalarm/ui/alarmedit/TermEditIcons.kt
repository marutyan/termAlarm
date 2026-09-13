package com.marutyan.termalarm.ui.alarmedit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marutyan.termalarm.ui.theme.customColors

/**
 * デザインHTMLで指定されたタグアイコンを描画するComposable。
 * タームのラベル設定行の先頭アイコンとして用いる。
 */
@Composable
fun TermTagIcon(
    modifier: Modifier = Modifier.size(20.dp),
    color: Color = MaterialTheme.customColors.subtleText,
) {
    val path = remember {
        PathParser().parsePathString("M20.6 13.4l-7.2 7.2a2 2 0 01-2.8 0l-7.2-7.2A2 2 0 013 12V5a2 2 0 012-2h7a2 2 0 011.4.6l7.2 7.2a2 2 0 010 2.6z").toPath()
    }
    Canvas(modifier = modifier) {
        val scale = size.width / 24f
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.6f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(
            color = color,
            radius = 1.3f * scale,
            center = Offset(7.8f * scale, 7.8f * scale),
        )
    }
}

/**
 * デザインHTMLで指定された4本縦線の間隔アイコンを描画するComposable。
 * 間隔設定行および間隔設定画面の見出しアイコンとして用いる。
 */
@Composable
fun TermIntervalBarsIcon(
    modifier: Modifier = Modifier.size(20.dp),
    color: Color = MaterialTheme.customColors.subtleText,
) {
    val path = remember {
        PathParser().parsePathString("M4 6v12M10 6v12M15 6v12M19 6v12").toPath()
    }
    Canvas(modifier = modifier) {
        val scale = size.width / 24f
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.7f * scale, cap = StrokeCap.Round),
        )
    }
}

/**
 * デザインHTMLで指定された丸囲みクエスチョンアイコンを描画するComposable。
 * 解除チャレンジ設定行およびポップアップの見出しアイコンとして用い、20dpのサイズでも疑問符と識別できるよう太さと大きさを最適化している。
 */
@Composable
fun TermQuestionCircleIcon(
    modifier: Modifier = Modifier.size(20.dp),
    color: Color = MaterialTheme.customColors.subtleText,
) {
    val questionPath = remember {
        PathParser().parsePathString("M9.2 8.8a3 3 0 015.6 1.5c0 1.8-2.8 2.2-2.8 4.2").toPath()
    }
    Canvas(modifier = modifier) {
        val scale = size.width / 24f
        drawCircle(
            color = color,
            radius = 9f * scale,
            center = Offset(12f * scale, 12f * scale),
            style = Stroke(width = 1.8f * scale),
        )
        drawPath(
            path = questionPath,
            color = color,
            style = Stroke(width = 2.2f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(
            color = color,
            radius = 1.3f * scale,
            center = Offset(12f * scale, 17.2f * scale),
        )
    }
}

/**
 * デザインHTMLで指定された二度寝チェック用時計リピートアイコンを描画するComposable。
 * ターム編集画面の二度寝チェック設定行のアイコンとして用いる。
 */
@Composable
fun TermWakeCheckIcon(
    modifier: Modifier = Modifier.size(20.dp),
    color: Color = MaterialTheme.customColors.subtleText,
) {
    val path1 = remember { PathParser().parsePathString("M3.2 11a9 9 0 113 7.5").toPath() }
    val path2 = remember { PathParser().parsePathString("M3 5v5h5").toPath() }
    val path3 = remember { PathParser().parsePathString("M12 8.5V12l2.6 1.8").toPath() }
    Canvas(modifier = modifier) {
        val scale = size.width / 24f
        val stroke = Stroke(width = 1.6f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path1, color, style = stroke)
        drawPath(path2, color, style = stroke)
        drawPath(path3, color, style = stroke)
    }
}

/**
 * デザインHTMLで指定された右シェブロン(>)アイコンを描画するComposable。
 * 設定行の右端に配置して別画面・ダイアログへの遷移を示唆するために用いる。
 */
@Composable
fun TermChevronRightIcon(
    modifier: Modifier = Modifier.size(17.dp),
    color: Color = MaterialTheme.customColors.subtleText,
) {
    val path = remember { PathParser().parsePathString("M9 5l7 7-7 7").toPath() }
    Canvas(modifier = modifier) {
        val scale = size.width / 24f
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.8f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * デザインHTMLで指定された右矢印(→)アイコンを描画するComposable。
 * 間隔設定画面で開始から終了への変化方向を示すために用いる。
 */
@Composable
fun TermArrowRightIcon(
    modifier: Modifier = Modifier.size(20.dp),
    color: Color = MaterialTheme.customColors.subtleText,
) {
    val path = remember { PathParser().parsePathString("M4 12h15M14 7l5 5-5 5").toPath() }
    Canvas(modifier = modifier) {
        val scale = size.width / 24f
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.6f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * デザインHTMLで指定された丸に斜線の終了アイコンを描画するComposable。
 * ターム終了確認ダイアログの見出しアイコンとして用いる。
 */
@Composable
fun TermEndIcon(
    modifier: Modifier = Modifier.size(21.dp),
    color: Color = MaterialTheme.customColors.subtleText,
) {
    val linePath = remember { PathParser().parsePathString("M8.5 8.5l7 7").toPath() }
    Canvas(modifier = modifier) {
        val scale = size.width / 24f
        drawCircle(
            color = color,
            radius = 9f * scale,
            center = Offset(12f * scale, 12f * scale),
            style = Stroke(width = 1.7f * scale),
        )
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 1.7f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
