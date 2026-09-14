package com.marutyan.termalarm.ui.timer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * カード中央に出す残り時間と経過時間の文字列を確かめるテスト。
 * 残りを切り捨てて出すと、まだ1秒近く残っているのに「0:00」と表示され、
 * リングの残りや実際に鳴る時刻とずれて見える。この丸め方を固定する。
 */
class TimerCardFormatTest {

    @Test
    fun `残り時間はちょうどのときその秒を出す`() {
        assertEquals("1:00", formatTimerRemaining(60_000L))
        assertEquals("0:01", formatTimerRemaining(1_000L))
    }

    @Test
    fun `残り時間は端数があるとき切り上げる`() {
        // 59.001秒残っていれば、まだ1分と出す
        assertEquals("1:00", formatTimerRemaining(59_001L))
        // 0.001秒でも残っていれば1秒と出す。0:00は鳴る瞬間だけ
        assertEquals("0:01", formatTimerRemaining(1L))
    }

    @Test
    fun `残りが0のときだけ0秒と出す`() {
        assertEquals("0:00", formatTimerRemaining(0L))
        assertEquals("0:00", formatTimerRemaining(-500L))
    }

    @Test
    fun `1時間以上は時も出す`() {
        assertEquals("1:00:00", formatTimerRemaining(3_600_000L))
        assertEquals("2:03:04", formatTimerRemaining(7_384_000L))
    }

    @Test
    fun `経過時間は切り捨てる`() {
        // 0を過ぎた直後に1秒と出さない
        assertEquals("0:00", formatTimerElapsed(1L))
        assertEquals("0:00", formatTimerElapsed(999L))
        assertEquals("0:01", formatTimerElapsed(1_000L))
    }
}
