package com.marutyan.termalarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * alarm_scheduleテーブルへのアクセスを定義するDAO。AlarmRepositoryから利用する。
 */
@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm_schedule ORDER BY id")
    fun observeAll(): Flow<List<AlarmScheduleEntity>>

    @Query("SELECT * FROM alarm_schedule WHERE id = :id")
    suspend fun getById(id: Long): AlarmScheduleEntity?

    // 新規追加時はid=0を渡すとautoGenerateにより新しいidが割り当てられ、そのidを返す
    @Insert
    suspend fun insert(entity: AlarmScheduleEntity): Long

    @Update
    suspend fun update(entity: AlarmScheduleEntity)

    @Delete
    suspend fun delete(entity: AlarmScheduleEntity)

    @Query("UPDATE alarm_schedule SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
