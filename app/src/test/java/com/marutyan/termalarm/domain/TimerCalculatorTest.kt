package com.marutyan.termalarm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerCalculatorTest {

    // --- 残り時間の計算 ---

    @Test
    fun `動作中は経過分だけ残り時間が減る`() {
        val state = startTimer(id = 1L, label = "t", durationMillis = 10_000L, nowElapsedRealtime = 1_000L, nowWallClockMillis = 100_000L)
        // 開始から4秒経過した時点
        assertEquals(6_000L, remainingMillis(state, nowElapsedRealtime = 5_000L, nowWallClockMillis = 104_000L))
    }

    @Test
    fun `一時停止中は経過時間によらず残り時間が変わらない`() {
        val running = startTimer(id = 1L, label = "t", durationMillis = 10_000L, nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        val paused = pauseTimer(running, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L)
        assertEquals(6_000L, remainingMillis(paused, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L))
        // さらに5秒経過しても一時停止中は減らない
        assertEquals(6_000L, remainingMillis(paused, nowElapsedRealtime = 9_000L, nowWallClockMillis = 9_000L))
    }

    // --- 延長 ---

    @Test
    fun `動作中に延長すると合計と残りの両方に加算される`() {
        val running = startTimer(id = 1L, label = "t", durationMillis = 10_000L, nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        // 4秒経過(残り6秒)した時点で1分延長する
        val extended = extendTimer(running, extraMillis = 60_000L, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L)
        assertEquals(70_000L, extended.totalMillis)
        // 延長直後は残り6秒+60秒=66秒
        assertEquals(66_000L, remainingMillis(extended, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L))
        // 延長後さらに1秒経過すると65秒
        assertEquals(65_000L, remainingMillis(extended, nowElapsedRealtime = 5_000L, nowWallClockMillis = 5_000L))
    }

    @Test
    fun `一時停止中に延長すると再開するまで残りは変わらず加算分だけ増える`() {
        val running = startTimer(id = 1L, label = "t", durationMillis = 10_000L, nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        val paused = pauseTimer(running, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L) // 残り6秒
        val extended = extendTimer(paused, extraMillis = 60_000L, nowElapsedRealtime = 9_000L, nowWallClockMillis = 9_000L)
        assertEquals(70_000L, extended.totalMillis)
        // 一時停止中なのでnowが進んでも66秒のまま
        assertEquals(66_000L, remainingMillis(extended, nowElapsedRealtime = 20_000L, nowWallClockMillis = 20_000L))
    }

    // --- 一時停止→再開 ---

    @Test
    fun `一時停止していた時間は再開後の残り時間に加算されない`() {
        val running = startTimer(id = 1L, label = "t", durationMillis = 10_000L, nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        val paused = pauseTimer(running, nowElapsedRealtime = 3_000L, nowWallClockMillis = 3_000L) // 残り7秒
        // 一時停止のまま5秒経過してから再開する
        val resumed = resumeTimer(paused, nowElapsedRealtime = 8_000L, nowWallClockMillis = 8_000L)
        // 再開直後は一時停止時と同じ残り7秒(停止していた5秒分は消費されていない)
        assertEquals(7_000L, remainingMillis(resumed, nowElapsedRealtime = 8_000L, nowWallClockMillis = 8_000L))
        // 再開から2秒後は5秒(停止前の経過3秒+停止後の経過2秒=5秒経過分だけ減る。停止中の5秒は含まない)
        assertEquals(5_000L, remainingMillis(resumed, nowElapsedRealtime = 10_000L, nowWallClockMillis = 10_000L))
    }

    // --- 複数のタイマーの独立性 ---

    @Test
    fun `複数のタイマーはそれぞれ独立して計算される`() {
        val a = startTimer(id = 1L, label = "a", durationMillis = 10_000L, nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        val b = startTimer(id = 2L, label = "b", durationMillis = 20_000L, nowElapsedRealtime = 0L, nowWallClockMillis = 0L)

        val aPaused = pauseTimer(a, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L)
        val bExtended = extendTimer(b, extraMillis = 5_000L, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L)

        // aを一時停止・bを延長しても互いの値は混ざらない
        assertEquals(6_000L, remainingMillis(aPaused, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L))
        assertEquals(25_000L, bExtended.totalMillis)
        assertEquals(21_000L, remainingMillis(bExtended, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L))
        // aはPAUSEDのまま、bはRUNNINGのまま
        assertEquals(TimerRunState.PAUSED, aPaused.runState)
        assertEquals(TimerRunState.RUNNING, bExtended.runState)
    }

    // --- 完了判定 ---

    @Test
    fun `残り時間が尽きるとisDueがtrueになりfinishTimerでFINISHEDへ遷移する`() {
        val running = startTimer(id = 1L, label = "t", durationMillis = 10_000L, nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        assertFalse(isDue(running, nowElapsedRealtime = 9_999L, nowWallClockMillis = 9_999L))
        assertTrue(isDue(running, nowElapsedRealtime = 10_000L, nowWallClockMillis = 10_000L))

        val finished = finishTimer(running)
        assertEquals(TimerRunState.FINISHED, finished.runState)
        assertEquals(0L, finished.remainingMillisAtAnchor)
        // FINISHED中はisDueが常にfalse(RUNNINGのみが対象)
        assertFalse(isDue(finished, nowElapsedRealtime = 999_999L, nowWallClockMillis = 999_999L))
    }

    // --- 再起動をまたいだ復元 ---

    @Test
    fun `再起動でelapsedRealtimeが巻き戻ると壁時計の差分で残り時間を計算する`() {
        val wallStart = 1_700_000_000_000L
        val running = startTimer(id = 1L, label = "t", durationMillis = 10_000L, nowElapsedRealtime = 5_000L, nowWallClockMillis = wallStart)

        // 再起動後、elapsedRealtimeは0から数え直される(anchorより小さい値になる)。
        // 実世界では開始から8秒後に相当する壁時計時刻とする
        val nowElapsedAfterReboot = 200L
        val nowWallAfterReboot = wallStart + 8_000L
        assertEquals(2_000L, remainingMillis(running, nowElapsedAfterReboot, nowWallAfterReboot))

        // rebaseTimerAfterReboot()で以後の計算をelapsedRealtime基準へ戻す
        val rebased = rebaseTimerAfterReboot(running, nowElapsedAfterReboot, nowWallAfterReboot)
        assertEquals(2_000L, rebased.remainingMillisAtAnchor)
        assertEquals(nowElapsedAfterReboot, rebased.anchorElapsedRealtime)
        assertEquals(TimerRunState.RUNNING, rebased.runState)
        // 再アンカー後はelapsedRealtime基準で普通に減っていく
        assertEquals(1_900L, remainingMillis(rebased, nowElapsedAfterReboot + 100L, nowWallAfterReboot + 100L))
    }

    @Test
    fun `PAUSED中のタイマーは再起動を挟んでも再アンカーされない`() {
        val running = startTimer(id = 1L, label = "t", durationMillis = 10_000L, nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        val paused = pauseTimer(running, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L)
        val rebased = rebaseTimerAfterReboot(paused, nowElapsedRealtime = 100L, nowWallClockMillis = 999_999L)
        // RUNNING以外は対象外でそのまま返る
        assertEquals(paused, rebased)
    }
}
