package com.marutyan.termalarm.ui.alarmedit

import com.marutyan.termalarm.domain.ChallengeTiming
import com.marutyan.termalarm.domain.ChallengeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class AlarmEditViewModelKtTest {

    @Test
    fun `AlarmEditUiStateからAlarmScheduleへの変換が正しく値を受け渡す`() {
        val state = AlarmEditUiState(
            id = 42L,
            startMinutes = 420,
            endMinutes = 480,
            intervalMinutes = 10,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            label = "起床",
            enabled = true,
            challengeTiming = ChallengeTiming.END_ONLY,
            challenge = ChallengeLevel.HARD,
            wakeCheck = true,
        )
        val schedule = state.toSchedule(existingSkippedSessionStart = null)

        assertEquals(42L, schedule.id)
        assertEquals(420, schedule.startMinutes)
        assertEquals(480, schedule.endMinutes)
        assertEquals(10, schedule.startIntervalMinutes)
        assertEquals(10, schedule.endIntervalMinutes)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), schedule.repeatDays)
        assertEquals("起床", schedule.label)
        assertTrue(schedule.enabled)
        assertEquals(ChallengeTiming.END_ONLY, schedule.challengeTiming)
        assertEquals(ChallengeLevel.HARD, schedule.challenge)
        assertTrue(schedule.wakeCheck)
    }

    @Test
    fun `新規作成時のIDが0になる`() {
        val state = AlarmEditUiState(id = null)
        val schedule = state.toSchedule(existingSkippedSessionStart = null)
        assertEquals(0L, schedule.id)
    }
}
