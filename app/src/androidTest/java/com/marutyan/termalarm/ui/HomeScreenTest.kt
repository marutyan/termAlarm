package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ホーム画面(HomeScreen)の主要操作と画面要素を保証するUIテスト。
 * ターム追加、一覧への反映、編集シートへの復元、空状態の表示を検証する。
 */
@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @get:Rule
    val screenWakeRule = ScreenWakeRule()

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
    }

    // タームが1件も無いとき、「タームを追加」ボタンが表示されることを保証する
    @Test
    fun タームが無いときタームを追加ボタンが表示される() {
        composeTestRule.setContent { ListEditHost(repository) }
        composeTestRule.onNodeWithText(context().getString(R.string.home_add_term)).assertExists()
    }

    // 「タームを追加」を押すと編集シートが開き、保存するとホームに1件現れ、要約と時刻範囲が表示されることを保証する
    @Test
    fun タームを追加して保存するとホームに反映される() {
        composeTestRule.setContent { ListEditHost(repository) }

        composeTestRule.onNodeWithText(context().getString(R.string.home_add_term)).performClick()
        // 編集シートの保存ボタンが表示されるまで待つ
        composeTestRule.waitUntilAtLeastOneExists(hasText(context().getString(R.string.save)), 5_000)

        composeTestRule.onNodeWithText(context().getString(R.string.save)).performClick()

        // 保存後はホーム画面へ戻り、カードの要約と時刻範囲が出るまで待つ
        val summary = "5分ごと · 25回"
        composeTestRule.waitUntilAtLeastOneExists(hasText(summary), 5_000)
        composeTestRule.onNodeWithText("7:00 \u2013 9:00").assertExists()

        val saved = runBlocking { repository.observeAll().first() }
        assertEquals(1, saved.size)
    }

    // 保存したタームをホームから開くと、値が復元されることを保証する
    @Test
    fun 保存したタームを開くと値が復元される() {
        val id = runBlocking { repository.add(defaultTestSchedule()) }
        composeTestRule.setContent { ListEditHost(repository) }

        composeTestRule.onNodeWithText("5分ごと · 25回", useUnmergedTree = true).performClick()
        // 編集シートの保存ボタンが表示されるまで待つ
        composeTestRule.waitUntilAtLeastOneExists(hasText(context().getString(R.string.save)), 5_000)

        composeTestRule.onNodeWithText("7:00").assertExists()
        composeTestRule.onNodeWithText("9:00").assertExists()
        composeTestRule.onNodeWithText(context().getString(R.string.term_edit_interval_constant_summary, 5)).assertExists()

        val restored = runBlocking { repository.getById(id) }
        assertEquals(5, restored?.startIntervalMinutes)
    }

    // 「このタームを終了」を押すと確認ダイアログが開き、終了するとskippedSessionStartが設定されることを保証する
    @Test
    fun このタームを終了で確認が開き実行すると鳴らなくなる() {
        val now = java.time.ZonedDateTime.now()
        val minuteOfDay = now.hour * 60 + now.minute
        val startMinutes = (minuteOfDay - 10).coerceAtLeast(0)
        val endMinutes = (minuteOfDay + 60).coerceAtMost(1439)
        val id = runBlocking {
            repository.add(
                defaultTestSchedule(
                    startMinutes = startMinutes,
                    endMinutes = endMinutes,
                    intervalMinutes = 5,
                ),
            )
        }

        composeTestRule.setContent { ListEditHost(repository) }

        // 「このタームを終了」ボタンを押す
        composeTestRule.waitUntilAtLeastOneExists(hasText(context().getString(R.string.home_end_term)), 5_000)
        composeTestRule.onNodeWithText(context().getString(R.string.home_end_term)).performClick()

        // ターム終了確認ダイアログの見出しが表示される
        composeTestRule.waitUntilAtLeastOneExists(hasText(context().getString(R.string.term_end_title)), 5_000)

        // 「終了する」を押す
        composeTestRule.onNodeWithText(context().getString(R.string.term_end_confirm)).performClick()

        // リポジトリのskippedSessionStartが設定されたことを確認
        composeTestRule.waitUntil(5_000) {
            val schedule = runBlocking { repository.getById(id) }
            schedule?.skippedSessionStart != null
        }
        val updated = runBlocking { repository.getById(id) }
        assertEquals(now.toLocalDate(), updated?.skippedSessionStart)
    }

    private fun context() = composeTestRule.activity
}
