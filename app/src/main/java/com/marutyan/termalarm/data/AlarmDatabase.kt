package com.marutyan.termalarm.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * アプリ唯一のRoomデータベース。alarm_schedule/stopwatch_state/stopwatch_lapの3テーブルを持つ。
 *
 * ストップウォッチ機能の担当はこのworktree(feature/stopwatch-tab)の基点(main)ではDBがversion 1
 * (alarm_scheduleのみ)のため、ここではMIGRATION_1_3として直接version 3へ上げる。並行して進めている
 * タイマー機能(feature/timer-tab)がtimer_stateを足してversion 2にする作業をしており、
 * 両方をmergeする際にPMがMigrationの連番(1→2→3)を付け直して統合する前提（それまでは単独でも
 * assembleDebug/testDebugUnitTestが通るようMIGRATION_1_3として自己完結させている）。
 *
 * テーブルを追加してバージョンを据え置くと、既にアプリが入っていた端末でテーブルが作られず機能しない
 * (実際にタイマー機能で起きた事故)。fallbackToDestructiveMigrationは使わない。既存のアラーム設定が
 * 消えてしまうため。
 *
 * exportSchema = true にして app/schemas/ のJSONをコミットしている。次にスキーマを変えるときは、
 * 直前のバージョンのJSONと比べてMigrationを書く。
 */
@Database(entities = [AlarmScheduleEntity::class, StopwatchStateEntity::class, StopwatchLapEntity::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun stopwatchDao(): StopwatchDao

    companion object {
        // ストップウォッチのテーブルを追加する
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stopwatch_state` (" +
                        "`id` INTEGER PRIMARY KEY NOT NULL, " +
                        "`accumulatedMillis` INTEGER NOT NULL, " +
                        "`anchorElapsedRealtime` INTEGER NOT NULL, " +
                        "`anchorWallClockMillis` INTEGER NOT NULL, " +
                        "`runState` TEXT NOT NULL)",
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

        @Volatile
        private var instance: AlarmDatabase? = null

        // アプリ全体で1つのDB接続を共有するためのシングルトン取得口。ui/alarm担当がRepository生成時に使う
        fun getInstance(context: Context): AlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarm_schedule.db",
                ).addMigrations(MIGRATION_1_3).build().also { instance = it }
            }
    }
}
