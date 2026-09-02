package com.marutyan.termalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState

/**
 * TimerStateをRoomで永続化するためのテーブル定義。全フィールドが素のプリミティブ型のため
 * 追加のTypeConverterは不要（runStateはenum名の文字列としてそのまま保存し、toDomain/toEntityで変換する）。
 * AlarmDatabaseとは別ファイルに定義し、AlarmDatabase.kt側の変更をentity登録の1行に留める
 * （複数担当が同時にAlarmDatabase.ktを編集するため）。
 */
@Entity(tableName = "timer_state")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val label: String,
    val totalMillis: Long,
    val remainingMillisAtAnchor: Long,
    val anchorElapsedRealtime: Long,
    val anchorWallClockMillis: Long,
    val runState: String, // TimerRunState.name
)

internal fun TimerEntity.toDomain() = TimerState(
    id = id,
    label = label,
    totalMillis = totalMillis,
    remainingMillisAtAnchor = remainingMillisAtAnchor,
    anchorElapsedRealtime = anchorElapsedRealtime,
    anchorWallClockMillis = anchorWallClockMillis,
    runState = TimerRunState.valueOf(runState),
)

internal fun TimerState.toEntity() = TimerEntity(
    id = id,
    label = label,
    totalMillis = totalMillis,
    remainingMillisAtAnchor = remainingMillisAtAnchor,
    anchorElapsedRealtime = anchorElapsedRealtime,
    anchorWallClockMillis = anchorWallClockMillis,
    runState = runState.name,
)
