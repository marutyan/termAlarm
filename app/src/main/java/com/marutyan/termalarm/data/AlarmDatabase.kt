package com.marutyan.termalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * アプリ唯一のRoomデータベース。alarm_scheduleテーブルのみを持つ。
 * 未リリースのため現行スキーマがそのままversion 1（skipRequiresApp/skipGame/snoozeMinutesを含む）。
 * 将来のスキーマ変更に備え exportSchema = true とし、生成されたJSONを app/schemas/ にコミットする。
 */
@Database(entities = [AlarmScheduleEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

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
