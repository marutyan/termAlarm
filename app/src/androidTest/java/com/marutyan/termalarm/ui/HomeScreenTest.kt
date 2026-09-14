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

    /**
     * まだ開始時刻に達していないタームでも「次の鳴動」が表示され、「このタームを終了」ボタンは表示されないことを検証する。
     * セッション開始前であっても次回鳴動をホームで確認できるようにしつつ、未開始タームへの不要な終了導線を出さないことを保証する。
     */
    @Test
    fun 未開始のタームでも次の鳴動が表示されこのタームを終了は表示されない() {
        val now = java.time.ZonedDateTime.now()
        // 確実に未開始（次回鳴動が翌日以降）となるよう、翌日の曜日に設定する
        val tomorrow = now.toLocalDate().plusDays(1)
        val schedule = defaultTestSchedule(
            startMinutes = 7 * 60,
            endMinutes = 9 * 60,
            repeatDays = setOf(tomorrow.dayOfWeek),
            intervalMinutes = 5,
        )
        runBlocking { repository.add(schedule) }

        composeTestRule.setContent { ListEditHost(repository) }

        // 「次の鳴動」ラベルが表示されていること
        composeTestRule.waitUntilAtLeastOneExists(hasText(context().getString(R.string.home_next_trigger_label)), 5_000)
        composeTestRule.onNodeWithText(context().getString(R.string.home_next_trigger_label)).assertExists()

        // 次回鳴動時刻（7:00）が表示されていること
        composeTestRule.onNodeWithText("7:00").assertExists()

        // まだ始まっていないため「このタームを終了」ボタンは表示されないこと
        composeTestRule.onNodeWithText(context().getString(R.string.home_end_term)).assertDoesNotExist()
    }

    /**
     * 当日のセッション開始前のタームでも「次の鳴動」が表示され、「このタームを終了」ボタンは表示されないことを検証する。
     * 直前に控えているタームがホームで確認でき、かつ開始前には終了ボタンが出ないことを保証する。
     */
    @Test
    fun 当日未開始のタームでも次の鳴動が表示されこのタームを終了は表示されない() {
        val now = java.time.ZonedDateTime.now()
        val minuteOfDay = now.hour * 60 + now.minute
        if (minuteOfDay < 1400) {
            val startMinutes = minuteOfDay + 10
            val endMinutes = (minuteOfDay + 30).coerceAtMost(1439)
            val schedule = defaultTestSchedule(
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                repeatDays = setOf(now.dayOfWeek),
                intervalMinutes = 5,
            )
            runBlocking { repository.add(schedule) }

            composeTestRule.setContent { ListEditHost(repository) }

            composeTestRule.waitUntilAtLeastOneExists(hasText(context().getString(R.string.home_next_trigger_label)), 5_000)
            composeTestRule.onNodeWithText(context().getString(R.string.home_next_trigger_label)).assertExists()
            composeTestRule.onNodeWithText(context().getString(R.string.home_end_term)).assertDoesNotExist()
        }
    }

    /**
     * 通知権限の状態に応じたバナーの表示・非表示を検証する。
     * 未許可環境ではバナーが表示され、許可済み環境ではバナーが表示されないことを確認する。
     */
    @Test
    fun 通知権限の状態に応じてバナーが適切に表示される() {
        composeTestRule.setContent { ListEditHost(repository) }
        val bannerText = context().getString(R.string.notification_permission_banner)
        if (com.marutyan.termalarm.alarm.NotificationPermission.isGranted(context())) {
            composeTestRule.onNodeWithText(bannerText).assertDoesNotExist()
        } else {
            composeTestRule.onNodeWithText(bannerText).assertExists()
        }
    }

    /**
     * 正確なアラーム権限の状態に応じたバナーの表示・非表示を検証する。
     * 権限が揃っているときにはバナーが表示されないことを確認する。
     */
    @Test
    fun 正確なアラーム権限の状態に応じてバナーが適切に表示される() {
        composeTestRule.setContent { ListEditHost(repository) }
        val bannerText = context().getString(R.string.exact_alarm_permission_banner)
        if (com.marutyan.termalarm.alarm.ExactAlarmPermission.isGranted(context())) {
            composeTestRule.onNodeWithText(bannerText).assertDoesNotExist()
        } else {
            composeTestRule.onNodeWithText(bannerText).assertExists()
        }
    }

    private fun context() = composeTestRule.activity
}
