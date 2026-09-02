package com.marutyan.termalarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * clock_settingsテーブル(常に1行)へのアクセスを定義するDAO。ClockSettingsRepositoryから利用する。
 */
@Dao
interface ClockSettingsDao {
    @Query("SELECT * FROM clock_settings WHERE id = ${ClockSettingsEntity.SINGLE_ROW_ID}")
    fun observe(): Flow<ClockSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClockSettingsEntity)
}
