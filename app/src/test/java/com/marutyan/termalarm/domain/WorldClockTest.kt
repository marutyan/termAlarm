package com.marutyan.termalarm.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 端末側のタイムゾーンはテスト全体でUTC固定にし、都市側のオフセットだけを変えて検証する
private val DEVICE = ZoneId.of("UTC")

class WorldClockTimeTest {

    @Test
    fun `東京はUTCより9時間進んでいる`() {
        val instant = Instant.parse("2026-01-01T03:00:00Z")
        val diff = timeDifference(ZoneId.of("Asia/Tokyo"), DEVICE, instant)
        assertTrue(diff.isAhead)
        assertEquals(9, diff.hourPart)
        assertEquals(0, diff.minutePart)
        assertEquals(0, diff.dayOffset)
    }

    @Test
    fun `ロサンゼルスはUTCより8時間遅れている`() {
        val instant = Instant.parse("2026-01-01T12:00:00Z")
        val diff = timeDifference(ZoneId.of("America/Los_Angeles"), DEVICE, instant)
        assertFalse(diff.isAhead)
        assertEquals(8, diff.hourPart)
        assertEquals(0, diff.minutePart)
        assertEquals(0, diff.dayOffset)
    }

    @Test
    fun `インドは30分単位の時差になる`() {
        val instant = Instant.parse("2026-01-01T03:00:00Z")
        val diff = timeDifference(ZoneId.of("Asia/Kolkata"), DEVICE, instant)
        assertTrue(diff.isAhead)
        assertEquals(5, diff.hourPart)
        assertEquals(30, diff.minutePart)
    }

    @Test
    fun `ネパールは45分単位の時差になる`() {
        val instant = Instant.parse("2026-01-01T03:00:00Z")
        val diff = timeDifference(ZoneId.of("Asia/Kathmandu"), DEVICE, instant)
        assertTrue(diff.isAhead)
        assertEquals(5, diff.hourPart)
        assertEquals(45, diff.minutePart)
    }

    @Test
    fun `進んでいる都市が端末より先に日付を跨ぐと翌日になる`() {
        // UTCで1月1日23時。東京(+9時間)は既に1月2日8時になっている
        val instant = Instant.parse("2026-01-01T23:00:00Z")
        val diff = timeDifference(ZoneId.of("Asia/Tokyo"), DEVICE, instant)
        assertEquals(1, diff.dayOffset)
    }

    @Test
    fun `遅れている都市が端末よりまだ前日だと前日になる`() {
        // UTCで1月1日1時。ロサンゼルス(-8時間)はまだ12月31日17時
        val instant = Instant.parse("2026-01-01T01:00:00Z")
        val diff = timeDifference(ZoneId.of("America/Los_Angeles"), DEVICE, instant)
        assertEquals(-1, diff.dayOffset)
    }

    @Test
    fun `日付変更線をまたぐと2日分ずれることがある`() {
        // 端末はEtc/GMT+12(UTC-12)で現地1月1日23時。キリバス(UTC+14、26時間先)は既に1月3日1時
        val instant = Instant.parse("2026-01-02T11:00:00Z")
        val diff = timeDifference(ZoneId.of("Pacific/Kiritimati"), ZoneId.of("Etc/GMT+12"), instant)
        assertTrue(diff.isAhead)
        assertEquals(26, diff.hourPart)
        assertEquals(0, diff.minutePart)
        assertEquals(2, diff.dayOffset)
    }

    @Test
    fun `時差が無ければ0時間0分で当日になる`() {
        val instant = Instant.parse("2026-01-01T03:00:00Z")
        val diff = timeDifference(ZoneId.of("Europe/London"), ZoneId.of("UTC"), instant)
        assertEquals(0, diff.hourPart)
        assertEquals(0, diff.minutePart)
        assertEquals(0, diff.dayOffset)
    }
}

class WorldClockCityListTest {

    private fun city(id: Long, order: Int) = WorldClockCity(id = id, zoneId = "Zone$id", sortOrder = order)

    @Test
    fun `削除すると残りのsortOrderが詰め直される`() {
        val cities = listOf(city(1, 0), city(2, 1), city(3, 2))
        val result = cities.withoutCity(id = 2)
        assertEquals(listOf(1L, 3L), result.map { it.id })
        assertEquals(listOf(0, 1), result.map { it.sortOrder })
    }

    @Test
    fun `存在しないidを削除しても一覧は変わらない`() {
        val cities = listOf(city(1, 0), city(2, 1))
        assertEquals(cities, cities.withoutCity(id = 999))
    }

    @Test
    fun `先頭を末尾へ動かすと並びが入れ替わる`() {
        val cities = listOf(city(1, 0), city(2, 1), city(3, 2))
        val result = cities.movedCity(fromIndex = 0, toIndex = 2)
        assertEquals(listOf(2L, 3L, 1L), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.sortOrder })
    }

    @Test
    fun `範囲外の移動指定は何もしない`() {
        val cities = listOf(city(1, 0), city(2, 1))
        assertEquals(cities, cities.movedCity(fromIndex = 0, toIndex = 5))
    }

    @Test
    fun `同じ位置への移動は何もしない`() {
        val cities = listOf(city(1, 0), city(2, 1))
        assertEquals(cities, cities.movedCity(fromIndex = 1, toIndex = 1))
    }
}
