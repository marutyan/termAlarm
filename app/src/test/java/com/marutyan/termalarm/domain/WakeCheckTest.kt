package com.marutyan.termalarm.domain

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 二度寝チェックの判定を確かめるテスト。
 *
 * 仕様（docs/SPEC.md「二度寝チェック」）。
 * 範囲の最後の回を止めてから待ち時間が経つと、確認が1回だけ鳴る。
 * 範囲の途中で止めた回については確認しない。
 * 「タームを終了」で終わらせたセッションでも確認しない。
 *
 * この判定は関数としては前から存在したが、呼び出す側が無かったため一度も動いていなかった。
 * 「関数があること」ではなく「最後の回でだけ予約が入ること」を押さえる。
 */
class WakeCheckTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")

    // 7:00から8:00まで15分ごと（7:00 7:15 7:30 7:45 8:00 の5回）
    private fun term(
        wakeCheck: Boolean = true,
        skipped: java.time.LocalDate? = null,
        repeatDays: Set<DayOfWeek> = emptySet(),
    ) = AlarmSchedule(
        id = 1,
        startMinutes = 7 * 60,
        endMinutes = 8 * 60,
        startIntervalMinutes = 15,
        endIntervalMinutes = 15,
        repeatDays = repeatDays,
        label = "",
        enabled = true,
        wakeCheck = wakeCheck,
        skippedSessionStart = skipped,
    )

    private fun at(hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 9, 14, hour, minute, 0, 0, zone)

    @Test
    fun `切っていれば確認しない`() {
        assertFalse(shouldPerformWakeCheck(term(wakeCheck = false), at(8, 0)))
        assertNull(wakeCheckTime(term(wakeCheck = false), at(8, 0), 5))
    }

    @Test
    fun `入れていれば確認する`() {
        assertTrue(shouldPerformWakeCheck(term(), at(8, 0)))
    }

    @Test
    fun `タームを終了させたセッションでは確認しない`() {
        val ended = term(skipped = at(7, 30).toLocalDate())
        assertFalse(shouldPerformWakeCheck(ended, at(7, 30)))
    }

    @Test
    fun `確認の時刻は止めた時刻から待ち時間ぶん後になる`() {
        assertEquals(at(8, 5), wakeCheckTime(term(), at(8, 0), 5))
        assertEquals(at(8, 15), wakeCheckTime(term(), at(8, 0), 15))
    }

    @Test
    fun `最後の回だけ残りが0になる`() {
        // 途中の回では、まだ鳴る回が残っている
        assertEquals(4, remainingOccurrenceCount(term(), at(7, 0)))
        assertEquals(1, remainingOccurrenceCount(term(), at(7, 45)))
        // 最後の回で0になる。予約を入れる側はこれを見て判断する
        assertEquals(0, remainingOccurrenceCount(term(), at(8, 0)))
    }

    @Test
    fun `曜日を指定したタームでも同じ判定になる`() {
        val weekly = term(repeatDays = setOf(DayOfWeek.MONDAY))
        assertTrue(shouldPerformWakeCheck(weekly, at(8, 0)))
        assertEquals(0, remainingOccurrenceCount(weekly, at(8, 0)))
    }
}
