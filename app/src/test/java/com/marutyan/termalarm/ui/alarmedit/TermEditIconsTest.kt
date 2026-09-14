package com.marutyan.termalarm.ui.alarmedit

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ターム編集関連アイコンが正しい寸法およびベクターノード構造で生成されているかを検証するテスト。
 * Canvas描画からImageVectorへの移行に伴い、円・点・斜線などの構成要素が欠損なく描画可能であることを担保する。
 */
class TermEditIconsTest {

    /**
     * すべてのアイコンが24dp×24dpのデフォルト寸法および24f×24fのビューポートで構築されていることを検証する。
     */
    @Test
    fun 各アイコンが24の座標系と寸法を持つ() {
        val icons = listOf(
            TermTagIcon,
            TermIntervalBarsIcon,
            TermQuestionCircleIcon,
            TermWakeCheckIcon,
            TermChevronRightIcon,
            TermArrowRightIcon,
            TermEndIcon,
        )

        for (icon in icons) {
            assertEquals(24.dp, icon.defaultWidth)
            assertEquals(24.dp, icon.defaultHeight)
            assertEquals(24f, icon.viewportWidth)
            assertEquals(24f, icon.viewportHeight)
        }
    }

    /**
     * TermQuestionCircleIconに外側の円・疑問符曲線・下部の点の3つの描画要素が含まれていることを検証する。
     * 旧Canvas実装でscale=0により外側の円が消失していた不具合の再発を防ぐ。
     */
    @Test
    fun TermQuestionCircleIconの外側の円と疑問符と点が含まれる() {
        val paths = TermQuestionCircleIcon.root.filterIsInstance<VectorPath>()
        // 外側の円(stroke)、疑問符曲線(stroke)、下部の点(fill)の3要素
        assertEquals(3, paths.size)

        // 第1要素: 外側の円（線幅1.8f）
        val outerCircle = paths[0]
        assertEquals(1.8f, outerCircle.strokeLineWidth)

        // 第2要素: 疑問符の曲線（線幅2.2f）
        val questionMark = paths[1]
        assertEquals(2.2f, questionMark.strokeLineWidth)

        // 第3要素: 下部の点（塗りつぶしfillが指定されていること）
        val dot = paths[2]
        assertTrue("下部の点は塗りつぶしが有効であること", dot.fill != null)
    }

    /**
     * TermTagIconに外枠パスと穴（小さな塗りつぶし円）の2要素が含まれていることを検証する。
     * 旧Canvas実装でscale=0により穴が消失していた不具合の再発を防ぐ。
     */
    @Test
    fun TermTagIconの外枠と穴の点が含まれる() {
        val paths = TermTagIcon.root.filterIsInstance<VectorPath>()
        // 外枠線(stroke)と穴の点(fill)の2要素
        assertEquals(2, paths.size)

        // 第1要素: タグ外枠線（線幅1.6f）
        val outline = paths[0]
        assertEquals(1.6f, outline.strokeLineWidth)

        // 第2要素: タグの穴（塗りつぶしfillが指定されていること）
        val hole = paths[1]
        assertTrue("タグの穴は塗りつぶしが有効であること", hole.fill != null)
    }

    /**
     * TermEndIconが線幅1.7fで外周円と中央を横切る斜線を含むパスとして構築されていることを検証する。
     * 旧実装で斜線が左上隅に偏っていた不具合を解消し、円と斜線が一体のベクターとして描かれることを担保する。
     */
    @Test
    fun TermEndIconの外周円と中央斜線が含まれる() {
        val paths = TermEndIcon.root.filterIsInstance<VectorPath>()
        assertEquals(1, paths.size)

        val endPath = paths[0]
        assertEquals(1.7f, endPath.strokeLineWidth)
        // ノード数が円(弧)と斜線を含んで複数存在することを確認
        assertTrue(endPath.pathData.size >= 3)
    }
}
