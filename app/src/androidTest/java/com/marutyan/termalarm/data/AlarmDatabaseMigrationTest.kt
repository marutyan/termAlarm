package com.marutyan.termalarm.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * データベースの移行が、保存済みの設定を保ったまま行われることを確かめるテスト。
 *
 * 移行を間違えると、利用者が選んだ音や配色が黙って消える。ここで実際に古い形の行を
 * 書き込んでから移行し、値が残っていることと、足した列が空で始まることを固定する。
 */
@RunWith(AndroidJUnit4::class)
class AlarmDatabaseMigrationTest {

    private val databaseName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AlarmDatabase::class.java,
    )

    @Test
    fun タイマーの音を足す移行で既存の設定が残る() {
        helper.createDatabase(databaseName, 1).use { db ->
            db.execSQL(
                "INSERT INTO app_settings " +
                    "(id, alarmSoundUri, vibration, fadeInSeconds, silenceAfterMinutes, " +
                    "wakeCheckMinutes, theme, enabledGames) " +
                    "VALUES (0, 'content://media/alarm/7', 1, 5, 10, 5, 'BLACK', 'ARITHMETIC,SHAKE_DEVICE')",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            AlarmDatabase.MIGRATION_1_2,
        )

        migrated.query(
            "SELECT alarmSoundUri, timerSoundUri, theme, enabledGames FROM app_settings WHERE id = 0",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("content://media/alarm/7", cursor.getString(0))
            // 足した列は空で始まる。空は「アラームと同じ音を使う」を表す
            assertTrue(cursor.isNull(1))
            assertEquals("BLACK", cursor.getString(2))
            assertEquals("ARITHMETIC,SHAKE_DEVICE", cursor.getString(3))
        }
    }
}
