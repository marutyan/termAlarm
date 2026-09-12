package com.marutyan.termalarm.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

// テストで共通に使うタイムゾーン。DSTの影響を受けないためnextTrigger等の基本ケースに使う
private val TOKYO = ZoneId.of("Asia/Tokyo")

// 通常のAlarmScheduleを組み立てるテスト用ヘルパー。指定しなかった項目はテストに影響しない既定値にする
private fun schedule(
    startMinutes: Int,
    endMinutes: Int,
    startIntervalMinutes: Int,
    endIntervalMinutes: Int = startIntervalMinutes,
    repeatDays: Set<DayOfWeek> = emptySet(),
    enabled: Boolean = true,
    skippedSessionStart: LocalDate? = null,
) = AlarmSchedule(
    id = 1L,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    startIntervalMinutes = startIntervalMinutes,
    endIntervalMinutes = endIntervalMinutes,
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
        assertEquals(25, occurrenceCount(schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, startIntervalMinutes = 5)))
    }

    @Test
    fun `startとendが同じなら1回だけになる`() {
        assertEquals(1, occurrenceCount(schedule(startMinutes = 7 * 60, endMinutes = 7 * 60, startIntervalMinutes = 5)))
    }

    @Test
    fun `spanが割り切れない場合は最後の鳴動がendMinutesより前になる`() {
        // 7:00-9:00(span=120分)を7分間隔にすると 120/7+1=18回。最後は 7:00+17*7分=8:59 で、
        // 9:00そのものは鳴らない（次の8:59+7分=9:06は範囲外）。
        // ※ docs/SPEC.md本文の記載例は「最後は8:57」だが、SPECが定義する計算式
        //   (span/intervalMinutes+1、occurrenceはstart+k*interval)通りに計算すると8:59になり、
        //   本文中の具体例の数値そのものがこの式と矛盾している（8:57は7分刻みの倍数ではない）。
        //   計算式を共通契約として優先し、本文の例の数値は誤りとみなして実装・テストした。
        val s = schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, startIntervalMinutes = 7)
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
        val s = schedule(startMinutes = 23 * 60, endMinutes = 60, startIntervalMinutes = 30, repeatDays = setOf(DayOfWeek.MONDAY))

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
        val s = schedule(startMinutes = 23 * 60, endMinutes = 60, startIntervalMinutes = 30, repeatDays = setOf(DayOfWeek.TUESDAY))

        val tuesdayEarlyMorning = ZonedDateTime.of(tuesday, java.time.LocalTime.of(0, 15), TOKYO)
        assertEquals(ZonedDateTime.of(tuesday, java.time.LocalTime.of(23, 0), TOKYO), nextTrigger(s, tuesdayEarlyMorning))
    }

    // --- repeatDaysが空（次の1回だけ） ---

    @Test
    fun `repeatDaysが空なら次の1回だけを返しその後はnullになる`() {
        val today = LocalDate.of(2024, 1, 3)
        val s = schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, startIntervalMinutes = 5)

        val beforeStart = ZonedDateTime.of(today, java.time.LocalTime.of(6, 0), TOKYO)
        assertEquals(ZonedDateTime.of(today, java.time.LocalTime.of(7, 0), TOKYO), nextTrigger(s, beforeStart))

        // 最後の鳴動(9:00)を過ぎたら、翌日以降を探さずnull（自動的に無効化される想定）
        val afterLast = ZonedDateTime.of(today, java.time.LocalTime.of(9, 1), TOKYO)
        assertNull(nextTrigger(s, afterLast))
    }

    @Test
    fun `enabledがfalseならnull`() {
        val s = schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, startIntervalMinutes = 5, enabled = false)
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
            startIntervalMinutes = 5,
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
        val s = schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, startIntervalMinutes = 5)
        val ringingAt = ZonedDateTime.of(today, java.time.LocalTime.of(7, 5), TOKYO)

        assertEquals(23, remainingOccurrenceCount(s, ringingAt))
        assertEquals(ZonedDateTime.of(today, java.time.LocalTime.of(7, 10), TOKYO), nextTrigger(s, ringingAt))
    }

    @Test
    fun `日またぎセッションでも残り回数はセッション開始日基準で数える`() {
        // 23:00-01:00・30分間隔（全5回: 23:00,23:30,00:00,00:30,01:00）
        val monday = LocalDate.of(2024, 1, 1)
        val tuesday = monday.plusDays(1)
        val s = schedule(startMinutes = 23 * 60, endMinutes = 60, startIntervalMinutes = 30)

        val at2330 = ZonedDateTime.of(monday, java.time.LocalTime.of(23, 30), TOKYO)
        assertEquals(3, remainingOccurrenceCount(s, at2330)) // 00:00,00:30,01:00

        val at0000 = ZonedDateTime.of(tuesday, java.time.LocalTime.of(0, 0), TOKYO)
        assertEquals(2, remainingOccurrenceCount(s, at0000)) // 00:30,01:00
    }

    // --- 一覧表示用の要約 ---

    @Test
    fun `要約文字列を組み立てる`() {
        assertEquals("5分ごと · 25回", scheduleSummary(schedule(startMinutes = 7 * 60, endMinutes = 9 * 60, startIntervalMinutes = 5)))
        assertEquals("1回のみ", scheduleSummary(schedule(startMinutes = 7 * 60, endMinutes = 7 * 60, startIntervalMinutes = 5)))
    }

    // --- タイムゾーン・DST ---

    @Test
    fun `サマータイム開始のギャップは存在しない時刻をギャップ分繰り上げる`() {
        // America/New_Yorkの2024-03-10は02:00→03:00にジャンプし、02:00-02:59は存在しない。
        // 01:30-02:30を30分間隔にすると offsetは01:30,02:00,02:30で、02:00は存在しないため
        // ZonedDateTime.atZoneの既定解決によりギャップ分(1時間)繰り上がって03:00になる。
        val newYork = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2024, 3, 10)
        val s = schedule(startMinutes = 90, endMinutes = 150, startIntervalMinutes = 30)

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
        val s = schedule(startMinutes = 90, endMinutes = 90, startIntervalMinutes = 1) // 01:30ちょうど1回だけ

        val justBefore = ZonedDateTime.of(dstDay, java.time.LocalTime.of(1, 0), newYork) // まだEDT(-04:00)側
        val next = nextTrigger(s, justBefore)

        assertEquals(1, next?.hour)
        assertEquals(30, next?.minute)
        assertEquals(java.time.ZoneOffset.ofHours(-4), next?.offset)
    }

    // 一覧の「今日はもう止める」の導線は、押して意味があるときだけ出す。
    // 常時出すと、アラーム自体を無効にするトグルとの違いが伝わらなくなる。
    @Test
    fun `当日終了はこれから鳴る回が残っているときだけ実行できる`() {
        val today = LocalDate.of(2024, 1, 3)
        val s = schedule(7 * 60, 9 * 60, 5)

        // タームの途中。残りがあるので終了できる
        assertTrue(canEndTodaySession(s, ZonedDateTime.of(today, java.time.LocalTime.of(7, 30), TOKYO)))
        // ちょうど開始時刻。ここから始まる
        assertTrue(canEndTodaySession(s, ZonedDateTime.of(today, java.time.LocalTime.of(7, 0), TOKYO)))
        // まだ始まっていない。終わらせるものが無いので出さない
        assertFalse(canEndTodaySession(s, ZonedDateTime.of(today, java.time.LocalTime.of(6, 0), TOKYO)))
        // 最後の回まで鳴り終えた後。今日の分はもう無い
        assertFalse(canEndTodaySession(s, ZonedDateTime.of(today, java.time.LocalTime.of(9, 30), TOKYO)))
    }

    @Test
    fun `無効なアラームと既にスキップ済みのセッションでは当日終了を実行できない`() {
        val today = LocalDate.of(2024, 1, 3)
        val disabled = schedule(7 * 60, 9 * 60, 5, enabled = false)
        assertFalse(canEndTodaySession(disabled, ZonedDateTime.of(today, java.time.LocalTime.of(7, 30), TOKYO)))

        val alreadySkipped = schedule(7 * 60, 9 * 60, 5, repeatDays = DayOfWeek.entries.toSet(), skippedSessionStart = today)
        assertFalse(canEndTodaySession(alreadySkipped, ZonedDateTime.of(today, java.time.LocalTime.of(7, 30), TOKYO)))
    }

    // --- 残り時間表示(remainingTimeUntilNextTrigger) ---
    // 毎週月曜7:00のみ鳴る単発アラームを固定し、「now」だけを動かして各粒度の境界を検証する。
    // 2024-01-08は月曜日で、隔週などではなく毎週鳴るため次回は必ずこの日時に一致する。

    private val weeklyMondayAlarm = schedule(7 * 60, 7 * 60, 5, repeatDays = setOf(DayOfWeek.MONDAY))
    private val mondayTrigger = ZonedDateTime.of(2024, 1, 8, 7, 0, 0, 0, TOKYO)

    @Test
    fun `残り59秒は1分未満、ちょうど60秒で1分表示に切り替わる`() {
        assertEquals(
            RemainingTime.LessThanOneMinute,
            remainingTimeUntilNextTrigger(weeklyMondayAlarm, mondayTrigger.minusSeconds(59)),
        )
        assertEquals(
            RemainingTime.Minutes(1),
            remainingTimeUntilNextTrigger(weeklyMondayAlarm, mondayTrigger.minusSeconds(60)),
        )
    }

    @Test
    fun `残り59分は分表示、ちょうど60分で時間表示に切り替わる`() {
        assertEquals(
            RemainingTime.Minutes(59),
            remainingTimeUntilNextTrigger(weeklyMondayAlarm, mondayTrigger.minusMinutes(59)),
        )
        assertEquals(
            RemainingTime.HoursAndMinutes(1, 0),
            remainingTimeUntilNextTrigger(weeklyMondayAlarm, mondayTrigger.minusMinutes(60)),
        )
    }

    @Test
    fun `8時間30分は時間と分の組で表す`() {
        assertEquals(
            RemainingTime.HoursAndMinutes(8, 30),
            remainingTimeUntilNextTrigger(weeklyMondayAlarm, mondayTrigger.minusMinutes(8 * 60 + 30)),
        )
    }

    @Test
    fun `残り23時間59分は時間表示、ちょうど24時間で日表示に切り替わる`() {
        assertEquals(
            RemainingTime.HoursAndMinutes(23, 59),
            remainingTimeUntilNextTrigger(weeklyMondayAlarm, mondayTrigger.minusMinutes(23 * 60 + 59)),
        )
        assertEquals(
            RemainingTime.Days(1),
            remainingTimeUntilNextTrigger(weeklyMondayAlarm, mondayTrigger.minusHours(24)),
        )
    }

    @Test
    fun `2日先は日数のみで表す`() {
        assertEquals(
            RemainingTime.Days(2),
            remainingTimeUntilNextTrigger(weeklyMondayAlarm, mondayTrigger.minusDays(2)),
        )
    }

    @Test
    fun `無効なアラームは残り時間を返さない`() {
        val disabled = weeklyMondayAlarm.copy(enabled = false)
        assertNull(remainingTimeUntilNextTrigger(disabled, mondayTrigger.minusMinutes(30)))
    }

    @Test
    fun `日をまたぐタームは、始まってから終わるまでの間だけ終了できる`() {
        val day = LocalDate.of(2024, 1, 3)
        // 22:00から翌2:00まで、10分ごと
        val s = schedule(22 * 60, 2 * 60, 10, repeatDays = DayOfWeek.entries.toSet())

        // 始まる前
        assertFalse(canEndTodaySession(s, ZonedDateTime.of(day, java.time.LocalTime.of(21, 0), TOKYO)))
        // 始まった直後
        assertTrue(canEndTodaySession(s, ZonedDateTime.of(day, java.time.LocalTime.of(22, 0), TOKYO)))
        // 日付が変わった後、まだターム中
        assertTrue(canEndTodaySession(s, ZonedDateTime.of(day.plusDays(1), java.time.LocalTime.of(1, 0), TOKYO)))
        // 終わった後
        assertFalse(canEndTodaySession(s, ZonedDateTime.of(day.plusDays(1), java.time.LocalTime.of(3, 0), TOKYO)))
    }

    // --- 可変間隔アラーム ---

    // 7:00〜8:00、開始10分・終了3分の可変間隔で、各鳴動時刻が仕様どおりの11回に収束するか検証する。
    // 仕様書の計算例に沿って手計算した確定期待値と一致するかを確かめ、進捗率に応じた間隔計算と端数処理の正確性を保証する。
    @Test
    fun `7時から8時で開始10分終了3分の可変間隔で鳴動時刻列が11回と一致する`() {
        val s = schedule(
            startMinutes = 7 * 60,
            endMinutes = 8 * 60,
            startIntervalMinutes = 10,
            endIntervalMinutes = 3,
        )

        assertEquals(11, occurrenceCount(s))

        val expectedOffsets = listOf(0, 10, 19, 27, 34, 40, 45, 50, 54, 58, 60)
        assertEquals(expectedOffsets, calculateOccurrenceOffsets(s))

        val today = LocalDate.of(2024, 1, 3)
        val expectedDateTimes = listOf(
            ZonedDateTime.of(today, java.time.LocalTime.of(7, 0), TOKYO),
            ZonedDateTime.of(today, java.time.LocalTime.of(7, 10), TOKYO),
            ZonedDateTime.of(today, java.time.LocalTime.of(7, 19), TOKYO),
            ZonedDateTime.of(today, java.time.LocalTime.of(7, 27), TOKYO),
            ZonedDateTime.of(today, java.time.LocalTime.of(7, 34), TOKYO),
            ZonedDateTime.of(today, java.time.LocalTime.of(7, 40), TOKYO),
            ZonedDateTime.of(today, java.time.LocalTime.of(7, 45), TOKYO),
            ZonedDateTime.of(today, java.time.LocalTime.of(7, 50), TOKYO),
            ZonedDateTime.of(today, java.time.LocalTime.of(7, 54), TOKYO),
            ZonedDateTime.of(today, java.time.LocalTime.of(7, 58), TOKYO),
            ZonedDateTime.of(today, java.time.LocalTime.of(8, 0), TOKYO),
        )

        var current = ZonedDateTime.of(today, java.time.LocalTime.of(6, 59), TOKYO)
        val actualDateTimes = mutableListOf<ZonedDateTime>()
        while (true) {
            val next = nextTrigger(s, current) ?: break
            actualDateTimes.add(next)
            current = next
        }
        assertEquals(expectedDateTimes, actualDateTimes)
    }

    // 開始間隔と終了間隔が同値のときに等間隔アラームの鳴動時刻列と完全に一致することを検証する。
    // 複数の間隔（5分・15分・30分）で検証し、可変間隔アルゴリズムの共通化によって既存の等間隔の挙動が壊れていないことを確認する。
    @Test
    fun `startIntervalMinutesとendIntervalMinutesが同値なら可変間隔でも等間隔と同じ列になる`() {
        // 7:00〜8:00 (span=60分) で5分、15分、30分の3通りの間隔で確認する
        val s5 = schedule(
            startMinutes = 7 * 60,
            endMinutes = 8 * 60,
            startIntervalMinutes = 5,
            endIntervalMinutes = 5,
        )
        val expectedOffsets5 = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60)
        assertEquals(13, occurrenceCount(s5))
        assertEquals(expectedOffsets5, calculateOccurrenceOffsets(s5))

        val s15 = schedule(
            startMinutes = 7 * 60,
            endMinutes = 8 * 60,
            startIntervalMinutes = 15,
            endIntervalMinutes = 15,
        )
        val expectedOffsets15 = listOf(0, 15, 30, 45, 60)
        assertEquals(5, occurrenceCount(s15))
        assertEquals(expectedOffsets15, calculateOccurrenceOffsets(s15))

        val s30 = schedule(
            startMinutes = 7 * 60,
            endMinutes = 8 * 60,
            startIntervalMinutes = 30,
            endIntervalMinutes = 30,
        )
        val expectedOffsets30 = listOf(0, 30, 60)
        assertEquals(3, occurrenceCount(s30))
        assertEquals(expectedOffsets30, calculateOccurrenceOffsets(s30))
    }

    // 間隔の計算結果が最低保証である1分を下回らないことを検証する。
    // 範囲終了付近で間隔が極小になった場合でも0分や負の間隔にならず、各鳴動が1分以上空くことを保証する。
    @Test
    fun `間隔の計算結果が1分を下回らず途中の間隔が0や負にならない`() {
        // 7:00〜7:20 (span=20分)、開始5分・終了1分のケース
        val s = schedule(
            startMinutes = 7 * 60,
            endMinutes = 7 * 60 + 20,
            startIntervalMinutes = 5,
            endIntervalMinutes = 1,
        )
        val expectedOffsets = listOf(0, 5, 9, 12, 15, 17, 19, 20)
        val offsets = calculateOccurrenceOffsets(s)
        assertEquals(expectedOffsets, offsets)

        for (i in 0 until offsets.size - 1) {
            val interval = offsets[i + 1] - offsets[i]
            assertTrue("各鳴動間の間隔は1分以上であること (index=$i, interval=$interval)", interval >= 1)
        }
    }

    // 可変間隔において、最後の鳴動が必ずセッション終了時刻に一致することを検証する。
    // 等間隔とは異なり、割り切れない場合でも最後の鳴動が終了時刻へ寄せて生成されることを保証する。
    @Test
    fun `可変間隔のとき最後の鳴動が必ず終了時刻に一致する`() {
        // 7:00〜7:30 (span=30分)、開始10分・終了4分（割り切れない設定）
        val s = schedule(
            startMinutes = 7 * 60,
            endMinutes = 7 * 60 + 30,
            startIntervalMinutes = 10,
            endIntervalMinutes = 4,
        )
        val expectedOffsets = listOf(0, 10, 18, 24, 29, 30)
        val offsets = calculateOccurrenceOffsets(s)
        assertEquals(expectedOffsets, offsets)
        assertEquals(30, offsets.last())

        val today = LocalDate.of(2024, 1, 3)
        val justBeforeLast = ZonedDateTime.of(today, java.time.LocalTime.of(7, 29), TOKYO)
        assertEquals(ZonedDateTime.of(today, java.time.LocalTime.of(7, 30), TOKYO), nextTrigger(s, justBeforeLast))

        val atLast = ZonedDateTime.of(today, java.time.LocalTime.of(7, 30), TOKYO)
        assertNull("最後の鳴動(7:30)以降はnextTriggerがnullになること", nextTrigger(s, atLast))
    }

    // 深夜から翌朝にかけて日をまたぐセッションにおいて可変間隔が正確に計算されることを検証する。
    // 日付跨ぎの分数計算と日付の繰り上がり、およびセッション開始日基準の次回鳴動・残り回数が保たれることを確認する。
    @Test
    fun `日をまたぐ範囲で可変間隔が正しく計算される`() {
        // 23:00〜翌01:00 (span=120分)、開始60分・終了20分
        val monday = LocalDate.of(2024, 1, 1)
        val tuesday = monday.plusDays(1)
        val s = schedule(
            startMinutes = 23 * 60,
            endMinutes = 60,
            startIntervalMinutes = 60,
            endIntervalMinutes = 20,
            repeatDays = setOf(DayOfWeek.MONDAY),
        )

        val expectedOffsets = listOf(0, 60, 100, 120)
        assertEquals(4, occurrenceCount(s))
        assertEquals(expectedOffsets, calculateOccurrenceOffsets(s))

        // 月曜22:30 -> 月曜23:00に鳴る
        val beforeStart = ZonedDateTime.of(monday, java.time.LocalTime.of(22, 30), TOKYO)
        assertEquals(ZonedDateTime.of(monday, java.time.LocalTime.of(23, 0), TOKYO), nextTrigger(s, beforeStart))

        // 月曜23:00 -> 火曜00:00に鳴る
        val atStart = ZonedDateTime.of(monday, java.time.LocalTime.of(23, 0), TOKYO)
        assertEquals(ZonedDateTime.of(tuesday, java.time.LocalTime.of(0, 0), TOKYO), nextTrigger(s, atStart))

        // 火曜00:00 -> 火曜00:40に鳴る
        val atMidnight = ZonedDateTime.of(tuesday, java.time.LocalTime.of(0, 0), TOKYO)
        assertEquals(ZonedDateTime.of(tuesday, java.time.LocalTime.of(0, 40), TOKYO), nextTrigger(s, atMidnight))

        // 火曜00:40 -> 火曜01:00に鳴る
        val at0040 = ZonedDateTime.of(tuesday, java.time.LocalTime.of(0, 40), TOKYO)
        assertEquals(ZonedDateTime.of(tuesday, java.time.LocalTime.of(1, 0), TOKYO), nextTrigger(s, at0040))

        // 火曜01:00 -> 次週月曜23:00まで鳴らない
        val nextMonday = monday.plusWeeks(1)
        val atEnd = ZonedDateTime.of(tuesday, java.time.LocalTime.of(1, 0), TOKYO)
        assertEquals(ZonedDateTime.of(nextMonday, java.time.LocalTime.of(23, 0), TOKYO), nextTrigger(s, atEnd))

        // 残り回数（そのセッション内で、現在鳴っている回を含めない残りの鳴動回数）
        assertEquals(3, remainingOccurrenceCount(s, atStart)) // 00:00, 00:40, 01:00
        assertEquals(2, remainingOccurrenceCount(s, atMidnight)) // 00:40, 01:00
        assertEquals(1, remainingOccurrenceCount(s, at0040)) // 01:00
        assertEquals(0, remainingOccurrenceCount(s, atEnd))
    }
}
