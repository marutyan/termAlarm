package com.marutyan.termalarm.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// 指定した1種類だけを許可してgenerateGameQuestionを呼ぶテスト用ヘルパー。
private fun onlyType(type: GameType, random: Random): GameQuestion =
    generateGameQuestion(random, allowedTypes = setOf(type))

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
    fun `鏡文字は英数字8文字であり表示した文字列と同じ入力で正解になる`() {
        val q = onlyType(GameType.MIRROR_TEXT, Random(10)) as GameQuestion.MirrorText
        assertEquals(8, q.text.length)
        assertEquals(q.text, q.correctAnswer)

        assertTrue(judgeGameAnswer(q, q.text))
        assertFalse(judgeGameAnswer(q, q.text.lowercase()))
        assertFalse(judgeGameAnswer(q, "DIFFERENT"))
    }

    @Test
    fun `光った順を再現は1から9から重複なしで5つを選んでいる`() {
        val q = onlyType(GameType.SEQUENCE_RECALL, Random(20)) as GameQuestion.SequenceRecall
        assertEquals(5, q.sequence.size)
        assertEquals(5, q.sequence.toSet().size)
        assertTrue(q.sequence.all { it in 1..9 })
    }

    @Test
    fun `光った順を再現は同じ並びをカンマ区切りで渡すと正解になり順を入れ替えると不正解になる`() {
        val q = onlyType(GameType.SEQUENCE_RECALL, Random(20)) as GameQuestion.SequenceRecall
        val correct = q.sequence.joinToString(",")
        val swapped = q.sequence.reversed().joinToString(",")

        assertTrue(judgeGameAnswer(q, correct))
        assertFalse(judgeGameAnswer(q, swapped))

        // 固定の具体値による直接検証
        val manual = GameQuestion.SequenceRecall(listOf(1, 3, 5, 7, 9), "1,3,5,7,9")
        assertTrue(judgeGameAnswer(manual, "1,3,5,7,9"))
        assertFalse(judgeGameAnswer(manual, "9,7,5,3,1"))
    }

    @Test
    fun `神経衰弱は12枚で6種類の絵柄が2枚ずつ入っている`() {
        val q = onlyType(GameType.MEMORY_PAIRS, Random(30)) as GameQuestion.MemoryPairs
        assertEquals(12, q.cards.size)
        val distinctKinds = q.cards.toSet()
        assertEquals(setOf(0, 1, 2, 3, 4, 5), distinctKinds)
        for (kind in 0..5) {
            assertEquals(2, q.cards.count { it == kind })
        }
    }

    @Test
    fun `神経衰弱は6で正解になる`() {
        val q = onlyType(GameType.MEMORY_PAIRS, Random(30)) as GameQuestion.MemoryPairs
        assertEquals("6", q.correctAnswer)

        assertTrue(judgeGameAnswer(q, "6"))
        assertFalse(judgeGameAnswer(q, "5"))
        assertFalse(judgeGameAnswer(q, "0"))
    }

    @Test
    fun `歩くは必要な歩数と同じ数を渡すと正解になる`() {
        val q = onlyType(GameType.WALK, Random(40)) as GameQuestion.Walk
        assertEquals(30, q.requiredSteps)
        assertEquals("30", q.correctAnswer)

        assertTrue(judgeGameAnswer(q, "30"))
        assertFalse(judgeGameAnswer(q, "29"))
        assertFalse(judgeGameAnswer(q, "31"))
    }

    @Test
    fun `allowedTypesに1種類だけ渡すと必ずその種類が出る`() {
        val random = Random(42)
        repeat(50) {
            val q = generateGameQuestion(random, allowedTypes = setOf(GameType.WALK))
            assertEquals(GameType.WALK, q.type)
        }
    }

    @Test
    fun `allowedTypesが空のとき例外になる`() {
        assertThrows(IllegalArgumentException::class.java) {
            generateGameQuestion(Random(7), allowedTypes = emptySet())
        }
    }

    // 回答用のテンキーに符号が無いため、引き算の答えは負になってはならない。
    // 1問だけでは偶然通るので、多数のseedで不変条件が保たれることを確かめる。
    @Test
    fun `引き算の答えが負にならない`() {
        repeat(500) { seed ->
            val q = onlyType(GameType.ARITHMETIC, Random(seed)) as GameQuestion.Arithmetic
            val answer = q.correctAnswer.toInt()
            assertTrue("seed=$seed で答えが負になった: $q", answer >= 0)
            if (!q.isAddition) {
                assertTrue("seed=$seed で左辺が右辺より小さい: $q", q.left >= q.right)
            }
        }
    }
}
