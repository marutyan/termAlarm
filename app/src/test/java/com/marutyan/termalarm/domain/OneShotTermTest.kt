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
 * 曜日を指定していないターム（一回きり）のふるまいを確かめるテスト。
 *
 * 期待する動き。オンにしたら、今日のその時刻がまだ来ていなければ今日、
 * 過ぎていれば翌日に鳴る。鳴り終わったら自分でオフになる。
 * 曜日を指定していればその曜日ごとに繰り返し、勝手にオフにはならない。
 */
class OneShotTermTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")

    // 18:55から19:10まで5分ごと。曜日は指定しない
    private fun oneShot(enabled: Boolean = true, skipped: java.time.LocalDate? = null) = AlarmSchedule(
        id = 1,
        startMinutes = 18 * 60 + 55,
        endMinutes = 19 * 60 + 10,
        startIntervalMinutes = 5,
        endIntervalMinutes = 5,
        repeatDays = emptySet(),
        label = "",
        enabled = enabled,
        skippedSessionStart = skipped,
    )

    private fun at(hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 9, 14, hour, minute, 0, 0, zone)

    @Test
    fun `開始前なら今日のその時刻に鳴る`() {
        val next = nextTrigger(oneShot(), at(18, 0))
        assertEquals(at(18, 55), next)
    }

    @Test
    fun `終わったあとは翌日のその時刻に鳴る`() {
        val next = nextTrigger(oneShot(), at(19, 30))
        assertEquals(at(18, 55).plusDays(1), next)
    }

    @Test
    fun `オフなら次の鳴動は無い`() {
        assertNull(nextTrigger(oneShot(enabled = false), at(18, 0)))
    }

    @Test
    fun `開始前はまだ鳴り終わっていない`() {
        assertFalse(isOneShotSessionFinished(oneShot(), at(18, 0)))
    }

    @Test
    fun `途中はまだ鳴り終わっていない`() {
        assertFalse(isOneShotSessionFinished(oneShot(), at(19, 0)))
    }

    @Test
    fun `最後の回を過ぎたら鳴り終わっている`() {
        assertTrue(isOneShotSessionFinished(oneShot(), at(19, 30)))
    }

    @Test
    fun `今日のぶんを終了させたら鳴り終わっている`() {
        val skipped = oneShot(skipped = at(19, 0).toLocalDate())
        assertTrue(isOneShotSessionFinished(skipped, at(19, 0)))
    }

    @Test
    fun `曜日を指定したタームは勝手にオフにならない`() {
        val weekly = oneShot().copy(repeatDays = setOf(DayOfWeek.MONDAY))
        assertFalse(isOneShotSessionFinished(weekly, at(19, 30)))
        // 2026-09-14は月曜。翌週の月曜に鳴る
        assertEquals(at(18, 55).plusDays(7), nextTrigger(weekly, at(19, 30)))
    }
}
