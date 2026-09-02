package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.AlarmSchedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val TOKYO = ZoneId.of("Asia/Tokyo")

// テスト用のAlarmScheduleを組み立てるヘルパー。id=0は新規追加時にRoomのautoGenerateへ渡す値と同じ意味
private fun schedule(
    startMinutes: Int,
    endMinutes: Int,
    skippedSessionStart: LocalDate? = null,
) = AlarmSchedule(
    id = 0L,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    intervalMinutes = 5,
    repeatDays = emptySet(),
    label = "test",
    soundUri = null,
    vibrate = true,
    enabled = true,
    skippedSessionStart = skippedSessionStart,
)

class AlarmRepositoryTest {

    @Test
    fun `新規追加した3項目の既定値が保存と読み出しで保たれる`() = runTest {
        val repository = AlarmRepository(FakeAlarmDao())
        val id = repository.add(schedule(startMinutes = 7 * 60, endMinutes = 9 * 60))

        val loaded = repository.getById(id)
        assertEquals(true, loaded?.skipRequiresApp)
        assertEquals(false, loaded?.skipGame)
        assertNull(loaded?.snoozeMinutes)
    }

    @Test
    fun `当日終了は現在時刻が属するセッション開始日をskippedSessionStartへ書き込む`() = runTest {
        val repository = AlarmRepository(FakeAlarmDao())
        val id = repository.add(schedule(startMinutes = 7 * 60, endMinutes = 9 * 60))

        val today = LocalDate.of(2024, 1, 3)
        val now = ZonedDateTime.of(today, LocalTime.of(8, 0), TOKYO)
        repository.endTodaySession(id, now)

        assertEquals(today, repository.getById(id)?.skippedSessionStart)
    }

    @Test
    fun `日をまたぐセッションでは当日終了が前日をセッション開始日として書き込む`() = runTest {
        // 23:00-01:00のセッション中、日付が変わった直後(0:15)に「今日はもう止める」を押した場合、
        // 属するセッションは前日23:00に始まったものなのでskippedSessionStartは前日になる
        val repository = AlarmRepository(FakeAlarmDao())
        val id = repository.add(schedule(startMinutes = 23 * 60, endMinutes = 60))

        val monday = LocalDate.of(2024, 1, 1)
        val tuesday = monday.plusDays(1)
        val now = ZonedDateTime.of(tuesday, LocalTime.of(0, 15), TOKYO)
        repository.endTodaySession(id, now)

        assertEquals(monday, repository.getById(id)?.skippedSessionStart)
    }

    @Test
    fun `存在しないidへの当日終了は何も起きない`() = runTest {
        val repository = AlarmRepository(FakeAlarmDao())
        repository.endTodaySession(999L, ZonedDateTime.now(TOKYO))
        assertNull(repository.getById(999L))
    }
}
