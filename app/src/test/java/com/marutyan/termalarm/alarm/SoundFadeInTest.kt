package com.marutyan.termalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundFadeInTest {

    @Test
    fun `既定の5秒はフェードインありのミリ秒を返す`() {
        assertEquals(5000L, SoundFadeIn.durationMillisOrNull(5))
    }

    @Test
    fun `設定で秒数を変えるとミリ秒も変わる`() {
        assertEquals(20_000L, SoundFadeIn.durationMillisOrNull(20))
    }

    @Test
    fun `タイマーの既定1・5秒は小数のまま反映する`() {
        assertEquals(1500L, SoundFadeIn.durationMillisOrNull(1.5f))
    }

    @Test
    fun `0秒は「なし」を意味しnullを返す`() {
        assertNull(SoundFadeIn.durationMillisOrNull(0))
        assertNull(SoundFadeIn.durationMillisOrNull(0f))
    }

    @Test
    fun `負の値もフェードインなし扱いにする`() {
        assertNull(SoundFadeIn.durationMillisOrNull(-1))
    }
}
