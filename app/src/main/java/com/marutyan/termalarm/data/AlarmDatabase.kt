package com.marutyan.termalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * アプリ唯一のRoomデータベース。アラーム、タイマー、ストップウォッチ、時計タブの表示設定の状態を持つ。
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
        ClockSettingsEntity::class,
        AppSettingsEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun timerDao(): TimerDao
    abstract fun stopwatchDao(): StopwatchDao
    abstract fun clockSettingsDao(): ClockSettingsDao
    abstract fun appSettingsDao(): AppSettingsDao

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

        /** 世界時計をやめたので、都市の一覧を消す */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `world_clock_city`")
            }
        }

        /** アプリ全体の設定を保存するテーブルを追加する */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_settings` (" +
                        "`id` INTEGER PRIMARY KEY NOT NULL, " +
                        "`dismissMethod` TEXT NOT NULL, " +
                        "`autoStopMinutes` INTEGER NOT NULL, " +
                        "`defaultSnoozeMinutes` INTEGER NOT NULL, " +
                        "`alarmFadeInSeconds` INTEGER NOT NULL, " +
                        "`volumeButtonAction` TEXT NOT NULL, " +
                        "`weekStart` TEXT NOT NULL, " +
                        "`showClockSeconds` INTEGER NOT NULL, " +
                        "`timerSoundUri` TEXT, " +
                        "`timerFadeInSeconds` REAL NOT NULL, " +
                        "`timerVibration` INTEGER NOT NULL)",
                )
            }
        }

        /**
         * 時計に秒を出す設定の既定を、出さないから出すへ変える。
         * 純正の時計は秒まで出しており、そちらを既定にすると決めたため
         * (利用者の指示、2026年9月5日)。すでに保存されている行も合わせて書き換える。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `app_settings` SET `showClockSeconds` = 1")
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
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                )
                    .build().also { instance = it }
            }
    }
}
