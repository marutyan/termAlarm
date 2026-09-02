package com.marutyan.termalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marutyan.termalarm.domain.AlarmSchedule
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * AlarmSchedule をRoomで永続化するためのテーブル定義。
 * repeatDays(Set<DayOfWeek>)とskippedSessionStart(LocalDate)はConvertersに登録したTypeConverterで
 * DB用のプリミティブ型に変換される。domain.AlarmScheduleとの相互変換はtoDomain()/toEntity()で行い、
 * domain層をRoomに依存させない。
 */
@Entity(tableName = "alarm_schedule")
data class AlarmScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val startMinutes: Int,
    val endMinutes: Int,
    val intervalMinutes: Int,
    val repeatDays: Set<DayOfWeek>,
    val label: String,
    val soundUri: String?,
    val vibrate: Boolean,
    val enabled: Boolean,
    val skippedSessionStart: LocalDate?,
    val skipRequiresApp: Boolean = true, // 当日終了をアプリからのみ許すか（既定true）
    val skipGame: Boolean = false, // 当日終了の前にゲームを1問挟むか（既定false）
    val snoozeMinutes: Int? = null, // スヌーズの分数。nullなら無効（既定null）
)

// data層のEntityからdomain層のAlarmScheduleへ変換する
internal fun AlarmScheduleEntity.toDomain() = AlarmSchedule(
    id = id,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    intervalMinutes = intervalMinutes,
    repeatDays = repeatDays,
    label = label,
    soundUri = soundUri,
    vibrate = vibrate,
    enabled = enabled,
    skippedSessionStart = skippedSessionStart,
    skipRequiresApp = skipRequiresApp,
    skipGame = skipGame,
    snoozeMinutes = snoozeMinutes,
)

// domain層のAlarmScheduleをRoomで保存するEntityへ変換する
internal fun AlarmSchedule.toEntity() = AlarmScheduleEntity(
    id = id,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    intervalMinutes = intervalMinutes,
    repeatDays = repeatDays,
    label = label,
    soundUri = soundUri,
    vibrate = vibrate,
    enabled = enabled,
    skippedSessionStart = skippedSessionStart,
    skipRequiresApp = skipRequiresApp,
    skipGame = skipGame,
    snoozeMinutes = snoozeMinutes,
)
