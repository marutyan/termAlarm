package com.marutyan.termalarm.ui.alarms

import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.data.FakeAlarmDao
import com.marutyan.termalarm.domain.AlarmSchedule
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AlarmsViewModelの単体テスト。
 * 通常アラーム一覧の購読、タームの除外、有効/無効の切り替え操作を検証する。
 */
class AlarmsViewModelTest {

    private lateinit var dao: FakeAlarmDao
    private lateinit var repository: AlarmRepository
    private lateinit var viewModel: AlarmsViewModel

    @Before
    fun setUp() {
        dao = FakeAlarmDao()
        repository = AlarmRepository(dao)
        viewModel = AlarmsViewModel(repository)
    }

    @Test
    fun `通常アラームのみが一覧に含まれタームは除外される`() = runTest {
        val termSchedule = AlarmSchedule(
            id = 1L,
            startMinutes = 7 * 60,
            endMinutes = 9 * 60,
            startIntervalMinutes = 5,
            endIntervalMinutes = 5,
            repeatDays = setOf(DayOfWeek.MONDAY),
            label = "ターム",
            enabled = true,
        )
        val singleAlarmSchedule = AlarmSchedule(
            id = 2L,
            startMinutes = 8 * 60,
            endMinutes = 8 * 60,
            startIntervalMinutes = 5,
            endIntervalMinutes = 5,
            repeatDays = emptySet(),
            label = "通常アラーム",
            enabled = true,
        )
        repository.add(termSchedule)
        repository.add(singleAlarmSchedule)

        val alarms = viewModel.alarms.first { it.isNotEmpty() }
        assertEquals(1, alarms.size)
        assertEquals(singleAlarmSchedule.id, alarms[0].id)
        assertEquals("通常アラーム", alarms[0].label)
        assertTrue(alarms[0].enabled)
    }

    @Test
    fun `toggleEnabledで有効無効が更新される`() = runTest {
        val id = repository.add(
            AlarmSchedule(
                id = 0L,
                startMinutes = 12 * 60 + 30,
                endMinutes = 12 * 60 + 30,
                startIntervalMinutes = 5,
                endIntervalMinutes = 5,
                repeatDays = emptySet(),
                label = "昼寝",
                enabled = true,
            ),
        )
        val schedule = repository.getById(id)!!

        viewModel.toggleEnabled(schedule, false)

        val updated = repository.observeAll().first { list -> list.any { it.id == id && !it.enabled } }
        assertFalse(updated.first { it.id == id }.enabled)
    }
}
