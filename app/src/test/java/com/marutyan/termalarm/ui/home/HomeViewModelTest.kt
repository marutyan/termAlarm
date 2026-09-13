package com.marutyan.termalarm.ui.home

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
 * HomeViewModelの単体テスト。
 * ターム一覧の購読と、有効/無効の切り替え操作を検証する。
 */
class HomeViewModelTest {

    private lateinit var dao: FakeAlarmDao
    private lateinit var repository: AlarmRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        dao = FakeAlarmDao()
        repository = AlarmRepository(dao)
        viewModel = HomeViewModel(repository)
    }

    @Test
    fun `初期状態ではターム一覧が正しく取得できる`() = runTest {
        val schedule = AlarmSchedule(
            id = 1L,
            startMinutes = 7 * 60,
            endMinutes = 9 * 60,
            startIntervalMinutes = 5,
            endIntervalMinutes = 5,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            label = "朝のターム",
            enabled = true,
        )
        repository.add(schedule)

        val terms = viewModel.terms.first { it.isNotEmpty() }
        assertEquals(1, terms.size)
        assertEquals(schedule.label, terms[0].label)
        assertTrue(terms[0].enabled)
    }

    @Test
    fun `toggleEnabledで有効無効が更新される`() = runTest {
        val id = repository.add(
            AlarmSchedule(
                id = 0L,
                startMinutes = 7 * 60,
                endMinutes = 9 * 60,
                startIntervalMinutes = 5,
                endIntervalMinutes = 5,
                repeatDays = setOf(DayOfWeek.MONDAY),
                label = "",
                enabled = true,
            ),
        )
        val schedule = repository.getById(id)!!

        viewModel.toggleEnabled(schedule, false)

        val updated = repository.observeAll().first { list -> list.any { it.id == id && !it.enabled } }
        assertFalse(updated.first { it.id == id }.enabled)
    }

    @Test
    fun `通常アラームはターム一覧から除外される`() = runTest {
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

        val terms = viewModel.terms.first { it.isNotEmpty() }
        assertEquals(1, terms.size)
        assertEquals(termSchedule.id, terms[0].id)
    }
}
