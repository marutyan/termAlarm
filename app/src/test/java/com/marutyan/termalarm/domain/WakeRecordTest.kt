package com.marutyan.termalarm.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val TOKYO = ZoneId.of("Asia/Tokyo")

class WakeRecordTest {

    @Test
    fun `全部AUTO_SILENCEDのセッションで起床とみなした回がnullになる`() {
        val today = LocalDate.of(2026, 9, 13)
        val rangeStart = ZonedDateTime.of(today, java.time.LocalTime.of(7, 0), TOKYO)
        val session = SessionRecord(
            sessionStart = today,
            rangeStartAt = rangeStart,
            rings = listOf(
                RingRecord(
                    scheduledAt = rangeStart,
                    dismissedAt = null,
                    dismissMethod = DismissMethod.AUTO_SILENCED,
                    occurrenceIndex = 0,
                ),
                RingRecord(
                    scheduledAt = rangeStart.plusMinutes(5),
                    dismissedAt = null,
                    dismissMethod = DismissMethod.AUTO_SILENCED,
                    occurrenceIndex = 1,
                ),
            ),
        )

        assertNull(wakeOccurrence(session))
        assertNull(wakeDurationMinutes(session))
    }

    @Test
    fun `途中で止めた後に放置した回が続く場合最後に止めた回が起床とみなした回になる`() {
        val today = LocalDate.of(2026, 9, 13)
        val rangeStart = ZonedDateTime.of(today, java.time.LocalTime.of(7, 0), TOKYO)
        val session = SessionRecord(
            sessionStart = today,
            rangeStartAt = rangeStart,
            rings = listOf(
                RingRecord(
                    scheduledAt = rangeStart,
                    dismissedAt = rangeStart.plusMinutes(1),
                    dismissMethod = DismissMethod.CHALLENGE,
                    occurrenceIndex = 0,
                ),
                RingRecord(
                    scheduledAt = rangeStart.plusMinutes(5),
                    dismissedAt = rangeStart.plusMinutes(6),
                    dismissMethod = DismissMethod.LONG_PRESS,
                    occurrenceIndex = 1,
                ),
                RingRecord(
                    scheduledAt = rangeStart.plusMinutes(10),
                    dismissedAt = null,
                    dismissMethod = DismissMethod.AUTO_SILENCED,
                    occurrenceIndex = 2,
                ),
                RingRecord(
                    scheduledAt = rangeStart.plusMinutes(15),
                    dismissedAt = null,
                    dismissMethod = DismissMethod.AUTO_SILENCED,
                    occurrenceIndex = 3,
                ),
            ),
        )

        // 最後に止めたのは occurrenceIndex = 1 の回。1始まりで数えるため 1 + 1 = 2回目となる
        assertEquals(2, wakeOccurrence(session))
        // 7:00から7:06までの所要時間は6分
        assertEquals(6L, wakeDurationMinutes(session))
    }

    @Test
    fun `長押し緊急停止の割合が正しく求まり鳴動が0件なら0_0になる`() {
        // 鳴動が0件のケース
        assertEquals(0.0, longPressDismissRatio(emptyList()), 0.0001)

        val today = LocalDate.of(2026, 9, 13)
        val rangeStart = ZonedDateTime.of(today, java.time.LocalTime.of(7, 0), TOKYO)
        val emptySession = SessionRecord(
            sessionStart = today,
            rangeStartAt = rangeStart,
            rings = emptyList(),
        )
        assertEquals(0.0, longPressDismissRatio(listOf(emptySession)), 0.0001)

        // 複数セッションにわたる計算のケース
        val session1 = SessionRecord(
            sessionStart = today,
            rangeStartAt = rangeStart,
            rings = listOf(
                RingRecord(rangeStart, rangeStart.plusMinutes(1), DismissMethod.CHALLENGE, 0),
                RingRecord(rangeStart.plusMinutes(5), rangeStart.plusMinutes(6), DismissMethod.LONG_PRESS, 1),
                RingRecord(rangeStart.plusMinutes(10), null, DismissMethod.AUTO_SILENCED, 2),
            ),
        )
        val session2 = SessionRecord(
            sessionStart = today.plusDays(1),
            rangeStartAt = rangeStart.plusDays(1),
            rings = listOf(
                RingRecord(rangeStart.plusDays(1), rangeStart.plusDays(1).plusMinutes(1), DismissMethod.LONG_PRESS, 0),
                RingRecord(rangeStart.plusDays(1).plusMinutes(5), rangeStart.plusDays(1).plusMinutes(6), DismissMethod.CHALLENGE, 1),
            ),
        )

        // 全5回の鳴動のうち LONG_PRESS は 2回。手計算: 2 / 5 = 0.4
        assertEquals(0.4, longPressDismissRatio(listOf(session1, session2)), 0.0001)
        assertEquals(0.4, longPressRate(listOf(session1, session2)), 0.0001)
    }

