package com.marutyan.termalarm.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画面に出す文字が下限を下回っていないことを確かめるテスト。
 *
 * 小さい文字は、作っている最中は問題なく見えても、実機では読めないことが多い。
 * 実際、11sp前後の文字が38か所あり、まとめて引き上げた。
 * 同じことが繰り返されないよう、ソースを直接見て下限を守らせる。
 */
class TextMinSizeTest {

    /** 画面に出す文字の下限(sp)。[TEXT_MIN_SIZE] と同じ値を、あえて別に書いて突き合わせる。 */
    private val minSizeSp = 13.0f

    private val sourceRoot = File("src/main/java/com/marutyan/termalarm")

    // fontSize = 12.sp / minFontSize = 11.5.sp のような書き方から数値を取り出す
    private val fontSizePattern = Regex("""(?:fontSize|minFontSize)\s*=\s*([0-9]+(?:\.[0-9]+)?)\.sp""")

    @Test
    fun `定数の値が下限と一致している`() {
        assertTrue(
            "TEXT_MIN_SIZE がテストの期待値と違う。どちらかを直すこと",
            TEXT_MIN_SIZE.value == minSizeSp,
        )
    }

    @Test
    fun `画面に下限より小さい文字が無い`() {
        assertTrue("ソースの場所が見つからない: ${sourceRoot.absolutePath}", sourceRoot.isDirectory)
        val violations = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    fontSizePattern.findAll(line).forEach { match ->
                        val size = match.groupValues[1].toFloat()
                        if (size < minSizeSp) {
                            violations += "${file.path}:${index + 1} ${match.value}"
                        }
                    }
                }
            }
        assertTrue(
            "下限(${minSizeSp}sp)より小さい文字がある:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }
}
