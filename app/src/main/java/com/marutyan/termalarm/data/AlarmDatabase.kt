package com.marutyan.termalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * アプリ唯一のRoomデータベース。アラーム、タイマー、ストップウォッチ、アプリ全体の設定の状態を持つ。
 *
 * 未公開のため、既存データを引き継ぐ移行処理は書かず、version 1から作り直している。
 * 既存の開発端末に古いversionのデータベースが残っていてもversion不整合でクラッシュしないよう、
 * ファイル名を alarm_schedule.db から termalarm.db へ変更した。
 * fallbackToDestructiveMigration は、公開後にマイグレーションを書き忘れた際に利用者のデータを黙って消してしまうため使わない。
 * スキーマを変えるたびに、1つずつマイグレーションを足していく。
 *
 * exportSchema = true にして app/schemas/ のJSONをコミットしている。次にスキーマを変えるときは、
 * 直前のバージョンのJSONと比べてMigrationを書く。
 *
 * 置き場所は端末保護ストレージ（device protected storage）にする。理由は[storageContext]にある。
 */
@Database(
    entities = [
        AlarmScheduleEntity::class,
        TimerEntity::class,
        StopwatchStateEntity::class,
        StopwatchLapEntity::class,
        AppSettingsEntity::class,
        RingRecordEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun timerDao(): TimerDao
    abstract fun stopwatchDao(): StopwatchDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun ringRecordDao(): RingRecordDao

    companion object {
        // データベースのファイル名。置き場所を移すときにも使うため、ここを唯一の出どころにする
        private const val DATABASE_NAME = "termalarm.db"

        @Volatile
        private var instance: AlarmDatabase? = null

        // アプリ全体で1つのDB接続を共有するためのシングルトン取得口
        fun getInstance(context: Context): AlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    storageContext(context),
                    AlarmDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        /**
         * タイマー専用の音を保存する列を足す移行。
         *
         * 以前はタイマーもアラームと同じ音しか鳴らせなかった。純正の時計アプリと同じく
         * 別々に選べるようにするため、列を1つ足す。既存の行はNULL、つまり
         * 「アラームと同じ音を使う」として扱われる。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN timerSoundUri TEXT")
            }
        }

        /**
         * データベースを置く場所を返す。端末のロックを解除する前でも読める
         * 端末保護ストレージ（device protected storage）を使う。
         *
         * 通常のストレージはロックを解除するまで読めない。そこへ置いていると、端末を再起動した後、
         * 解除するまでアラームの予約を入れ直せない。夜間に更新などで再起動されると朝に鳴らない。
         *
         * 以前の版が通常のストレージへ作ったファイルは、ここへ一度だけ移す。
         * 移動はファイルを開く前でないとできないため、接続を作る直前に行う。
         * 既に移動済み、または元のファイルが無ければ何も起きない。
         *
         * ロック解除に紐づく暗号化の保護からは外れるが、置くのはアラームの時刻と設定であり、
         * 鳴らないほうが実害が大きいと判断した。
         */
        private fun storageContext(context: Context): Context {
            val appContext = context.applicationContext
            val deviceContext = appContext.createDeviceProtectedStorageContext()
            deviceContext.moveDatabaseFrom(appContext, DATABASE_NAME)
            return deviceContext
        }
    }
}
