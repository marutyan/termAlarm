package com.marutyan.termalarm.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// 指定した1種類だけを許可してgenerateGameQuestionを呼ぶテスト用ヘルパー。
// excludedTypesに他の全種類を渡すことで、狙った種類の出題ロジックと除外フィルタを同時に検証する
private fun onlyType(type: GameType, random: Random): GameQuestion =
    generateGameQuestion(random, excludedTypes = GameType.entries.toSet() - type)

class GameTest {

    @Test
    fun `計算問題は左辺と右辺から演算した結果が正解になる`() {
        val q = onlyType(GameType.ARITHMETIC, Random(1)) as GameQuestion.Arithmetic
        val expected = if (q.isAddition) q.left + q.right else q.left - q.right
        assertEquals(expected.toString(), q.correctAnswer)
        assertTrue(q.left in 10..99)
        assertTrue(q.right in 10..99)

        assertTrue(judgeGameAnswer(q, expected.toString()))
        assertFalse(judgeGameAnswer(q, (expected + 1).toString()))
    }

    @Test
    fun `順にタップは1から12のシャッフルで正解は昇順の並び`() {
        val q = onlyType(GameType.SEQUENTIAL_TAP, Random(2)) as GameQuestion.SequentialTap
        assertEquals((1..12).toList(), q.shuffledNumbers.sorted())
        assertEquals("1,2,3,4,5,6,7,8,9,10,11,12", q.correctAnswer)

        assertTrue(judgeGameAnswer(q, "1,2,3,4,5,6,7,8,9,10,11,12"))
        assertFalse(judgeGameAnswer(q, "2,1,3,4,5,6,7,8,9,10,11,12"))
    }

    @Test
    fun `書き写しは8文字でユーザー入力が完全一致すれば正解`() {
        val q = onlyType(GameType.TRANSCRIBE, Random(3)) as GameQuestion.Transcribe
        assertEquals(8, q.text.length)

        assertTrue(judgeGameAnswer(q, q.text))
        assertFalse(judgeGameAnswer(q, q.text.lowercase()))
    }

    @Test
    fun `端末を振るは規定回数に達したら正解`() {
        val q = onlyType(GameType.SHAKE_DEVICE, Random(4)) as GameQuestion.ShakeDevice
        assertTrue(q.requiredShakes > 0)

        assertTrue(judgeGameAnswer(q, q.requiredShakes.toString()))
        assertFalse(judgeGameAnswer(q, (q.requiredShakes - 1).toString()))
    }

    @Test
    fun `図形を数えるはtargetの出現数が正解`() {
        val q = onlyType(GameType.COUNT_SHAPES, Random(5)) as GameQuestion.CountShapes
        val actualCount = q.shapes.count { it == q.target }
        assertEquals(actualCount.toString(), q.correctAnswer)
        assertEquals(12, q.shapes.size)

        assertTrue(judgeGameAnswer(q, actualCount.toString()))
        assertFalse(judgeGameAnswer(q, (actualCount + 1).toString()))
    }

    @Test
    fun `色と文字は文字色が正解で語の意味とは異なる色になる`() {
        val q = onlyType(GameType.COLOR_WORD, Random(6)) as GameQuestion.ColorWord
        assertNotEquals(q.word, q.displayColor)
        assertTrue(q.displayColor in q.choices)
        assertEquals(q.displayColor, q.correctAnswer)

        assertTrue(judgeGameAnswer(q, q.displayColor))
        assertFalse(judgeGameAnswer(q, q.word))
    }

    @Test
    fun `excludedTypesに含めた種類は何度generateしても出題されない`() {
        val random = Random(42)
        repeat(200) {
            val q = generateGameQuestion(random, excludedTypes = setOf(GameType.SHAKE_DEVICE))
            assertNotEquals(GameType.SHAKE_DEVICE, q.type)
        }
    }

    @Test
    fun `全種類を除外すると出題できず例外になる`() {
        assertThrows(IllegalArgumentException::class.java) {
            generateGameQuestion(Random(7), excludedTypes = GameType.entries.toSet())
        }
    }
}
