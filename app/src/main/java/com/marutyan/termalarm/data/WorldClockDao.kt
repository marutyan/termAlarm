package com.marutyan.termalarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * world_clock_cityテーブルへのアクセスを定義するDAO。WorldClockRepositoryから利用する。
 */
@Dao
interface WorldClockCityDao {
    @Query("SELECT * FROM world_clock_city ORDER BY sortOrder")
    fun observeAll(): Flow<List<WorldClockCityEntity>>

    @Query("SELECT * FROM world_clock_city ORDER BY sortOrder")
    suspend fun getAll(): List<WorldClockCityEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM world_clock_city")
    suspend fun maxSortOrder(): Int

    // 新規追加時はid=0を渡すとautoGenerateにより新しいidが割り当てられる
    @Insert
    suspend fun insert(entity: WorldClockCityEntity): Long

    // 並べ替え結果を一括で反映する。1件ずつdeleteAndInsertし直すより安全(idが変わらない)
    @Update
    suspend fun updateAll(entities: List<WorldClockCityEntity>)

    @Query("DELETE FROM world_clock_city WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/**
 * clock_settingsテーブル(常に1行)へのアクセスを定義するDAO。WorldClockRepositoryから利用する。
 */
@Dao
interface ClockSettingsDao {
    @Query("SELECT * FROM clock_settings WHERE id = ${ClockSettingsEntity.SINGLE_ROW_ID}")
    fun observe(): Flow<ClockSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClockSettingsEntity)
}
