package com.marutyan.termalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marutyan.termalarm.domain.StopwatchLap

/**
 * StopwatchLap(ラップ1件)をRoomで永続化するためのテーブル定義。stopwatch_stateとは別テーブルとし、
 * リセット時はこのテーブルを全削除するだけでよい形にする(StopwatchRepository.clearLaps)。
 */
@Entity(tableName = "stopwatch_lap")
data class StopwatchLapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lapNumber: Int,
    val lapMillis: Long,
    val totalMillis: Long,
)

internal fun StopwatchLapEntity.toDomain() = StopwatchLap(
    lapNumber = lapNumber,
    lapMillis = lapMillis,
    totalMillis = totalMillis,
)

internal fun StopwatchLap.toEntity() = StopwatchLapEntity(
    lapNumber = lapNumber,
    lapMillis = lapMillis,
    totalMillis = totalMillis,
)
