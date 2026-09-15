package com.marutyan.termalarm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画面・通知・ステータスバーのチップに出る文字が食い違わないことを確かめるテスト。
 *
 * 以前は画面が切り上げ、通知が切り捨て、チップがまた別の作り方をしていて、
 * 同じ瞬間に違う秒数が出ていた。文字の作り方を1か所へ寄せた結果を固定する。
 */
class TimerDisplaySyncTest {

    private val anchorElapsed = 1_000_000L
    private val anchorWall = 1_700_000_000_000L

    private fun running(totalMillis: Long, remainingAtAnchor: Long = totalMillis) = TimerState(
        id = 1L,
        label = "",
        totalMillis = totalMillis,
        remainingMillisAtAnchor = remainingAtAnchor,
        anchorElapsedRealtime = anchorElapsed,
        anchorWallClockMillis = anchorWall,
        runState = TimerRunState.RUNNING,
    )

    private fun at(millisSinceAnchor: Long) = anchorElapsed + millisSinceAnchor

    @Test
    fun `残りがあるうちは切り上げた残り時間を出す`() {
        val timer = running(60_000L)
        assertEquals("1:00", timerDisplayText(timer, at(0), anchorWall))
        // 59.001秒残っていれば、まだ1分と出す
        assertEquals("1:00", timerDisplayText(timer, at(999), anchorWall))
        assertEquals("0:59", timerDisplayText(timer, at(1_000), anchorWall))
        // 1ミリ秒でも残っていれば0:01。0:00は出さない
        assertEquals("0:01", timerDisplayText(timer, at(59_999), anchorWall))
    }

    @Test
    fun `0になった瞬間からマイナスで数え上げる`() {
        val timer = running(60_000L)
        assertEquals("−0:00", timerDisplayText(timer, at(60_000), anchorWall))
        assertEquals("−0:00", timerDisplayText(timer, at(60_999), anchorWall))
        assertEquals("−0:01", timerDisplayText(timer, at(61_000), anchorWall))
    }

    @Test
    fun `鳴動中へ移る前でも0を過ぎていれば過ぎた扱いにする`() {
        val timer = running(60_000L)
        assertFalse(isTimerOverdue(timer, at(59_999), anchorWall))
        assertTrue(isTimerOverdue(timer, at(60_000), anchorWall))
    }

    @Test
    fun `一時停止中は数え上げずその時点の残りを出す`() {
        val paused = running(60_000L, remainingAtAnchor = 12_500L)
            .copy(runState = TimerRunState.PAUSED)
        // 時間が進んでも変わらない
        assertEquals("0:13", timerDisplayText(paused, at(0), anchorWall))
        assertEquals("0:13", timerDisplayText(paused, at(30_000), anchorWall))
        assertFalse(isTimerOverdue(paused, at(30_000), anchorWall))
    }

    @Test
    fun `残りの割合はリングとバーで同じ値になる`() {
        val timer = running(60_000L)
        assertEquals(1f, timerRemainingFraction(timer, at(0), anchorWall), 0.0001f)
        assertEquals(0.5f, timerRemainingFraction(timer, at(30_000), anchorWall), 0.0001f)
        assertEquals(0f, timerRemainingFraction(timer, at(60_000), anchorWall), 0.0001f)
        // 0を過ぎても負にはしない
        assertEquals(0f, timerRemainingFraction(timer, at(90_000), anchorWall), 0.0001f)
    }

    @Test
    fun `次に数字が変わるまでの時間は残りの端数になる`() {
        val timer = running(60_000L)
        // 残り59.4秒。0.4秒後に0:59へ下がる
        assertEquals(400L, millisUntilNextSecondBoundary(listOf(timer), at(600), anchorWall))
        // ちょうど区切りのときは、まるまる1秒待つ
        assertEquals(1000L, millisUntilNextSecondBoundary(listOf(timer), at(1_000), anchorWall))
    }

    @Test
    fun `0を過ぎたあとは数え上げの端数で変わる`() {
        val timer = running(60_000L)
        // 0を過ぎて0.3秒。あと0.7秒で−0:01へ増える
        assertEquals(700L, millisUntilNextSecondBoundary(listOf(timer), at(60_300), anchorWall))
    }

