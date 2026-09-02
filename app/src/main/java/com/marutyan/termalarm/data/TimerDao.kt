package com.marutyan.termalarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * timer_stateテーブルへのアクセスを定義するDAO。TimerRepositoryから利用する。
 */
@Dao
interface TimerDao {
    @Query("SELECT * FROM timer_state ORDER BY id")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timer_state WHERE id = :id")
    suspend fun getById(id: Long): TimerEntity?

    // 端末再起動直後、RUNNING中だったタイマーだけを再アンカーするために使う(TimerBootReceiver)
    @Query("SELECT * FROM timer_state WHERE runState = 'RUNNING'")
    suspend fun getAllRunningOnce(): List<TimerEntity>

    // 指定した実行状態のタイマーの件数。サービスを続けるかの判断に使う
    @Query("SELECT COUNT(*) FROM timer_state WHERE runState IN (:states)")
    suspend fun countByRunStates(states: List<String>): Int

    @Insert
    suspend fun insert(entity: TimerEntity): Long

    @Update
    suspend fun update(entity: TimerEntity)

    @Delete
    suspend fun delete(entity: TimerEntity)

    @Query("DELETE FROM timer_state WHERE id = :id")
    suspend fun deleteById(id: Long)
}
