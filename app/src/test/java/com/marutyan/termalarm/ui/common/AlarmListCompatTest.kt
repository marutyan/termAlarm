package com.marutyan.termalarm.ui.common

import com.marutyan.termalarm.ui.alarmlist.orderedDaysOfWeek
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AlarmListCompatの互換関数orderedDaysOfWeekの単体テスト。
 */
class AlarmListCompatTest {

    @Test
    fun `月曜始まりでDayOfWeekの並びそのまま`() {
        val expected = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
        )
        assertEquals(expected, orderedDaysOfWeek())
    }

    @Test
    fun `7曜日を過不足なく含む`() {
        assertEquals(DayOfWeek.entries.toSet(), orderedDaysOfWeek().toSet())
    }
}
