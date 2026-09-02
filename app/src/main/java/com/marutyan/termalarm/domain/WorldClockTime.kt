package com.marutyan.termalarm.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * ある都市(targetZone)と端末(deviceZone)との時差。文字列化(strings.xml)はUI側の責務とし、
 * ここでは表示に必要な数値だけを持つ(RemainingTimeと同じ考え方、ScheduleCalculator.kt参照)。
 * hourPart/minutePartは時差の絶対値を時・分に分解したもの。インド(+5:30)やネパール(+5:45)のような
 * 30分・45分単位のオフセットにも対応するため、分単位で差を求めてから時・分へ分解している。
 * dayOffsetは端末側の日付を基準にした都市側の日付のずれ(前日=-1、当日=0、翌日=1など)。
 */
data class TimeDifference(
    val isAhead: Boolean,
    val hourPart: Int,
    val minutePart: Int,
    val dayOffset: Int,
)

/**
 * instant時点での、deviceZoneから見たtargetZoneとの時差と日付のずれを計算する。
 * サマータイムの有無や30分・45分単位のオフセットも、その瞬間のUTCオフセットの差として自然に扱える。
 */
fun timeDifference(targetZone: ZoneId, deviceZone: ZoneId, instant: Instant = Instant.now()): TimeDifference {
    val target = ZonedDateTime.ofInstant(instant, targetZone)
    val device = ZonedDateTime.ofInstant(instant, deviceZone)
    val offsetDiffMinutes = (target.offset.totalSeconds - device.offset.totalSeconds) / 60
    val dayOffset = ChronoUnit.DAYS.between(device.toLocalDate(), target.toLocalDate())
    val absMinutes = abs(offsetDiffMinutes)
    return TimeDifference(
        isAhead = offsetDiffMinutes >= 0,
        hourPart = absMinutes / 60,
        minutePart = absMinutes % 60,
        dayOffset = dayOffset.toInt(),
    )
}
