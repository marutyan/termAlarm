package com.marutyan.termalarm.data

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Room が素のままでは保存できない型（Set<DayOfWeek>、LocalDate）をDB用のプリミティブ型と相互変換する。
 * AlarmDatabase に登録して使う。
 */
class Converters {
    // Set<DayOfWeek> をビットマスクのIntへ変換する。bit(n) は DayOfWeek.of(n+1) が含まれるかを表す
    @TypeConverter
    fun fromDayOfWeekSet(days: Set<DayOfWeek>): Int =
        days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    @TypeConverter
    fun toDayOfWeekSet(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filterTo(mutableSetOf()) { day -> (mask shr (day.value - 1)) and 1 == 1 }

    // LocalDate はエポック日数(Long)へ変換して保存する。未設定(null)はそのままnullで保存する
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}
