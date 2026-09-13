package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // ホームの時刻が、ステータスバーの下端から74dp以上空いていることを保証する
    @Test
    fun ホームの時刻がステータスバーの下端から74dp空いている() {
        composeTestRule.setContent { ListEditHost(repository) }

        // 現在時刻のノードを取得
        val now = java.time.LocalTime.now()
        val expectedTime = "${now.hour}:${now.minute.toString().padStart(2, '0')}"
        val clockNode = composeTestRule.onNodeWithText(expectedTime)
        clockNode.assertExists()
        val rootBounds = composeTestRule.onRoot().getBoundsInRoot()
        val clockBounds = clockNode.getBoundsInRoot()
        val topOffset = (clockBounds.top - rootBounds.top).value
        // ステータスバー(通常24dp以上)+74dp = 最低74dp以上空いていることを保証
        assertTrue("ホームの時刻の上余白($topOffset dp)が74dp以上であること", topOffset >= 74f)
    }

    // 開始と終了が同じ通常アラームはホーム一覧に出ないことを保証する
    @Test
    fun 通常アラームはホーム画面に表示されない() {
        val singleAlarm = defaultTestSchedule().copy(
            id = 0L,
            startMinutes = 8 * 60,
            endMinutes = 8 * 60,
            label = "通常アラームラベル",
        )
        runBlocking { repository.add(singleAlarm) }

        composeTestRule.setContent { ListEditHost(repository) }
        composeTestRule.onNodeWithText("通常アラームラベル").assertDoesNotExist()
    }

    private fun context() = composeTestRule.activity
}
