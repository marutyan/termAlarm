package com.marutyan.termalarm.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * アプリ唯一のRoomデータベース。alarm_schedule/timer_stateの2テーブルを持つ。
 *
 * timer_stateを足すときにバージョンを1のまま据え置いたところ、既にアプリが入っていた端末で
 * テーブルが作られず、タイマーがまったく動かなかった。未リリースでも開発端末には前の版のDBが
 * 残っているため、スキーマを変えたらバージョンを上げてMigrationを書く必要がある。
 *
 * exportSchema = true にして app/schemas/ のJSONをコミットしている。次にスキーマを変えるときは、
 * 直前のバージョンのJSONと比べてMigrationを書く。
 */
@Database(entities = [AlarmScheduleEntity::class, TimerEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun timerDao(): TimerDao

    companion object {
        /**
         * タイマーのテーブルを追加する。
         * fallbackToDestructiveMigrationは使わない。既存のアラーム設定が消えてしまうため。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timer_state` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`totalMillis` INTEGER NOT NULL, " +
                        "`remainingMillisAtAnchor` INTEGER NOT NULL, " +
                        "`anchorElapsedRealtime` INTEGER NOT NULL, " +
                        "`anchorWallClockMillis` INTEGER NOT NULL, " +
                        "`runState` TEXT NOT NULL)",
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
