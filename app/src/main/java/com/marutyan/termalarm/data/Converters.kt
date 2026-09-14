package com.marutyan.termalarm.data

import androidx.room.TypeConverter
import com.marutyan.termalarm.domain.ChallengeLevel
import com.marutyan.termalarm.domain.ChallengeTiming
import com.marutyan.termalarm.domain.GameType
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Room が素のままでは保存できない型（Set<DayOfWeek>、LocalDate、ChallengeTiming、ChallengeLevel、Set<GameType>）をDB用のプリミティブ型と相互変換する。
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
     * 解除チャレンジの出題タイミングを文字列へ変換する。
     * RoomでChallengeTimingの列をTEXT型として保存するために必要。
     */
    @TypeConverter
    fun fromChallengeTiming(timing: ChallengeTiming): String = timing.name

    /**
     * 保存された文字列から解除チャレンジの出題タイミングを復元する。
     * DBから読み出した文字列をdomainの型へ戻すために必要。未知の文字列に当たった場合はNEVERへ倒す。
     */
    @TypeConverter
    fun toChallengeTiming(name: String): ChallengeTiming =
        runCatching { ChallengeTiming.valueOf(name) }.getOrDefault(ChallengeTiming.NEVER)

    /**
     * 解除チャレンジの強さを文字列へ変換する。
     * RoomでChallengeLevelの列をTEXT型として保存するために必要。
     */
    @TypeConverter
    fun fromChallengeLevel(challenge: ChallengeLevel): String = challenge.name

    /**
     * 保存された文字列から解除チャレンジの強さを復元する。
     * DBから読み出した文字列をdomainの型へ戻すために必要。未知の文字列に当たった場合はEASYへ倒す。
     */
    @TypeConverter
    fun toChallengeLevel(name: String): ChallengeLevel =
        runCatching { ChallengeLevel.valueOf(name) }.getOrDefault(ChallengeLevel.EASY)

    /**
     * 有効にするミニゲームの集合をカンマ区切りの文字列へ変換する。
     * RoomでenabledGames列（Set<GameType>）をTEXT型として保存するために必要。
     */
    @TypeConverter
    fun fromGameTypeSet(games: Set<GameType>): String =
        games.joinToString(",") { it.name }

    /**
     * カンマ区切りの文字列から有効にするミニゲームの集合を復元する。
     * DBから読み出した文字列をdomainの型へ戻すために必要。未知の文字列は除外する。
     */
    @TypeConverter
    fun toGameTypeSet(value: String): Set<GameType> {
        if (value.isBlank()) return emptySet()
        return value.split(",")
            .mapNotNull { name -> runCatching { GameType.valueOf(name) }.getOrNull() }
            .toSet()
    }
}
