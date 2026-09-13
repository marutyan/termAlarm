package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyAncestor
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

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AlarmDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var clockRepository: ClockSettingsRepository

    @Before
    fun setUp() {
        val (database, settings, clock) = createTestSettingsRepositories()
        db = database
        settingsRepository = settings
        clockRepository = clock
    }

    @After
    fun tearDown() {
        db.close()
    }

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

    // 設定画面の項目が表示されることを保証する
    @Test
    fun 設定の項目が表示される() {
        setScreen()

        // セクション見出し
        composeTestRule.onNodeWithText(string(R.string.settings_section_alarm)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_section_clock)).assertExists()

        // アラームセクションの項目
        composeTestRule.onNodeWithText(string(R.string.sound_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.vibration_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_volume_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_fade_in_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_auto_stop_title)).assertExists()

        // 時計セクションの項目
        composeTestRule.onNodeWithText(string(R.string.settings_clock_style_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_date_time_title)).assertExists()
    }

    // 消音までの時間のダイアログを開いて値を選択すると画面とRepositoryに保存されることを保証する
    @Test
    fun 消音までの時間を変更すると画面とRepositoryに反映される() {
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.settings_auto_stop_title)).performClick()
        composeTestRule.onNode(hasText("5分") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().silenceAfterMinutes == 5 }
        }

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(5, saved.silenceAfterMinutes)
        composeTestRule.onNode(hasText(string(R.string.settings_auto_stop_title)) and hasText("5分")).assertExists()
    }

    // アラームの音量徐々に増加ダイアログを開いて選択すると画面とRepositoryに保存されることを保証する
    @Test
    fun アラームの徐々に音量を上げるを変更すると画面とRepositoryに反映される() {
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.settings_fade_in_title)).performClick()
        composeTestRule.onNode(hasText("10秒") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().fadeInSeconds == 10 }
        }

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(10, saved.fadeInSeconds)
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

    // バイブレーション行をタップするとSwitchが切り替わりRepositoryに保存されることを保証する
    @Test
    fun バイブレーションのSwitchを切り替えるとRepositoryに反映される() {
        setScreen()

        // 初期値はtrue。行をタップしてOFFにする
        composeTestRule.onNodeWithText(string(R.string.vibration_title)).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { !settingsRepository.observe().first().vibration }
        }

        val savedOff = runBlocking { settingsRepository.observe().first() }
        assertTrue("バイブレーションがfalseになること", !savedOff.vibration)
    }

    // TopAppBarの戻るボタンを押したときにonBackコールバックが呼ばれることを保証する
    @Test
    fun 戻るボタンを押すとコールバックが呼ばれる() {
        var backCalled = false
        setScreen(onBack = { backCalled = true })

        composeTestRule.onNodeWithText(string(R.string.back)).performClick()
        assertTrue(backCalled)
    }
}
