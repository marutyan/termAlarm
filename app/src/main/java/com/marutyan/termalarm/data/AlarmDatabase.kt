package com.marutyan.termalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * アプリ唯一のRoomデータベース。alarm_schedule/timer_stateの2テーブルを持つ。
 * 未リリースのため現行スキーマがそのままversion 1（timer_stateの追加を含む）。
 * まだ配布されていない＝端末上に旧スキーマのDBファイルが存在し得ないため、Migrationを書く必要がなく、
 * バージョンを1のまま更新後のスキーマをexportSchemaで書き出す方針にした（app/schemas/1.jsonが更新される）。
 * リリース後にスキーマを変えるときは、その時点からversionを上げてMigrationを書き始める。
 * 将来のスキーマ変更に備え exportSchema = true とし、生成されたJSONを app/schemas/ にコミットする。
 */
@Database(entities = [AlarmScheduleEntity::class, TimerEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun timerDao(): TimerDao

    companion object {
        @Volatile
        private var instance: AlarmDatabase? = null

        // アプリ全体で1つのDB接続を共有するためのシングルトン取得口。ui/alarm担当がRepository生成時に使う
        fun getInstance(context: Context): AlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarm_schedule.db",
                ).build().also { instance = it }
            }
    }
}
