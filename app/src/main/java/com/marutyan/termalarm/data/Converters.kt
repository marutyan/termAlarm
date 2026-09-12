package com.marutyan.termalarm.data

import androidx.room.TypeConverter
import com.marutyan.termalarm.domain.ChallengeLevel
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Room が素のままでは保存できない型（Set<DayOfWeek>、LocalDate、ChallengeLevel）をDB用のプリミティブ型と相互変換する。
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

    /**
     * 解除チャレンジの強さを文字列へ変換する。
     * RoomでChallengeLevelの列をTEXT型として保存するために必要。
     */
    @TypeConverter
    fun fromChallengeLevel(challenge: ChallengeLevel): String = challenge.name

    /**
     * 保存された文字列から解除チャレンジの強さを復元する。
     * DBから読み出した文字列をdomainの型へ戻すために必要。未知の文字列に当たった場合はNONEへ倒す。
     */
    @TypeConverter
    fun toChallengeLevel(name: String): ChallengeLevel =
        runCatching { ChallengeLevel.valueOf(name) }.getOrDefault(ChallengeLevel.NONE)
}
