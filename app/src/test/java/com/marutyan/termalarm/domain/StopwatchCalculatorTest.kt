package com.marutyan.termalarm.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StopwatchCalculatorTest {

    // --- 経過時間の計算 ---

    @Test
    fun `動作中は経過分だけ経過時間が増える`() {
        val state = startStopwatch(nowElapsedRealtime = 1_000L, nowWallClockMillis = 100_000L)
        // 開始から4秒経過した時点
        assertEquals(4_000L, elapsedMillis(state, nowElapsedRealtime = 5_000L, nowWallClockMillis = 104_000L))
    }

    @Test
    fun `一時停止中は経過時間によらず値が変わらない`() {
        val running = startStopwatch(nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        val paused = pauseStopwatch(running, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L)
        assertEquals(4_000L, elapsedMillis(paused, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L))
        // さらに5秒経過しても一時停止中は増えない
        assertEquals(4_000L, elapsedMillis(paused, nowElapsedRealtime = 9_000L, nowWallClockMillis = 9_000L))
    }

    // --- 一時停止→再開 ---

    @Test
    fun `一時停止していた時間は再開後の経過時間に加算されない`() {
        val running = startStopwatch(nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        val paused = pauseStopwatch(running, nowElapsedRealtime = 3_000L, nowWallClockMillis = 3_000L) // 経過3秒で停止
        // 一時停止のまま5秒経過してから再開する
        val resumed = resumeStopwatch(paused, nowElapsedRealtime = 8_000L, nowWallClockMillis = 8_000L)
        // 再開直後は一時停止時と同じ経過3秒(停止していた5秒分は加算されない)
        assertEquals(3_000L, elapsedMillis(resumed, nowElapsedRealtime = 8_000L, nowWallClockMillis = 8_000L))
        // 再開から2秒後は5秒(停止前の経過3秒+停止後の経過2秒=5秒。停止中の5秒は含まない)
        assertEquals(5_000L, elapsedMillis(resumed, nowElapsedRealtime = 10_000L, nowWallClockMillis = 10_000L))
    }

    // --- ラップ ---

    @Test
    fun `ラップは直前ラップからの所要時間とその時点の合計を持つ`() {
        val laps = mutableListOf<StopwatchLap>()
        val lap1 = recordLap(currentTotalMillis = 5_000L, previousLaps = laps)
        assertEquals(StopwatchLap(lapNumber = 1, lapMillis = 5_000L, totalMillis = 5_000L), lap1)
        laps.add(lap1)

        val lap2 = recordLap(currentTotalMillis = 12_000L, previousLaps = laps)
        assertEquals(StopwatchLap(lapNumber = 2, lapMillis = 7_000L, totalMillis = 12_000L), lap2)
        laps.add(lap2)

        val lap3 = recordLap(currentTotalMillis = 12_500L, previousLaps = laps)
        assertEquals(StopwatchLap(lapNumber = 3, lapMillis = 500L, totalMillis = 12_500L), lap3)
    }

    // --- リセット ---

    @Test
    fun `リセットすると経過時間が0でIDLEに戻る`() {
        val running = startStopwatch(nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        val paused = pauseStopwatch(running, nowElapsedRealtime = 9_000L, nowWallClockMillis = 9_000L)
        val reset = resetStopwatch(nowElapsedRealtime = 20_000L, nowWallClockMillis = 20_000L)
        assertEquals(0L, elapsedMillis(reset, nowElapsedRealtime = 25_000L, nowWallClockMillis = 25_000L))
        assertEquals(StopwatchRunState.IDLE, reset.runState)
        // リセット前のpausedの値には影響しない(参照透過であることの確認)
        assertEquals(9_000L, elapsedMillis(paused, nowElapsedRealtime = 25_000L, nowWallClockMillis = 25_000L))
    }

    // --- 再起動をまたいだ復元 ---

    @Test
    fun `再起動でelapsedRealtimeが巻き戻ると壁時計の差分で経過時間を計算する`() {
        val wallStart = 1_700_000_000_000L
        val running = startStopwatch(nowElapsedRealtime = 5_000L, nowWallClockMillis = wallStart)

        // 再起動後、elapsedRealtimeは0から数え直される(anchorより小さい値になる)。
        // 実世界では開始から8秒後に相当する壁時計時刻とする
        val nowElapsedAfterReboot = 200L
        val nowWallAfterReboot = wallStart + 8_000L
        assertEquals(8_000L, elapsedMillis(running, nowElapsedAfterReboot, nowWallAfterReboot))

        // rebaseStopwatchAfterReboot()で以後の計算をelapsedRealtime基準へ戻す
        val rebased = rebaseStopwatchAfterReboot(running, nowElapsedAfterReboot, nowWallAfterReboot)
        assertEquals(8_000L, rebased.accumulatedMillis)
        assertEquals(nowElapsedAfterReboot, rebased.anchorElapsedRealtime)
        assertEquals(StopwatchRunState.RUNNING, rebased.runState)
        // 再アンカー後はelapsedRealtime基準で普通に増えていく
        assertEquals(8_100L, elapsedMillis(rebased, nowElapsedAfterReboot + 100L, nowWallAfterReboot + 100L))
    }

    @Test
    fun `PAUSED中の状態は再起動を挟んでも再アンカーされない`() {
        val running = startStopwatch(nowElapsedRealtime = 0L, nowWallClockMillis = 0L)
        val paused = pauseStopwatch(running, nowElapsedRealtime = 4_000L, nowWallClockMillis = 4_000L)
        val rebased = rebaseStopwatchAfterReboot(paused, nowElapsedRealtime = 100L, nowWallClockMillis = 999_999L)
        // RUNNING以外は対象外でそのまま返る
        assertEquals(paused, rebased)
    }
}
