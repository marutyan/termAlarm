package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.StopMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val TOKYO = ZoneId.of("Asia/Tokyo")

/**
 * WakeRecordRepositoryの単体テスト。
 * 鳴動記録の保存・読み直し時の値の保持と、期間指定読み出しによる範囲フィルタリングを検証する。
 */
class WakeRecordRepositoryTest {

    /**
     * 鳴動記録を保存して読み直したときに、各フィールド（予定時刻、停止時刻、停止方法、回数、セッション開始日）
     * の値が欠落や変質なく正しく保たれることを検証する。
     */
    @Test
    fun `記録を保存して読み直すと、値が保たれること`() = runTest {
        val dao = FakeRingRecordDao()
        val repository = WakeRecordRepository(dao)

        val sessionDate = LocalDate.of(2026, 9, 13)
        val scheduledZdt = ZonedDateTime.of(sessionDate, java.time.LocalTime.of(7, 0), TOKYO)
        val stoppedZdt = ZonedDateTime.of(sessionDate, java.time.LocalTime.of(7, 1), TOKYO)

        val scheduledEpochMilli = scheduledZdt.toInstant().toEpochMilli()
        val stoppedEpochMilli = stoppedZdt.toInstant().toEpochMilli()

        // 1件保存
        val id = repository.record(
            alarmId = 42L,
            sessionStart = sessionDate.toEpochDay(),
            scheduledAt = scheduledEpochMilli,
            stoppedAt = stoppedEpochMilli,
            stopMethod = StopMethod.CHALLENGE.name,
            occurrenceIndex = 0,
        )
        assertEquals(1L, id)

        // 読み出して検証
        val sessions = repository.getAllSessions(TOKYO)
        assertEquals(1, sessions.size)

        val session = sessions[0]
        assertEquals(sessionDate, session.sessionStart)
        assertEquals(1, session.rings.size)

        val ring = session.rings[0]
        assertEquals(scheduledZdt, ring.scheduledAt)
        assertEquals(stoppedZdt, ring.stoppedAt)
        assertEquals(StopMethod.CHALLENGE, ring.stopMethod)
        assertEquals(0, ring.occurrenceIndex)

        // 自動消音（stoppedAt = null, AUTO_SILENCED）のケースも保存して値が保たれることを検証
        val autoSilencedZdt = scheduledZdt.plusMinutes(5)
        repository.record(
            alarmId = 42L,
            sessionStart = sessionDate.toEpochDay(),
            scheduledAt = autoSilencedZdt.toInstant().toEpochMilli(),
            stoppedAt = null,
            stopMethod = StopMethod.AUTO_SILENCED.name,
            occurrenceIndex = 1,
        )

        val updatedSessions = repository.getAllSessions(TOKYO)
        assertEquals(1, updatedSessions.size)
        assertEquals(2, updatedSessions[0].rings.size)

        val autoRing = updatedSessions[0].rings[1]
        assertEquals(autoSilencedZdt, autoRing.scheduledAt)
        assertNull(autoRing.stoppedAt)
        assertEquals(StopMethod.AUTO_SILENCED, autoRing.stopMethod)
        assertEquals(1, autoRing.occurrenceIndex)
    }

    /**
     * 期間（開始日〜終了日）を指定して読み出したときに、
     * 範囲外の日の記録は除外され、指定した範囲内のセッションのみが返ることを検証する。
     */
    @Test
    fun `期間を指定して読み出すと、その範囲の記録だけが返ること`() = runTest {
        val dao = FakeRingRecordDao()
        val repository = WakeRecordRepository(dao)

        val day1 = LocalDate.of(2026, 9, 7) // 範囲外（前）
        val day2 = LocalDate.of(2026, 9, 8) // 範囲内（初日）
        val day3 = LocalDate.of(2026, 9, 10) // 範囲内（中間）
        val day4 = LocalDate.of(2026, 9, 12) // 範囲内（最終日）
        val day5 = LocalDate.of(2026, 9, 13) // 範囲外（後）

        // day1〜day5の記録をそれぞれ1件ずつ保存
        val days = listOf(day1, day2, day3, day4, day5)
        for ((index, d) in days.withIndex()) {
            val zdt = ZonedDateTime.of(d, java.time.LocalTime.of(7, 0), TOKYO)
            repository.record(
                alarmId = 1L,
                sessionStart = d.toEpochDay(),
                scheduledAt = zdt.toInstant().toEpochMilli(),
                stoppedAt = zdt.plusMinutes(2).toInstant().toEpochMilli(),
                stopMethod = StopMethod.TAP.name,
                occurrenceIndex = 0,
            )
        }

        // 期間を day2(2026-09-08) から day4(2026-09-12) に指定して取得
        val filtered = repository.getSessionsBetween(day2, day4, TOKYO)

        // 範囲内の3件(day2, day3, day4)のみが返ることを検証
        assertEquals(3, filtered.size)
        assertEquals(day2, filtered[0].sessionStart)
        assertEquals(day3, filtered[1].sessionStart)
        assertEquals(day4, filtered[2].sessionStart)

        // Flow版の observeSessionsBetween でも同様に範囲内のみが得られることを検証
        val flowFiltered = repository.observeSessionsBetween(day2, day4, TOKYO).first()
        assertEquals(3, flowFiltered.size)
        assertEquals(day2, flowFiltered[0].sessionStart)
        assertEquals(day3, flowFiltered[1].sessionStart)
        assertEquals(day4, flowFiltered[2].sessionStart)
    }
}
