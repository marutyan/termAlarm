package com.marutyan.termalarm.ui.alarmlist

import com.marutyan.termalarm.domain.WeekStart
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

// orderedDaysOfWeekは設定「週の始まり」を曜日チップの並び順へ反映する判定。
// ui/alarmedit/AlarmEditScreen.ktでも同じ関数を使い回すため、ここでまとめて検証する。
class AlarmListScreenKtTest {

    @Test
    fun `既定値(月曜始まり)はDayOfWeekの並びそのまま`() {
        assertEquals(DayOfWeek.entries, orderedDaysOfWeek(WeekStart.MONDAY))
    }

    @Test
    fun `日曜始まりに変えると日曜が先頭に来て残りは月曜からの順を保つ`() {
        val expected = listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
        )
        assertEquals(expected, orderedDaysOfWeek(WeekStart.SUNDAY))
    }

    @Test
    fun `どちらの並びも7曜日を過不足なく含む`() {
        assertEquals(DayOfWeek.entries.toSet(), orderedDaysOfWeek(WeekStart.SUNDAY).toSet())
        assertEquals(DayOfWeek.entries.toSet(), orderedDaysOfWeek(WeekStart.MONDAY).toSet())
    }
}