    @Test
    fun `平均から起床とみなした回が無いセッションが除かれる`() {
        val today = LocalDate.of(2026, 9, 13)
        val rangeStart = ZonedDateTime.of(today, java.time.LocalTime.of(7, 0), TOKYO)

        // セッション1: 2回目で起床、所要10分
        val session1 = SessionRecord(
            sessionStart = today,
            rangeStartAt = rangeStart,
            rings = listOf(
                RingRecord(rangeStart, rangeStart.plusMinutes(1), DismissMethod.CHALLENGE, 0),
                RingRecord(rangeStart.plusMinutes(5), rangeStart.plusMinutes(10), DismissMethod.CHALLENGE, 1),
            ),
        )

        // セッション2: 4回目で起床、所要20分
        val session2 = SessionRecord(
            sessionStart = today.plusDays(1),
            rangeStartAt = rangeStart.plusDays(1),
            rings = listOf(
                RingRecord(rangeStart.plusDays(1), rangeStart.plusDays(1).plusMinutes(1), DismissMethod.CHALLENGE, 0),
                RingRecord(rangeStart.plusDays(1).plusMinutes(5), rangeStart.plusDays(1).plusMinutes(6), DismissMethod.CHALLENGE, 1),
                RingRecord(rangeStart.plusDays(1).plusMinutes(10), rangeStart.plusDays(1).plusMinutes(11), DismissMethod.CHALLENGE, 2),
                RingRecord(rangeStart.plusDays(1).plusMinutes(15), rangeStart.plusDays(1).plusMinutes(20), DismissMethod.LONG_PRESS, 3),
            ),
        )

        // セッション3: 全部 AUTO_SILENCED（起床とみなした回なし -> 平均から除外されるべき）
        val session3 = SessionRecord(
            sessionStart = today.plusDays(2),
            rangeStartAt = rangeStart.plusDays(2),
            rings = listOf(
                RingRecord(rangeStart.plusDays(2), null, DismissMethod.AUTO_SILENCED, 0),
                RingRecord(rangeStart.plusDays(2).plusMinutes(5), null, DismissMethod.AUTO_SILENCED, 1),
            ),
        )

        // セッション4: 鳴動記録0件（除外されるべき）
        val session4 = SessionRecord(
            sessionStart = today.plusDays(3),
            rangeStartAt = rangeStart.plusDays(3),
            rings = emptyList(),
        )

        val sessions = listOf(session1, session2, session3, session4)

        // 手計算:
        // 有効セッション: session1 (回数=2, 分数=10), session2 (回数=4, 分数=20)
        // 平均回数: (2 + 4) / 2 = 3.0
        // 平均分数: (10 + 20) / 2 = 15.0
        val averages = averageWakeMetrics(sessions)
        assertEquals(3.0, averages?.averageOccurrence ?: 0.0, 0.0001)
        assertEquals(15.0, averages?.averageDurationMinutes ?: 0.0, 0.0001)

        assertEquals(3.0, averageWakeOccurrence(sessions) ?: 0.0, 0.0001)
        assertEquals(15.0, averageWakeDurationMinutes(sessions) ?: 0.0, 0.0001)

        // 起床実績が1件も無い場合は null を返す
        assertNull(averageWakeMetrics(listOf(session3, session4)))
        assertNull(averageWakeOccurrence(listOf(session3, session4)))
        assertNull(averageWakeDurationMinutes(listOf(session3, session4)))
    }
}
