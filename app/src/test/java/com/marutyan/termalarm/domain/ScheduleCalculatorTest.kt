package com.marutyan.termalarm.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// テストで共通に使うタイムゾーン。DSTの影響を受けないためnextTrigger等の基本ケースに使う
private val TOKYO = ZoneId.of("Asia/Tokyo")

// 通常のAlarmScheduleを組み立てるテスト用ヘルパー。指定しなかった項目はテストに影響しない既定値にする
private fun schedule(
    startMinutes: Int,
    endMinutes: Int,
    intervalMinutes: Int,
    repeatDays: Set<DayOfWeek> = emptySet(),
    enabled: Boolean = true,
    skippedSessionStart: LocalDate? = null,
) = AlarmSchedule(
    id = 1L,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    intervalMinutes = intervalMinutes,
    repeatDays = repeatDays,
    label = "test",
    soundUri = null,
    vibrate = true,
    enabled = enabled,
    skippedSessionStart = skippedSessionStart,
)

class ScheduleCalculatorTest {

    // --- 鳴動回数（両端を含む） ---

    @Test
    fun `7時から9時を5分間隔で25回になる`() {
        assertEquals(25, occurrenceCount(schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, intervalMinutes = 5)))
    }

    @Test
    fun `startとendが同じなら1回だけになる`() {
        assertEquals(1, occurrenceCount(schedule(startMinutes = 7 * 60, endMinutes = 7 * 60, intervalMinutes = 5)))
    }

    @Test
    fun `spanが割り切れない場合は最後の鳴動がendMinutesより前になる`() {
        // 7:00-9:00(span=120分)を7分間隔にすると 120/7+1=18回。最後は 7:00+17*7分=8:59 で、
        // 9:00そのものは鳴らない（次の8:59+7分=9:06は範囲外）。
        // ※ docs/SPEC.md本文の記載例は「最後は8:57」だが、SPECが定義する計算式
        //   (span/intervalMinutes+1、occurrenceはstart+k*interval)通りに計算すると8:59になり、
        //   本文中の具体例の数値そのものがこの式と矛盾している（8:57は7分刻みの倍数ではない）。
        //   計算式を共通契約として優先し、本文の例の数値は誤りとみなして実装・テストした。
        val s = schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, intervalMinutes = 7)
        assertEquals(18, occurrenceCount(s))

        val today = LocalDate.of(2024, 1, 3) // 水曜日。repeatDays空なので曜日は無関係
        val justBeforeLast = ZonedDateTime.of(today, java.time.LocalTime.of(8, 58), TOKYO)
        assertEquals(ZonedDateTime.of(today, java.time.LocalTime.of(8, 59), TOKYO), nextTrigger(s, justBeforeLast))

        val atLast = ZonedDateTime.of(today, java.time.LocalTime.of(8, 59), TOKYO)
        assertNull("最後の鳴動(8:59)を過ぎたら単発扱いでnextTriggerはnull", nextTrigger(s, atLast))
    }

    // --- 曜日判定・日またぎ ---

    @Test
    fun `23時から1時の日またぎで開始日の曜日が使われる`() {
        // 2024-01-01は月曜日
        val monday = LocalDate.of(2024, 1, 1)
        val s = schedule(startMinutes = 23 * 60, endMinutes = 60, intervalMinutes = 30, repeatDays = setOf(DayOfWeek.MONDAY))

        // 月曜22:00 → 月曜23:00に鳴る
        val beforeStart = ZonedDateTime.of(monday, java.time.LocalTime.of(22, 0), TOKYO)
        assertEquals(ZonedDateTime.of(monday, java.time.LocalTime.of(23, 0), TOKYO), nextTrigger(s, beforeStart))

        // 火曜00:15（月曜開始セッションの続き）→ 火曜00:30に鳴る
        val tuesday = monday.plusDays(1)
        val afterMidnight = ZonedDateTime.of(tuesday, java.time.LocalTime.of(0, 15), TOKYO)
        assertEquals(ZonedDateTime.of(tuesday, java.time.LocalTime.of(0, 30), TOKYO), nextTrigger(s, afterMidnight))
    }

    @Test
    fun `日またぎセッションの曜日判定は開始日基準で終了日の曜日は使われない`() {
        // 火曜日だけ有効な設定。火曜0時台は「月曜開始セッション」の続きなので鳴らず、
        // 火曜23時から始まる次のセッションまで待つ。
        val monday = LocalDate.of(2024, 1, 1)
        val tuesday = monday.plusDays(1)
        val s = schedule(startMinutes = 23 * 60, endMinutes = 60, intervalMinutes = 30, repeatDays = setOf(DayOfWeek.TUESDAY))

        val tuesdayEarlyMorning = ZonedDateTime.of(tuesday, java.time.LocalTime.of(0, 15), TOKYO)
        assertEquals(ZonedDateTime.of(tuesday, java.time.LocalTime.of(23, 0), TOKYO), nextTrigger(s, tuesdayEarlyMorning))
    }

    // --- repeatDaysが空（次の1回だけ） ---

    @Test
    fun `repeatDaysが空なら次の1回だけを返しその後はnullになる`() {
        val today = LocalDate.of(2024, 1, 3)
        val s = schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, intervalMinutes = 5)

        val beforeStart = ZonedDateTime.of(today, java.time.LocalTime.of(6, 0), TOKYO)
        assertEquals(ZonedDateTime.of(today, java.time.LocalTime.of(7, 0), TOKYO), nextTrigger(s, beforeStart))

        // 最後の鳴動(9:00)を過ぎたら、翌日以降を探さずnull（自動的に無効化される想定）
        val afterLast = ZonedDateTime.of(today, java.time.LocalTime.of(9, 1), TOKYO)
        assertNull(nextTrigger(s, afterLast))
    }

    @Test
    fun `enabledがfalseならnull`() {
        val s = schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, intervalMinutes = 5, enabled = false)
        val now = ZonedDateTime.of(LocalDate.of(2024, 1, 3), java.time.LocalTime.of(6, 0), TOKYO)
        assertNull(nextTrigger(s, now))
    }

    // --- skippedSessionStart ---

    @Test
    fun `skippedSessionStartと一致するセッションは飛ばして次の該当曜日へ進む`() {
        val monday = LocalDate.of(2024, 1, 1) // このMondayを「今日はもう止める」でスキップ済みとする
        val nextMonday = monday.plusWeeks(1)
        val s = schedule(
            startMinutes = 7 * 60,
            endMinutes = 9 * 60,
            intervalMinutes = 5,
            repeatDays = setOf(DayOfWeek.MONDAY),
            skippedSessionStart = monday,
        )

        val mondayMorning = ZonedDateTime.of(monday, java.time.LocalTime.of(6, 0), TOKYO)
        assertEquals(ZonedDateTime.of(nextMonday, java.time.LocalTime.of(7, 0), TOKYO), nextTrigger(s, mondayMorning))
    }

    // --- 残り鳴動回数（現在鳴っている回を含めない） ---

    @Test
    fun `7時05分が鳴っているとき残りは23回で次は7時10分`() {
        // docs/SPEC.md「追記: 残り鳴動回数の数え方」の例そのもの
        val today = LocalDate.of(2024, 1, 3)
        val s = schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, intervalMinutes = 5)
        val ringingAt = ZonedDateTime.of(today, java.time.LocalTime.of(7, 5), TOKYO)

        assertEquals(23, remainingOccurrenceCount(s, ringingAt))
        assertEquals(ZonedDateTime.of(today, java.time.LocalTime.of(7, 10), TOKYO), nextTrigger(s, ringingAt))
    }

    @Test
    fun `日またぎセッションでも残り回数はセッション開始日基準で数える`() {
        // 23:00-01:00・30分間隔（全5回: 23:00,23:30,00:00,00:30,01:00）
        val monday = LocalDate.of(2024, 1, 1)
        val tuesday = monday.plusDays(1)
        val s = schedule(startMinutes = 23 * 60, endMinutes = 60, intervalMinutes = 30)

        val at2330 = ZonedDateTime.of(monday, java.time.LocalTime.of(23, 30), TOKYO)
        assertEquals(3, remainingOccurrenceCount(s, at2330)) // 00:00,00:30,01:00

        val at0000 = ZonedDateTime.of(tuesday, java.time.LocalTime.of(0, 0), TOKYO)
        assertEquals(2, remainingOccurrenceCount(s, at0000)) // 00:30,01:00
    }

    // --- 一覧表示用の要約 ---

    @Test
    fun `要約文字列を組み立てる`() {
        assertEquals("5分ごと · 25回", scheduleSummary(schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, intervalMinutes = 5)))
        assertEquals("1回のみ", scheduleSummary(schedule(startMinutes = 7 * 60, endMinutes = 7 * 60, intervalMinutes = 5)))
    }

    // --- タイムゾーン・DST ---

    @Test
    fun `サマータイム開始のギャップは存在しない時刻をギャップ分繰り上げる`() {
        // America/New_Yorkの2024-03-10は02:00→03:00にジャンプし、02:00-02:59は存在しない。
        // 01:30-02:30を30分間隔にすると offsetは01:30,02:00,02:30で、02:00は存在しないため
        // ZonedDateTime.atZoneの既定解決によりギャップ分(1時間)繰り上がって03:00になる。
        val newYork = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2024, 3, 10)
        val s = schedule(startMinutes = 90, endMinutes = 150, intervalMinutes = 30)

        val justAfterFirst = ZonedDateTime.of(dstDay, java.time.LocalTime.of(1, 31), newYork)
        val next = nextTrigger(s, justAfterFirst)

        assertEquals(3, next?.hour)
        assertEquals(0, next?.minute)
    }

    @Test
    fun `サマータイム終了の重複時刻は早い方のオフセットを採用する`() {
        // America/New_Yorkの2024-11-03は02:00→01:00に戻り、01:00-01:59が2回ある。
        // 01:30ちょうどに鳴る設定なら、繰り下げ前(EDT, UTC-4)の早い方が採用されるはず。
        val newYork = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2024, 11, 3)
        val s = schedule(startMinutes = 90, endMinutes = 90, intervalMinutes = 1) // 01:30ちょうど1回だけ

        val justBefore = ZonedDateTime.of(dstDay, java.time.LocalTime.of(1, 0), newYork) // まだEDT(-04:00)側
        val next = nextTrigger(s, justBefore)

        assertEquals(1, next?.hour)
        assertEquals(30, next?.minute)
        assertEquals(java.time.ZoneOffset.ofHours(-4), next?.offset)
    }
}
