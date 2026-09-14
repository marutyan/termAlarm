package com.marutyan.termalarm.ui.timer

import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * タイマー画面で、数字を入れる画面(新規作成)を出すかどうかの判定を確かめるテスト。
 *
 * 一覧の読み込みが終わる前に空リストを「0件」と受け取っていたため、
 * 保存済みのタイマーがあっても画面を開いた直後だけ新規作成の画面が見えていた。
 * 読み込み前と本当の0件を取り違えないことを、ここで固定する。
 */
class TimerKeypadVisibilityTest {

    private fun timer(id: Long) = TimerState(
        id = id,
        label = "",
        totalMillis = 60_000L,
        remainingMillisAtAnchor = 60_000L,
        anchorElapsedRealtime = 0L,
        anchorWallClockMillis = 0L,
        runState = TimerRunState.PAUSED,
    )

    @Test
    fun `読み込みが終わる前は出さない`() {
        assertFalse(shouldShowTimerKeypad(timers = null, isAddRequested = false))
    }

    @Test
    fun `読み込みが終わる前は追加ボタンの押下が残っていても出さない`() {
        // 押下の状態はrememberSaveableで復元されるため、読み込みより先に立っていることがある
        assertFalse(shouldShowTimerKeypad(timers = null, isAddRequested = true))
    }

    @Test
    fun `読み込みが終わって0件なら出す`() {
        assertTrue(shouldShowTimerKeypad(timers = emptyList(), isAddRequested = false))
    }

    @Test
    fun `1件以上あれば出さない`() {
        assertFalse(shouldShowTimerKeypad(timers = listOf(timer(1L)), isAddRequested = false))
    }

    @Test
    fun `1件以上あっても追加ボタンを押したら出す`() {
        assertTrue(shouldShowTimerKeypad(timers = listOf(timer(1L)), isAddRequested = true))
    }
}
