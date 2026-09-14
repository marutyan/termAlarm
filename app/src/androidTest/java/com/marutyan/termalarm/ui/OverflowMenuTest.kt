package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.alarm.AlarmSchedulerStore
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.data.StopwatchRepository
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.data.WakeRecordRepository
import com.marutyan.termalarm.ui.alarms.AlarmsScreen
import com.marutyan.termalarm.ui.alarms.AlarmsViewModel
import com.marutyan.termalarm.ui.home.HomeScreen
import com.marutyan.termalarm.ui.home.HomeViewModel
import com.marutyan.termalarm.ui.navigation.NavItem
import com.marutyan.termalarm.ui.navigation.TermAlarmBottomBar
import com.marutyan.termalarm.ui.records.RecordsScreen
import com.marutyan.termalarm.ui.records.RecordsViewModel
import com.marutyan.termalarm.ui.stopwatch.StopwatchScreen
import com.marutyan.termalarm.ui.stopwatch.StopwatchViewModel
import com.marutyan.termalarm.ui.timer.TimerScreen
import com.marutyan.termalarm.ui.timer.TimerViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 画面上部の帯に配置されるオーバーフローメニュー(TermAlarmTopBar / TermAlarmOverflowMenu)の表示と遷移、
 * および左ナビゲーションから設定項目が除外されていることを検証するUIテスト。
 */
@OptIn(ExperimentalTestApi::class)
class OverflowMenuTest {
    // 端末がスリープしていてもテストが動くようにする
    @get:Rule
    val screenWakeRule = ScreenWakeRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AlarmDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var timerRepository: TimerRepository
    private lateinit var stopwatchRepository: StopwatchRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var wakeRecordRepository: WakeRecordRepository

    /**
     * テストごとに独立したインメモリDBと各画面用のRepository群を初期化する。
     * 前のテストの保存データが次のテストに影響しないようにする。
     */
    @Before
    fun setUp() {
        val (database, repo) = createTestRepository()
        db = database
        repository = repo
        timerRepository = TimerRepository(db.timerDao())
        stopwatchRepository = StopwatchRepository(db.stopwatchDao())
        settingsRepository = SettingsRepository(db.appSettingsDao())
        wakeRecordRepository = WakeRecordRepository(db.ringRecordDao())
    }

    /**
     * テスト終了後の後始末。
     */
    @After
    fun tearDown() {
    }

