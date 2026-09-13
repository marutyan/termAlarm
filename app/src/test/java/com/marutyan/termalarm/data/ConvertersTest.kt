package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.ChallengeLevel
import com.marutyan.termalarm.domain.ChallengeTiming
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
}
