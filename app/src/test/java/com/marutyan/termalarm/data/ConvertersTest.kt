package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.ChallengeLevel
import com.marutyan.termalarm.domain.ChallengeTiming
import com.marutyan.termalarm.domain.GameType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Roomの型変換（TypeConverter）の動作を検証するテスト。
 * DBに保存される文字列とdomainのenumが正しく相互変換されることを保証するために必要。
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `ChallengeTimingの文字列相互変換が正しく動作し未知の値はNEVERに倒れる`() {
        assertEquals("NEVER", converters.fromChallengeTiming(ChallengeTiming.NEVER))
        assertEquals("END_ONLY", converters.fromChallengeTiming(ChallengeTiming.END_ONLY))
        assertEquals("EVERY_TIME", converters.fromChallengeTiming(ChallengeTiming.EVERY_TIME))

        assertEquals(ChallengeTiming.NEVER, converters.toChallengeTiming("NEVER"))
        assertEquals(ChallengeTiming.END_ONLY, converters.toChallengeTiming("END_ONLY"))
        assertEquals(ChallengeTiming.EVERY_TIME, converters.toChallengeTiming("EVERY_TIME"))
        assertEquals(ChallengeTiming.NEVER, converters.toChallengeTiming("UNKNOWN"))
    }

    @Test
    fun `ChallengeLevelの文字列相互変換が正しく動作し未知の値はEASYに倒れる`() {
        assertEquals("EASY", converters.fromChallengeLevel(ChallengeLevel.EASY))
        assertEquals("HARD", converters.fromChallengeLevel(ChallengeLevel.HARD))

        assertEquals(ChallengeLevel.EASY, converters.toChallengeLevel("EASY"))
        assertEquals(ChallengeLevel.HARD, converters.toChallengeLevel("HARD"))
        assertEquals(ChallengeLevel.EASY, converters.toChallengeLevel("UNKNOWN"))
    }

    @Test
    fun `GameTypeの集合が文字列と正しく相互変換され未知の値は除外される`() {
        val games = setOf(GameType.MIRROR_TEXT, GameType.WALK, GameType.SEQUENCE_RECALL)
        val serialized = converters.fromGameTypeSet(games)
        assertEquals("MIRROR_TEXT,WALK,SEQUENCE_RECALL", serialized)

        val deserialized = converters.toGameTypeSet(serialized)
        assertEquals(games, deserialized)

        // 未知の値が含まれていても無視されること
        val withUnknown = converters.toGameTypeSet("MIRROR_TEXT,UNKNOWN_GAME,WALK")
        assertEquals(setOf(GameType.MIRROR_TEXT, GameType.WALK), withUnknown)

        // 空文字列の場合は空セットになること
        assertEquals(emptySet<GameType>(), converters.toGameTypeSet(""))
        assertEquals("", converters.fromGameTypeSet(emptySet()))
    }
}
