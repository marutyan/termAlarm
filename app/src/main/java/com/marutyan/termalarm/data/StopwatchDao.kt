package com.marutyan.termalarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * stopwatch_state / stopwatch_lap テーブルへのアクセスを定義するDAO。StopwatchRepositoryから利用する。
 */
@Dao
interface StopwatchDao {
    // idは常にSTOPWATCH_SINGLETON_ID(0)固定。行が無い(初回起動時)場合はnullが返る
    @Query("SELECT * FROM stopwatch_state WHERE id = 0")
    fun observeState(): Flow<StopwatchStateEntity?>

    @Query("SELECT * FROM stopwatch_state WHERE id = 0")
    suspend fun getStateOnce(): StopwatchStateEntity?

    // 単一行のシングルトンテーブルのため、無ければ挿入・あれば置き換えるupsertとして扱う
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(entity: StopwatchStateEntity)

    @Query("SELECT * FROM stopwatch_lap ORDER BY lapNumber")
    fun observeLaps(): Flow<List<StopwatchLapEntity>>

    @Insert
    suspend fun insertLap(entity: StopwatchLapEntity)

    // リセット時に全ラップを消す
    @Query("DELETE FROM stopwatch_lap")
    suspend fun clearLaps()
}
