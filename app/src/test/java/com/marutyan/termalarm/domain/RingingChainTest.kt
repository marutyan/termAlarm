package com.marutyan.termalarm.domain

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 鳴動の鎖が切れないことを確かめるテスト。
 *
 * 「鳴らす前に次の1回を予約する」に変えた。停止したときに予約していると、
 * 記録の書き込みで失敗した・鳴動中にプロセスが落ちた・強制停止された、
 * といったときに鎖が切れ、そのタームが二度と鳴らなくなる。
 *
 * ここでは、鳴っている時刻を渡したときに次の回が正しく求まることを押さえる。
 * 予約そのものはAlarmManagerが相手なので、計算の側だけを固定する。
 */
class RingingChainTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")

    // 7:00から8:00まで15分ごと（7:00 7:15 7:30 7:45 8:00 の5回）
    private fun term(repeatDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY)) = AlarmSchedule(
        id = 1,
        startMinutes = 7 * 60,
        endMinutes = 8 * 60,
        startIntervalMinutes = 15,
        endIntervalMinutes = 15,
        repeatDays = repeatDays,
        label = "",
        enabled = true,
    )

    // 2026-09-14は月曜
    private fun at(hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 9, 14, hour, minute, 0, 0, zone)

    @Test
    fun `鳴っている時刻を渡すと次の回が返る`() {
        assertEquals(at(7, 15), nextTrigger(term(), at(7, 0)))
        assertEquals(at(7, 30), nextTrigger(term(), at(7, 15)))
        assertEquals(at(8, 0), nextTrigger(term(), at(7, 45)))
    }

    @Test
    fun `最後の回で鳴らしても次の週の分が返る`() {
        // 鎖が切れないこと。最後の回の時点でも、翌週の1回目が予約できる
        assertEquals(at(7, 0).plusDays(7), nextTrigger(term(), at(8, 0)))
    }

    @Test
    fun `曜日を指定していないタームは最後の回でも翌日が返る`() {
        val oneShot = term(repeatDays = emptySet())
        assertEquals(at(7, 0).plusDays(1), nextTrigger(oneShot, at(8, 0)))
    }

    @Test
    fun `予定より遅れて鳴っても次の回を飛ばさない`() {
        // 端末の休止などで40秒遅れて発火した場合でも、7:15の次は7:30
        assertEquals(at(7, 30), nextTrigger(term(), at(7, 15).plusSeconds(40)))
    }

    @Test
    fun `切ったタームには次の回が無い`() {
        assertEquals(null, nextTrigger(term().copy(enabled = false), at(7, 0)))
    }

    @Test
    fun `今日を終了させたタームは次の週へ飛ぶ`() {
        val ended = term().copy(skippedSessionStart = at(7, 30).toLocalDate())
        val next = nextTrigger(ended, at(7, 30))
        assertNotNull(next)
        assertEquals(at(7, 0).plusDays(7), next)
    }
}
