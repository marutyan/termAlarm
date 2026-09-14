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
import com.marutyan.termalarm.data.SettingsRepository
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

    @Before
    fun setUp() {
        val (database, settings) = createTestSettingsRepository()
        db = database
        settingsRepository = settings
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
                viewModel = remember { SettingsViewModel(settingsRepository) },
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
        composeTestRule.onNodeWithText(string(R.string.settings_section_appearance)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_section_about_app)).assertExists()

        // アラームセクションの項目
        composeTestRule.onNodeWithText(string(R.string.settings_sound_item_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.vibration_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_fade_in_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_silence_after_item_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_wake_check_item_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_mini_games_item_title)).assertExists()

        // 見た目・このアプリセクションの項目
        composeTestRule.onNodeWithText(string(R.string.settings_theme_item_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_privacy_item_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.settings_about_item_title)).assertExists()
    }

    // 消音までの時間のダイアログを開いて値を選択し、決定を押すと画面とRepositoryに保存されることを保証する
    @Test
    fun 消音までの時間を変更すると画面とRepositoryに反映される() {
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.settings_silence_after_item_title)).performClick()
        composeTestRule.onNode(hasText("5分") and hasAnyAncestor(isDialog())).performClick()
        composeTestRule.onNode(hasText(string(R.string.decide)) and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().silenceAfterMinutes == 5 }
        }

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(5, saved.silenceAfterMinutes)
    }

    // 消音までの時間のダイアログで値を選んでもキャンセルした場合は変更されないことを保証する
    @Test
    fun 消音までの時間でキャンセルを押すと値は変更されない() {
        setScreen()

        val initialMinutes = runBlocking { settingsRepository.observe().first().silenceAfterMinutes }

        composeTestRule.onNodeWithText(string(R.string.settings_silence_after_item_title)).performClick()
        composeTestRule.onNode(hasText("5分") and hasAnyAncestor(isDialog())).performClick()
        composeTestRule.onNode(hasText(string(R.string.cancel)) and hasAnyAncestor(isDialog())).performClick()

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(initialMinutes, saved.silenceAfterMinutes)
    }

    // アラームの音量徐々に増加ダイアログを開いて選択し、決定を押すと画面とRepositoryに保存されることを保証する
    @Test
    fun アラームの徐々に音量を上げるを変更すると画面とRepositoryに反映される() {
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.settings_fade_in_title)).performClick()
        composeTestRule.onNode(hasText("10秒") and hasAnyAncestor(isDialog())).performClick()
        composeTestRule.onNode(hasText(string(R.string.decide)) and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { settingsRepository.observe().first().fadeInSeconds == 10 }
        }

        val saved = runBlocking { settingsRepository.observe().first() }
        assertEquals(10, saved.fadeInSeconds)
        composeTestRule.onNodeWithText("10秒").assertExists()
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
}