    @Test
    fun `一時停止中だけなら数字は動かないので1秒を返す`() {
        val paused = running(60_000L).copy(runState = TimerRunState.PAUSED)
        assertEquals(1000L, millisUntilNextSecondBoundary(listOf(paused), at(0), anchorWall))
    }

    @Test
    fun `複数あるときは一番早く変わるものに合わせる`() {
        val a = running(60_000L)
        val b = running(60_000L).copy(id = 2L, anchorElapsedRealtime = anchorElapsed - 250L)
        // aは残り59.4秒(0.4秒後)、bは残り59.15秒(0.15秒後)
        assertEquals(150L, millisUntilNextSecondBoundary(listOf(a, b), at(600), anchorWall))
    }

    @Test
    fun `設定時間から自動で付いた名前は名前として扱わない`() {
        assertEquals(null, running(60_000L).copy(label = "1:00").userLabelOrNull())
        assertEquals(null, running(60_000L).copy(label = "0:05").userLabelOrNull())
        assertEquals(null, running(60_000L).copy(label = "1:02:03").userLabelOrNull())
        // 延長して設定時間が変わっても、自動で付いた名前だと見分けられること
        assertEquals(null, running(125_000L).copy(label = "0:05").userLabelOrNull())
        assertEquals(null, running(60_000L).copy(label = "").userLabelOrNull())
    }

    @Test
    fun `停止して設定した長さへ戻ったタイマーは通知へ出さない`() {
        // 「停止」はリセットと同じで、設定した長さへ戻して一覧に残す。
        // これを通知へ出していたため、止めたはずのものが「一時停止中」として残り、
        // 通知そのものも消えなかった
        val stopped = running(60_000L, remainingAtAnchor = 60_000L)
            .copy(runState = TimerRunState.PAUSED)
        assertFalse(isTimerActive(stopped, at(0), anchorWall))
    }

    @Test
    fun `途中で一時停止しただけなら通知へ出す`() {
        val paused = running(60_000L, remainingAtAnchor = 30_000L)
            .copy(runState = TimerRunState.PAUSED)
        assertTrue(isTimerActive(paused, at(0), anchorWall))
    }

    @Test
    fun `動作中と鳴動中は通知へ出す`() {
        assertTrue(isTimerActive(running(60_000L), at(0), anchorWall))
        val finished = running(60_000L, remainingAtAnchor = 0L)
            .copy(runState = TimerRunState.FINISHED)
        assertTrue(isTimerActive(finished, at(5_000), anchorWall))
    }

    @Test
    fun `利用者が付けた名前はそのまま扱う`() {
        assertEquals("パスタ", running(60_000L).copy(label = "パスタ").userLabelOrNull())
        assertEquals("休憩 5:00", running(60_000L).copy(label = "休憩 5:00").userLabelOrNull())
    }

    @Test
    fun `0になる瞬間にも見回る`() {
        // 表示の区切りだけで待つと、通知の先取り(60ms)のぶん0の手前で起きてしまい、
        // まだ0ではないので鳴らさず、次に起きるのが1秒後になっていた
        val timer = running(60_000L)
        val lead = 60L
        // 残り1060ms。表示の区切りは1000ms後で、0になるのは1060ms後。早い方に合わせる
        assertEquals(1000L, millisUntilNextTimerEvent(listOf(timer), at(58_940), anchorWall, lead))
        // 残り60ms。表示の区切りはこの先1秒だが、0になるのは60ms後なので、そちらへ合わせる
        assertEquals(60L, millisUntilNextTimerEvent(listOf(timer), at(59_940), anchorWall, lead))
    }

    @Test
    fun `0を過ぎたら表示の区切りだけで見回る`() {
        val timer = running(60_000L)
        val lead = 60L
        // すでに0のものは、この見回りで鳴動中へ移る。待ち時間を0にすると短い間隔で回り続ける
        assertEquals(
            940L,
            millisUntilNextTimerEvent(listOf(timer), at(60_000), anchorWall, lead),
        )
    }

    @Test
    fun `一時停止中だけなら1秒ごとに見回る`() {
        val paused = running(60_000L).copy(runState = TimerRunState.PAUSED)
        assertEquals(1000L, millisUntilNextTimerEvent(listOf(paused), at(0), anchorWall, 60L))
    }
}
