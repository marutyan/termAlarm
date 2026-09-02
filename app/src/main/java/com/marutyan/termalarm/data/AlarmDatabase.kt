package com.marutyan.termalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * アプリ唯一のRoomデータベース。alarm_schedule/world_clock_city/clock_settingsの3テーブルを持つ。
 *
 * 世界時計のテーブルを足すときにバージョンを据え置くと、既にアプリが入っていた端末でテーブルが
 * 作られず機能しなくなる(タイマーで実際に起きた事故と同じ)。スキーマを変えたらバージョンを上げ、
 * Migrationを書く。
 *
 * exportSchema = true にして app/schemas/ のJSONをコミットしている。次にスキーマを変えるときは、
 * 直前のバージョンのJSONと比べてMigrationを書く。
 */
@Database(
    entities = [AlarmScheduleEntity::class, WorldClockCityEntity::class, ClockSettingsEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun worldClockCityDao(): WorldClockCityDao
    abstract fun clockSettingsDao(): ClockSettingsDao

    companion object {
        /**
         * 世界時計の都市一覧テーブルと表示設定テーブルを追加する。
         * fallbackToDestructiveMigrationは使わない。既存のアラーム設定が消えてしまうため。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `world_clock_city` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`zoneId` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `clock_settings` (" +
                        "`id` INTEGER PRIMARY KEY NOT NULL, " +
                        "`displayMode` TEXT NOT NULL)",
                )
            }
        }

        @Volatile
        private var instance: AlarmDatabase? = null

        // アプリ全体で1つのDB接続を共有するためのシングルトン取得口。ui/alarm担当がRepository生成時に使う
        fun getInstance(context: Context): AlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarm_schedule.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
