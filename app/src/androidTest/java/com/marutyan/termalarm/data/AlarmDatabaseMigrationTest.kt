package com.marutyan.termalarm.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * データベースの移行が狙いどおり働くことを守る。
 *
 * テーブルを足したのに版を上げ忘れると、開発中の端末では前の版が残るため
 * テーブルが作られず機能しない（実際に起きた）。
 * さらに、値を書き換える種類の移行は、書いただけでは正しさが分からない。
 * ここでは古い版の形でデータを入れ、移行を通してから中身を確かめる。
 *
 * Roomが用意しているMigrationTestHelperは使わない。
 * それが読むスキーマJSONの解析部分が、このプロジェクトの他の依存と版が合わず動かないため。
 * 移行の処理自体は普通の関数なので、直接呼べば同じことを確かめられる。
 */
@RunWith(AndroidJUnit4::class)
class AlarmDatabaseMigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-test.db"
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        context.deleteDatabase(dbName)
    }

    /** 版6の形でデータベースを作り、設定の行を1つ入れる */
    private fun createVersion6(showClockSeconds: Int): SupportSQLiteDatabase {
        context.deleteDatabase(dbName)
        val callback = object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: SupportSQLiteDatabase) {
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
                db.execSQL(
                    "INSERT INTO app_settings VALUES " +
                        "(1, 'TAP', 10, 5, 5, 'ADJUST_VOLUME', 'SUNDAY', $showClockSeconds, NULL, 1.5, 1)",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val h = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build(),
        )
        helper = h
        return h.writableDatabase
    }

    private fun readShowClockSeconds(db: SupportSQLiteDatabase): Int =
        db.query("SELECT showClockSeconds FROM app_settings WHERE id = 1").use { cursor ->
            assertTrue("設定の行が残っていること", cursor.moveToFirst())
            cursor.getInt(0)
        }

    @Test
    fun 版6から7へ移ると時計の秒表示が入る() {
        val db = createVersion6(showClockSeconds = 0)
        assertEquals("移行の前は秒を出さない側", 0, readShowClockSeconds(db))

        AlarmDatabase.MIGRATION_6_7.migrate(db)

        assertEquals("移行の後は秒を出す側へ書き換わる", 1, readShowClockSeconds(db))
    }

    @Test
    fun すでに秒を出す設定なら値は変わらない() {
        val db = createVersion6(showClockSeconds = 1)

        AlarmDatabase.MIGRATION_6_7.migrate(db)

        assertEquals("そのまま", 1, readShowClockSeconds(db))
    }
}
