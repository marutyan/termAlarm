package com.marutyan.termalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marutyan.termalarm.domain.StopwatchRunState
import com.marutyan.termalarm.domain.StopwatchState

// stopwatch_stateテーブルの主キー固定値。ストップウォッチはアプリ全体で1つだけ存在する
// 単一行のシングルトンテーブルとして扱う(複数同時計測はSPEC対象外のため)
internal const val STOPWATCH_SINGLETON_ID = 0

/**
 * StopwatchStateをRoomで永続化するためのテーブル定義。全フィールドが素のプリミティブ型のため
 * 追加のTypeConverterは不要（runStateはenum名の文字列としてそのまま保存し、toDomain/toEntityで変換する）。
 * AlarmDatabaseとは別ファイルに定義し、AlarmDatabase.kt側の変更をentity登録の1行に留める
 * （複数担当が同時にAlarmDatabase.ktを編集するため、タイマー機能のTimerEntityと同じ方針）。
 */
@Entity(tableName = "stopwatch_state")
data class StopwatchStateEntity(
    @PrimaryKey
    val id: Int = STOPWATCH_SINGLETON_ID,
    val accumulatedMillis: Long,
    val anchorElapsedRealtime: Long,
    val anchorWallClockMillis: Long,
    val runState: String, // StopwatchRunState.name
)

internal fun StopwatchStateEntity.toDomain() = StopwatchState(
    accumulatedMillis = accumulatedMillis,
    anchorElapsedRealtime = anchorElapsedRealtime,
    anchorWallClockMillis = anchorWallClockMillis,
    runState = StopwatchRunState.valueOf(runState),
)

internal fun StopwatchState.toEntity() = StopwatchStateEntity(
    accumulatedMillis = accumulatedMillis,
    anchorElapsedRealtime = anchorElapsedRealtime,
    anchorWallClockMillis = anchorWallClockMillis,
    runState = runState.name,
)
