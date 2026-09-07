package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.ClockSettingsRepository
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.domain.ClockDisplayMode
import com.marutyan.termalarm.domain.VolumeButtonAction
import com.marutyan.termalarm.domain.WeekStart
import com.marutyan.termalarm.ui.settings.SettingsScreen
import com.marutyan.termalarm.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 設定画面(SettingsScreen)のUI表示と設定変更の保存を保証する。
 * 画面内の13項目すべてが表示され、ダイアログやスイッチでの変更が画面表示と各Repositoryに正しく永続化されることを検証する。
 */
@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    // 端末がスリープしていてもテストが動くようにする
    @get:Rule
    val screenWakeRule = ScreenWakeRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AlarmDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var clockRepository: ClockSettingsRepository

    /**
     * テストごとに独立したインメモリDBと各Repositoryを初期化する。
     * 前のテストの保存データが次のテストに影響しないようにする。
     */
    @Before
    fun setUp() {
        val (database, settingsRepo, clockRepo) = createTestSettingsRepositories()
        db = database
        settingsRepository = settingsRepo
        clockRepository = clockRepo
    }

    /**
     * テスト終了後の後始末。
     *
     * インメモリDBは閉じない。画面が持つViewModelは、テストが終わった後も
     * 保存の処理を続けていることがあり、閉じた先へ書きに行って落ちるため。
     * テストごとに新しいインスタンスを作っているので、閉じなくても値は混ざらない。
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
     * 設定画面をテストルール上にセットアップする。
     * テスト対象のSettingsViewModelをインメモリDBに接続した状態で画面を組み立てる。
     */
    private fun setScreen(onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            SettingsScreen(
                viewModel = remember { SettingsViewModel(settingsRepository, clockRepository) },
                onBack = onBack,
            )
        }
    }

    // 設定画面の3セクションに含まれる全13項目が表示されることを保証する
    @Test
    fun 設定の13項目が表示される() {
        setScreen()

        // セクション見出し
        composeTestRule.onNodeWithText(string(R.string.settings_section_alarm)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_section_clock)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_section_timer)).assertExists()

        // アラームセクションの項目
        composeTestRule.onNodeWithText(string(R.string.settings_dismiss_method_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_auto_stop_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_snooze_length_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_volume_button_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_week_start_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_volume_title)).assertExists()

        // 時計セクションの項目
        composeTestRule.onNodeWithText(string(R.string.settings_clock_style_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_show_seconds_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_date_time_title)).assertExists()

        // タイマーセクションの項目
        composeTestRule.onNodeWithText(string(R.string.settings_timer_sound_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_timer_vibration_title)).assertExists()

        // 「徐々に音量を上げる」はアラームとタイマーの2箇所に表示されるため合計2件存在することを確認する
        composeTestRule.onAllNodesWithText(string(R.string.settings_fade_in_title)).assertCountEquals(2)
    }

    // 消音までの時間のダイアログを開いて値を選択すると画面とRepositoryに保存されることを保証する
    @Test
    fun 消音までの時間を変更すると画面とRepositoryに反映される() {
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.settings_auto_stop_title)).performClick()
        composeTestRule.onNode(hasText("5分") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().autoStopMinutes == 5 }
        }

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(5, saved.autoStopMinutes)
        composeTestRule.onNode(hasText(string(R.string.settings_auto_stop_title)) and hasText("5分")).assertExists()
    }

    // スヌーズの長さのダイアログを開いて値を選択すると画面とRepositoryに保存されることを保証する
    @Test
    fun スヌーズの長さを変更すると画面とRepositoryに反映される() {
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.settings_snooze_length_title)).performClick()
        composeTestRule.onNode(hasText("10分") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().defaultSnoozeMinutes == 10 }
        }

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(10, saved.defaultSnoozeMinutes)
        composeTestRule.onNode(hasText(string(R.string.settings_snooze_length_title)) and hasText("10分")).assertExists()
    }

    // 音量ボタン動作のダイアログを開いて選択すると画面とRepositoryに保存されることを保証する
    @Test
    fun アラーム時の音量ボタンを変更すると画面とRepositoryに反映される() {
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.settings_volume_button_title)).performClick()
        val snoozeOption = string(R.string.settings_volume_button_snooze)
        composeTestRule.onNode(hasText(snoozeOption) and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().volumeButtonAction == VolumeButtonAction.SNOOZE }
        }

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(VolumeButtonAction.SNOOZE, saved.volumeButtonAction)
        composeTestRule.onNodeWithText(snoozeOption).assertExists()
    }

    // 週の始まりダイアログを開いて選択すると画面とRepositoryに保存されることを保証する
    @Test
    fun 週の始まりを変更すると画面とRepositoryに反映される() {
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.settings_week_start_title)).performClick()
        val mondayOption = string(R.string.settings_week_start_monday)
        composeTestRule.onNode(hasText(mondayOption) and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().weekStart == WeekStart.MONDAY }
        }

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(WeekStart.MONDAY, saved.weekStart)
        composeTestRule.onNodeWithText(mondayOption).assertExists()
    }

    // アラームの音量徐々に増加ダイアログを開いて選択すると画面とRepositoryに保存されることを保証する
    @Test
    fun アラームの徐々に音量を上げるを変更すると画面とRepositoryに反映される() {
        setScreen()

        // アラームセクションのフェードイン行(1つ目の「徐々に音量を上げる」)をタップする
        composeTestRule.onAllNodesWithText(string(R.string.settings_fade_in_title))[0].performClick()
        composeTestRule.onNode(hasText("10秒") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().alarmFadeInSeconds == 10 }
        }

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(10, saved.alarmFadeInSeconds)
        composeTestRule.onNodeWithText("10秒").assertExists()
    }

    // 時計スタイルダイアログを開いて選択すると画面とClockSettingsRepositoryに保存されることを保証する
    @Test
    fun 時計スタイルを変更すると画面とClockSettingsRepositoryに反映される() {
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.settings_clock_style_title)).performScrollTo().performClick()
        val analogOption = string(R.string.clock_display_mode_analog)
        composeTestRule.onNode(hasText(analogOption) and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { clockRepository.observeDisplayMode().first() == ClockDisplayMode.ANALOG }
        }

        val saved = runBlocking { clockRepository.observeDisplayMode().first() }
        assertEquals(ClockDisplayMode.ANALOG, saved)
        composeTestRule.onNodeWithText(analogOption).assertExists()
    }

    // 「時刻に秒を表示」行をタップするとSwitchが切り替わりRepositoryに保存されることを保証する
    @Test
    fun 時刻に秒を表示のSwitchを切り替えるとRepositoryに反映される() {
        setScreen()

        // 既定値はtrueのため、タップするとfalse(OFF)へ切り替わることを確認する
        composeTestRule.onNodeWithText(string(R.string.settings_show_seconds_title)).performScrollTo().performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { !settingsRepository.observe().first().showClockSeconds }
        }

        val savedOff = runBlocking { settingsRepository.observe().first() }
        assertTrue("時刻に秒を表示がfalseになること", !savedOff.showClockSeconds)

        // もう一度タップしてtrue(ON)に戻ることを確認する
        composeTestRule.onNodeWithText(string(R.string.settings_show_seconds_title)).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().showClockSeconds }
        }

        val savedOn = runBlocking { settingsRepository.observe().first() }
        assertTrue("時刻に秒を表示がtrueに戻ること", savedOn.showClockSeconds)
    }

    // タイマーの音量徐々に増加ダイアログを開いて選択すると画面とRepositoryに保存されることを保証する
    @Test
    fun タイマーの徐々に音量を上げるを変更すると画面とRepositoryに反映される() {
        setScreen()

        // タイマーセクションのフェードイン行(2つ目の「徐々に音量を上げる」)までスクロールしてタップする
        composeTestRule.onAllNodesWithText(string(R.string.settings_fade_in_title))[1].performScrollTo().performClick()
        composeTestRule.onNode(hasText("3秒") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().timerFadeInSeconds == 3.0f }
        }

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(3.0f, saved.timerFadeInSeconds, 0.01f)
    }

    // 「タイマーのバイブレーション」行をタップするとSwitchが切り替わりRepositoryに保存されることを保証する
    @Test
    fun タイマーのバイブレーションのSwitchを切り替えるとRepositoryに反映される() {
        setScreen()

        // 初期値はtrue。行をタップしてOFFにする
        composeTestRule.onNodeWithText(string(R.string.settings_timer_vibration_title)).performScrollTo().performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { !settingsRepository.observe().first().timerVibration }
        }

        val savedOff = runBlocking { settingsRepository.observe().first() }
        assertTrue("タイマーのバイブレーションがfalseになること", !savedOff.timerVibration)
    }

    // TopAppBarの戻るボタンを押したときにonBackコールバックが呼ばれることを保証する
    @Test
    fun 戻るボタンを押すとコールバックが呼ばれる() {
        var backCalled = false
        setScreen(onBack = { backCalled = true })

        val title = string(R.string.settings_title)
        composeTestRule.onNode(hasClickAction() and hasAnySibling(hasAnyDescendant(hasText(title)))).performClick()

        assertTrue("戻るボタンタップでonBackが呼ばれること", backCalled)
    }
}