    /**
     * リソースIDから文字列を取得するヘルパー。
     * テストコード内での文字列リソース参照を簡潔にする。
     */
    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    /**
     * 「⋮」メニューを開き、設定・プライバシー・このアプリについての3項目が表示されていることを検証する。
     * 主要5画面で共通するメニュー項目の存在確認処理を共通化する。
     */
    private fun assertThreeMenuItemsExist() {
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_settings)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.menu_privacy)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.menu_about)).assertExists()
    }

    // 下の帯に設定項目が存在せず、主要5機能のみが表示されることを検証する
    @Test
    fun 下の帯に設定項目がなく5項目のみ表示される() {
        composeTestRule.setContent {
            TermAlarmBottomBar(
                selectedItem = NavItem.TERMS,
                onSelectItem = {},
            )
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.nav_terms)).assertExists()
        composeTestRule.onNodeWithContentDescription(string(R.string.nav_standard_alarm)).assertExists()
        composeTestRule.onNodeWithContentDescription(string(R.string.nav_record)).assertExists()
        composeTestRule.onNodeWithContentDescription(string(R.string.nav_timer)).assertExists()
        composeTestRule.onNodeWithContentDescription(string(R.string.nav_stopwatch)).assertExists()
        composeTestRule.onNodeWithContentDescription(string(R.string.nav_settings)).assertDoesNotExist()
    }

    // ターム（ホーム）画面で上部の帯と三点メニューが表示されることを検証する
    @Test
    fun ターム画面でメニューを開くと3項目が表示される() {
        composeTestRule.setContent {
            HomeScreen(
                viewModel = remember { HomeViewModel(repository, AlarmSchedulerStore(testAppContext())) },
            )
        }
        composeTestRule.onNodeWithText(string(R.string.app_name)).assertExists()
        assertThreeMenuItemsExist()
    }

    // ターム（ホーム）画面の三点メニューから各項目へ遷移できることを検証する
    @Test
    fun ターム画面のメニューから各項目へ遷移できる() {
        var openedSettings = false
        var openedPrivacy = false
        var openedAbout = false

        composeTestRule.setContent {
            HomeScreen(
                viewModel = remember { HomeViewModel(repository, AlarmSchedulerStore(testAppContext())) },
                onOpenSettings = { openedSettings = true },
                onOpenPrivacyPolicy = { openedPrivacy = true },
                onOpenAbout = { openedAbout = true },
            )
        }

        // 設定へ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_settings)).performClick()
        assertTrue("設定コールバックが呼ばれること", openedSettings)

        // プライバシーへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_privacy)).performClick()
        assertTrue("プライバシーコールバックが呼ばれること", openedPrivacy)

        // このアプリについてへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_about)).performClick()
        assertTrue("このアプリについてコールバックが呼ばれること", openedAbout)
    }

    // 通常アラーム画面で上部の帯と三点メニューが表示されることを検証する
    @Test
    fun 通常アラーム画面でメニューを開くと3項目が表示される() {
        composeTestRule.setContent {
            AlarmsScreen(
                viewModel = remember { AlarmsViewModel(repository, AlarmSchedulerStore(testAppContext())) },
                onAddAlarm = {},
                onEditAlarm = {},
            )
        }
        composeTestRule.onNodeWithText(string(R.string.app_name)).assertExists()
        assertThreeMenuItemsExist()
    }

    // 記録画面で上部の帯と三点メニューが表示されることを検証する
    @Test
    fun 記録画面でメニューを開くと3項目が表示される() {
        composeTestRule.setContent {
            RecordsScreen(
                viewModel = remember { RecordsViewModel(wakeRecordRepository, repository) },
            )
        }
        composeTestRule.onNodeWithText(string(R.string.app_name)).assertExists()
        assertThreeMenuItemsExist()
    }

    // タイマータブで「⋮」メニューを開くと3項目が表示されることを保証する
    @Test
    fun タイマータブでメニューを開くと3項目が表示される() {
        composeTestRule.setContent {
            TimerScreen(
                viewModel = remember { TimerViewModel(timerRepository, testAppContext()) },
            )
        }
        composeTestRule.onNodeWithText(string(R.string.app_name)).assertExists()
        assertThreeMenuItemsExist()
    }

    // タイマータブの「⋮」メニューの各項目から対応する画面への遷移コールバックが呼ばれることを保証する
    @Test
    fun タイマータブのメニューから各項目へ遷移できる() {
        var openedSettings = false
        var openedPrivacy = false
        var openedAbout = false

        composeTestRule.setContent {
            TimerScreen(
                viewModel = remember { TimerViewModel(timerRepository, testAppContext()) },
                onOpenSettings = { openedSettings = true },
                onOpenPrivacyPolicy = { openedPrivacy = true },
                onOpenAbout = { openedAbout = true },
            )
        }

        // 設定へ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_settings)).performClick()
        assertTrue("設定コールバックが呼ばれること", openedSettings)

        // プライバシーへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_privacy)).performClick()
        assertTrue("プライバシーコールバックが呼ばれること", openedPrivacy)

        // このアプリについてへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_about)).performClick()
        assertTrue("このアプリについてコールバックが呼ばれること", openedAbout)
    }

    // ストップウォッチタブで「⋮」メニューを開くと3項目が表示されることを保証する
    @Test
    fun ストップウォッチタブでメニューを開くと3項目が表示される() {
        composeTestRule.setContent {
            StopwatchScreen(
                viewModel = remember { StopwatchViewModel(stopwatchRepository, testAppContext()) },
            )
        }
        composeTestRule.onNodeWithText(string(R.string.app_name)).assertExists()
        assertThreeMenuItemsExist()
    }

    // ストップウォッチタブの「⋮」メニューの各項目から対応する画面への遷移コールバックが呼ばれることを保証する
    @Test
    fun ストップウォッチタブのメニューから各項目へ遷移できる() {
        var openedSettings = false
        var openedPrivacy = false
        var openedAbout = false

        composeTestRule.setContent {
            StopwatchScreen(
                viewModel = remember { StopwatchViewModel(stopwatchRepository, testAppContext()) },
                onOpenSettings = { openedSettings = true },
                onOpenPrivacyPolicy = { openedPrivacy = true },
                onOpenAbout = { openedAbout = true },
            )
        }

        // 設定へ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_settings)).performClick()
        assertTrue("設定コールバックが呼ばれること", openedSettings)

        // プライバシーへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_privacy)).performClick()
        assertTrue("プライバシーコールバックが呼ばれること", openedPrivacy)

        // このアプリについてへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_about)).performClick()
        assertTrue("このアプリについてコールバックが呼ばれること", openedAbout)
    }
}
