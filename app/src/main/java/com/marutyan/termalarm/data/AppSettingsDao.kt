package com.marutyan.termalarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * app_settingsテーブル(常に1行)へのアクセスを定義するDAO。SettingsRepositoryから利用する。
 */
@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = ${AppSettingsEntity.SINGLE_ROW_ID}")
    fun observe(): Flow<AppSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppSettingsEntity)
}
