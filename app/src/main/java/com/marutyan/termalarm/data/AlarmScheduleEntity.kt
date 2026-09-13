package com.marutyan.termalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.ChallengeLevel
import com.marutyan.termalarm.domain.ChallengeTiming
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * AlarmSchedule をRoomで永続化するためのテーブル定義。
 * repeatDays(Set<DayOfWeek>)、skippedSessionStart(LocalDate)、challengeTiming(ChallengeTiming)、challenge(ChallengeLevel)は
 * Convertersに登録したTypeConverterでDB用のプリミティブ型に変換される。
 * domain.AlarmScheduleとの相互変換はtoDomain()/toEntity()で行い、domain層をRoomに依存させない。
 */
@Entity(tableName = "alarm_schedule")
data class AlarmScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val startMinutes: Int,
    val endMinutes: Int,
    val startIntervalMinutes: Int,
    val endIntervalMinutes: Int,
    val repeatDays: Set<DayOfWeek>,
    val label: String,
    val enabled: Boolean,
    val challengeTiming: ChallengeTiming = ChallengeTiming.NEVER,
    val challenge: ChallengeLevel,
    val wakeCheck: Boolean = false,
    val skippedSessionStart: LocalDate?,
)

// data層のEntityからdomain層のAlarmScheduleへ変換する
internal fun AlarmScheduleEntity.toDomain() = AlarmSchedule(
    id = id,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    startIntervalMinutes = startIntervalMinutes,
    endIntervalMinutes = endIntervalMinutes,
    repeatDays = repeatDays,
    label = label,
    enabled = enabled,
    challengeTiming = challengeTiming,
    challenge = challenge,
    wakeCheck = wakeCheck,
    skippedSessionStart = skippedSessionStart,
)

// domain層のAlarmScheduleをRoomで保存するEntityへ変換する
internal fun AlarmSchedule.toEntity() = AlarmScheduleEntity(
    id = id,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    startIntervalMinutes = startIntervalMinutes,
    endIntervalMinutes = endIntervalMinutes,
    repeatDays = repeatDays,
    label = label,
    enabled = enabled,
    challengeTiming = challengeTiming,
    challenge = challenge,
    wakeCheck = wakeCheck,
    skippedSessionStart = skippedSessionStart,
)

