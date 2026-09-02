package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * アラーム一覧画面から「FABで追加→既定値のまま保存→一覧に反映→開いて復元」までの一連の流れを保証する。
 * PMがadb操作で確認できなかった「保存が一覧に反映されるか」を実機で直接検証するのが目的(最優先項目)。
 * 各テストの前後でインメモリDBを作り直し、テスト同士が影響しないようにする。
 */
@OptIn(ExperimentalTestApi::class)
class AlarmListScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AlarmDatabase
    private lateinit var repository: AlarmRepository

    @Before
    fun setUp() {
        val (database, repo) = createTestRepository()
        db = database
        repository = repo
    }

    @After
    fun tearDown() {
        db.close()
    }

    // アラームが1件も無いとき、案内文が表示されることを保証する
    @Test
    fun アラームが無いとき案内文が表示される() {
        composeTestRule.setContent { ListEditHost(repository) }
        composeTestRule.onNodeWithText(context().getString(R.string.alarm_list_empty)).assertExists()
    }

    // FAB(追加ボタン)を押すと編集画面が開き、既定値のまま保存すると一覧に1件現れ、
    // 一覧の行に「5分ごと・25回」相当の要約が出ることを保証する
    @Test
    fun fabで追加した既定値のアラームが一覧に1件現れ要約が出る() {
        composeTestRule.setContent { ListEditHost(repository) }

        composeTestRule.onNodeWithContentDescription(context().getString(R.string.add_alarm)).performClick()
        // 編集画面(新規)のタイトルが表示されるまで待つ
        composeTestRule.waitUntilAtLeastOneExists(hasText(context().getString(R.string.edit_title_new)), 5_000)

        composeTestRule.onNodeWithText(context().getString(R.string.save)).performClick()

        // 保存はRoomへのsuspend書き込みを挟むため、一覧へ戻り行の要約が出るまで待つ
        val summary = "5分ごと · 25回"
        composeTestRule.waitUntilAtLeastOneExists(hasText(summary), 5_000)
        composeTestRule.onNodeWithText("7:00").assertExists()
        composeTestRule.onNodeWithText("9:00").assertExists()

        val saved = runBlocking { repository.observeAll().first() }
        org.junit.Assert.assertEquals(1, saved.size)
    }

    // 保存したアラームを一覧から開くと、保存した時刻・間隔が復元されることを保証する
    @Test
    fun 保存したアラームを開くと値が復元される() {
        val id = runBlocking { repository.add(defaultTestSchedule()) }
        composeTestRule.setContent { ListEditHost(repository) }

        composeTestRule.onNodeWithText("5分ごと · 25回").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText(context().getString(R.string.edit_title_existing)), 5_000)

        // TimeCardはラベルと値をマージした1ノードになる(「開始」「7:00」がまとめて1つのText)
        composeTestRule.onNodeWithText("7:00").assertExists()
        composeTestRule.onNodeWithText("9:00").assertExists()
        composeTestRule.onNodeWithText("5分").assertIsOn()

        val restored = runBlocking { repository.getById(id) }
        org.junit.Assert.assertEquals(5, restored?.intervalMinutes)
    }

    private fun context() = composeTestRule.activity
}
