package com.marutyan.termalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * アプリ唯一のRoomデータベース。アラーム、タイマー、ストップウォッチ、時計タブの表示設定、アプリ全体の設定の状態を持つ。
 *
 * 未公開のため、既存データを引き継ぐ移行処理は書かず、version 1から作り直している。
 * 既存の開発端末に古いversionのデータベースが残っていてもversion不整合でクラッシュしないよう、
 * ファイル名を alarm_schedule.db から termalarm.db へ変更した。
 * fallbackToDestructiveMigration は、公開後にマイグレーションを書き忘れた際に利用者のデータを黙って消してしまうため使わない。
 * version 1 を出荷した後は、これまでと同じく1つずつマイグレーションを足していく方針とする。
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
        RingRecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun timerDao(): TimerDao
    abstract fun stopwatchDao(): StopwatchDao
    abstract fun clockSettingsDao(): ClockSettingsDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun ringRecordDao(): RingRecordDao

    companion object {
        @Volatile
        private var instance: AlarmDatabase? = null

        // アプリ全体で1つのDB接続を共有するためのシングルトン取得口
        fun getInstance(context: Context): AlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "termalarm.db",
                ).build().also { instance = it }
            }
    }
}
