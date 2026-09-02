package com.marutyan.termalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * アプリ唯一のRoomデータベース。アラーム、タイマー、ストップウォッチ、世界時計の状態を持つ。
 *
 * タイマーのテーブルを足すときにバージョンを1のまま据え置いたところ、既にアプリが入っていた端末で
 * テーブルが作られず、タイマーがまったく動かなかった。未リリースでも開発端末には前の版のDBが
 * 残っているため、スキーマを変えたらバージョンを上げてMigrationを書く必要がある。
 *
 * Migrationは1つずつ順に並べる。飛び番の経路を書くと、どの版からどの版へ上がるときに何が起きるかを
 * 追えなくなる。fallbackToDestructiveMigrationは使わない。既存のアラーム設定が消えてしまうため。
 *
 * exportSchema = true にして app/schemas/ のJSONをコミットしている。次にスキーマを変えるときは、
 * 直前のバージョンのJSONと比べてMigrationを書く。
 */
@Database(
    entities = [
        AlarmScheduleEntity::class,
        TimerEntity::class,
        StopwatchStateEntity::class,
        StopwatchLapEntity::class,
        WorldClockCityEntity::class,
        ClockSettingsEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun timerDao(): TimerDao
    abstract fun stopwatchDao(): StopwatchDao
    abstract fun worldClockCityDao(): WorldClockCityDao
    abstract fun clockSettingsDao(): ClockSettingsDao

    companion object {
        /** タイマーのテーブルを追加する */
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

        /** ストップウォッチの状態とラップのテーブルを追加する */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stopwatch_state` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`accumulatedMillis` INTEGER NOT NULL, " +
                        "`anchorElapsedRealtime` INTEGER NOT NULL, " +
                        "`anchorWallClockMillis` INTEGER NOT NULL, " +
                        "`runState` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stopwatch_lap` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`lapNumber` INTEGER NOT NULL, " +
                        "`lapMillis` INTEGER NOT NULL, " +
                        "`totalMillis` INTEGER NOT NULL)",
                )
            }
        }

        /** 世界時計の都市一覧と、時計の表示設定のテーブルを追加する */
        val MIGRATION_3_4 = object : Migration(3, 4) {
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

        // アプリ全体で1つのDB接続を共有するためのシングルトン取得口
        fun getInstance(context: Context): AlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarm_schedule.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}
