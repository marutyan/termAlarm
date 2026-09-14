package com.marutyan.termalarm.ui.alarmedit

import com.marutyan.termalarm.domain.ChallengeLevel
import com.marutyan.termalarm.domain.ChallengeTiming
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `加速設定時の開始間隔と終了間隔がAlarmScheduleに反映される`() {
        val state = AlarmEditUiState(
            id = 1L,
            startMinutes = 420,
            endMinutes = 540,
            isVariableInterval = true,
            startIntervalMinutes = 10,
            endIntervalMinutes = 3,
            repeatDays = emptySet(),
            label = "加速テスト",
            enabled = true,
        )
        val schedule = state.toSchedule(existingSkippedSessionStart = null)

        assertEquals(10, schedule.startIntervalMinutes)
        assertEquals(3, schedule.endIntervalMinutes)
    }

    @Test
    fun `等間隔設定時は終了間隔が開始間隔と同じ値になる`() {
        val state = AlarmEditUiState(
            id = 1L,
            startMinutes = 420,
            endMinutes = 540,
            isVariableInterval = false,
            startIntervalMinutes = 15,
            endIntervalMinutes = 5, // isVariableIntervalがfalseなら無視されて15になる
            repeatDays = emptySet(),
            label = "等間隔テスト",
            enabled = true,
        )
        val schedule = state.toSchedule(existingSkippedSessionStart = null)

        assertEquals(15, schedule.startIntervalMinutes)
        assertEquals(15, schedule.endIntervalMinutes)
    }

    @Test
    fun `新規作成時のIDが0になる`() {
        val state = AlarmEditUiState(id = null)
        val schedule = state.toSchedule(existingSkippedSessionStart = null)
        assertEquals(0L, schedule.id)
    }

    @Test
    fun `鳴動時刻のプレビュー文字列が1行で省略される`() {
        val times = listOf(
            "7:00", "7:10", "7:19", "7:27", "7:34",
            "7:40", "7:45", "7:50", "7:54", "7:58", "8:00",
        )
        val preview = formatOccurrenceTimesPreview(times)
        assertEquals("7:00, 7:10, 7:19, 7:27 … 8:00", preview)
        assertEquals(1, preview.lines().size)
    }

    @Test
    fun `isSingleAlarmがtrueのとき開始と終了が同値でAlarmScheduleに変換される`() {
        val state = AlarmEditUiState(
            id = 1L,
            startMinutes = 12 * 60 + 30,
            endMinutes = 12 * 60 + 30,
            isSingleAlarm = true,
            label = "昼寝",
            enabled = true,
        )
        val schedule = state.toSchedule(existingSkippedSessionStart = null)
        assertEquals(schedule.startMinutes, schedule.endMinutes)
        assertEquals(12 * 60 + 30, schedule.startMinutes)
        assertTrue(state.isSingleAlarm)
    }
}
