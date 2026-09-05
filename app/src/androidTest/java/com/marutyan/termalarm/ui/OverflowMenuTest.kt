package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.data.ClockSettingsRepository
import com.marutyan.termalarm.data.StopwatchRepository
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.ui.alarmlist.AlarmListScreen
import com.marutyan.termalarm.ui.alarmlist.AlarmListViewModel
import com.marutyan.termalarm.ui.clock.ClockScreen
import com.marutyan.termalarm.ui.clock.ClockViewModel
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
 * 各タブ共通の「⋮」オーバーフローメニュー(TermAlarmOverflowMenu)のUI表示と画面遷移を保証する。
 * アラーム・時計・タイマー・ストップウォッチの全4タブにおいて3項目(設定・プライバシーポリシー・ライセンス)が表示され、
 * 各項目から対応する画面への遷移イベントが通知されることを検証する。
 */
@OptIn(ExperimentalTestApi::class)
class OverflowMenuTest {
    // 端末がスリープしていてもテストが動くようにする
    @get:Rule
    val screenWakeRule = ScreenWakeRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AlarmDatabase
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var clockRepository: ClockSettingsRepository
    private lateinit var timerRepository: TimerRepository
    private lateinit var stopwatchRepository: StopwatchRepository

    /**
     * テストごとに独立したインメモリDBと各タブ用のRepository群を初期化する。
     * 前のテストの保存データが次のテストに影響しないようにする。
     */
    @Before
    fun setUp() {
        val (database, alarmRepo) = createTestRepository()
        db = database
        alarmRepository = alarmRepo
        clockRepository = ClockSettingsRepository(db.clockSettingsDao())
        timerRepository = TimerRepository(db.timerDao())
        stopwatchRepository = StopwatchRepository(db.stopwatchDao())
    }

    /**
     * テスト終了後にインメモリDBをクローズしてリソースを解放する。
     * 後続テストとのリソース競合を防ぐ。
     */
    @After
    fun tearDown() {
        db.close()
    }

    /**
     * リソースIDから文字列を取得するヘルパー。
     * テストコード内での文字列リソース参照を簡潔にする。
     */
    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    /**
     * 「⋮」メニューを開き、設定・プライバシーポリシー・ライセンスの3項目が表示されていることを検証する。
     * 全4タブで共通するメニュー項目の存在確認処理を共通化する。
     */
    private fun assertThreeMenuItemsExist() {
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_settings)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.menu_privacy_policy)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.menu_license)).assertExists()
    }

    // アラームタブで「⋮」メニューを開くと3項目(設定・プライバシーポリシー・ライセンス)が表示されることを保証する
    @Test
    fun アラームタブでメニューを開くと3項目が表示される() {
        composeTestRule.setContent {
            AlarmListScreen(
                viewModel = remember { AlarmListViewModel(alarmRepository, testAppContext()) },
                onAddAlarm = {},
                onEditAlarm = {},
                onOpenAbout = {},
                onOpenPrivacyPolicy = {},
                onOpenSettings = {},
                onNavigateToSkipGame = {},
                exactAlarmBanner = {},
                notificationPermissionBanner = {},
            )
        }
        assertThreeMenuItemsExist()
    }

    // アラームタブの「⋮」メニューの各項目から対応する画面への遷移コールバックが呼ばれることを保証する
    @Test
    fun アラームタブのメニューから各項目へ遷移できる() {
        var openedSettings = false
        var openedPrivacy = false
        var openedAbout = false

        composeTestRule.setContent {
            AlarmListScreen(
                viewModel = remember { AlarmListViewModel(alarmRepository, testAppContext()) },
                onAddAlarm = {},
                onEditAlarm = {},
                onOpenAbout = { openedAbout = true },
                onOpenPrivacyPolicy = { openedPrivacy = true },
                onOpenSettings = { openedSettings = true },
                onNavigateToSkipGame = {},
                exactAlarmBanner = {},
                notificationPermissionBanner = {},
            )
        }

        // 設定へ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_settings)).performClick()
        assertTrue("設定コールバックが呼ばれること", openedSettings)

        // プライバシーポリシーへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_privacy_policy)).performClick()
        assertTrue("プライバシーポリシーコールバックが呼ばれること", openedPrivacy)

        // ライセンスへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_license)).performClick()
        assertTrue("ライセンスコールバックが呼ばれること", openedAbout)
    }

    // 時計タブで「⋮」メニューを開くと3項目が表示されることを保証する
    @Test
    fun 時計タブでメニューを開くと3項目が表示される() {
        composeTestRule.setContent {
            ClockScreen(
                viewModel = remember { ClockViewModel(clockRepository) },
                bottomBar = {},
            )
        }
        assertThreeMenuItemsExist()
    }

    // 時計タブの「⋮」メニューの各項目から対応する画面への遷移コールバックが呼ばれることを保証する
    @Test
    fun 時計タブのメニューから各項目へ遷移できる() {
        var openedSettings = false
        var openedPrivacy = false
        var openedAbout = false

        composeTestRule.setContent {
            ClockScreen(
                viewModel = remember { ClockViewModel(clockRepository) },
                onOpenSettings = { openedSettings = true },
                onOpenPrivacyPolicy = { openedPrivacy = true },
                onOpenAbout = { openedAbout = true },
                bottomBar = {},
            )
        }

        // 設定へ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_settings)).performClick()
        assertTrue("設定コールバックが呼ばれること", openedSettings)

        // プライバシーポリシーへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_privacy_policy)).performClick()
        assertTrue("プライバシーポリシーコールバックが呼ばれること", openedPrivacy)

        // ライセンスへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_license)).performClick()
        assertTrue("ライセンスコールバックが呼ばれること", openedAbout)
    }

    // タイマータブで「⋮」メニューを開くと3項目が表示されることを保証する
    @Test
    fun タイマータブでメニューを開くと3項目が表示される() {
        composeTestRule.setContent {
            TimerScreen(
                viewModel = remember { TimerViewModel(timerRepository, testAppContext()) },
                bottomBar = {},
            )
        }
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
                bottomBar = {},
            )
        }

        // 設定へ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_settings)).performClick()
        assertTrue("設定コールバックが呼ばれること", openedSettings)

        // プライバシーポリシーへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_privacy_policy)).performClick()
        assertTrue("プライバシーポリシーコールバックが呼ばれること", openedPrivacy)

        // ライセンスへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_license)).performClick()
        assertTrue("ライセンスコールバックが呼ばれること", openedAbout)
    }

    // ストップウォッチタブで「⋮」メニューを開くと3項目が表示されることを保証する
    @Test
    fun ストップウォッチタブでメニューを開くと3項目が表示される() {
        composeTestRule.setContent {
            StopwatchScreen(
                viewModel = remember { StopwatchViewModel(stopwatchRepository, testAppContext()) },
                bottomBar = {},
            )
        }
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
                bottomBar = {},
            )
        }

        // 設定へ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_settings)).performClick()
        assertTrue("設定コールバックが呼ばれること", openedSettings)

        // プライバシーポリシーへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_privacy_policy)).performClick()
        assertTrue("プライバシーポリシーコールバックが呼ばれること", openedPrivacy)

        // ライセンスへ遷移
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_more)).performClick()
        composeTestRule.onNodeWithText(string(R.string.menu_license)).performClick()
        assertTrue("ライセンスコールバックが呼ばれること", openedAbout)
    }
}
