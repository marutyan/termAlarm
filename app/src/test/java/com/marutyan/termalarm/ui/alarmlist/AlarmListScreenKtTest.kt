package com.marutyan.termalarm.ui.alarmlist

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

// orderedDaysOfWeekは曜日チップの並び順（月曜始まり固定）。
// ui/alarmedit/AlarmEditScreen.ktでも同じ関数を使い回すため、ここでまとめて検証する。
class AlarmListScreenKtTest {

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
