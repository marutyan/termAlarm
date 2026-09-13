package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.ui.alarmedit.AlarmEditScreen
import com.marutyan.termalarm.ui.alarmedit.AlarmEditViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ターム編集画面(AlarmEditScreen)単体の振る舞いを保証するUIテスト。
 * 間隔設定シートでの回数プレビュー追従・入力検証・ラベル設定と保存を検証する。
 */
@OptIn(ExperimentalTestApi::class)
class AlarmEditScreenTest {
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

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    // 間隔設定シートを開き、間隔チップを変えると「全N回」のプレビューが追従することを保証する
    @Test
    fun 間隔を変えると回数プレビューが追従する() {
        composeTestRule.setContent {
            AlarmEditScreen(
                viewModel = remember { AlarmEditViewModel(repository, testAppContext(), null) },
                onClose = {},
            )
        }
        // 間隔行をタップして間隔設定シートを開く
        composeTestRule.onNodeWithText(string(R.string.term_edit_interval_label)).performClick()
        composeTestRule.onNodeWithText("全25回").assertExists()

        // 10分チップをタップすると全13回に更新される
        composeTestRule.onNodeWithText("10").performClick()
        composeTestRule.onNodeWithText("全13回").assertExists()
    }

    // 不正な間隔(0分や150分)は1〜120分の安全な範囲に丸められ、保存されることを保証する
    @Test
    fun 不正な間隔は選べず保存されない() {
        lateinit var viewModel: AlarmEditViewModel
        composeTestRule.setContent {
            val created = remember { AlarmEditViewModel(repository, testAppContext(), null) }
            viewModel = created
            AlarmEditScreen(viewModel = created, onClose = {})
        }

        // 範囲外の値(0分)を指定しようとしても最小値1分に丸められる
        composeTestRule.runOnIdle {
            viewModel.setCustomInterval(0)
        }
        assertEquals(1, viewModel.uiState.startIntervalMinutes)

        // 範囲外の値(150分)を指定しようとしても最大値120分に丸められる
        composeTestRule.runOnIdle {
            viewModel.setCustomInterval(150)
        }
        assertEquals(120, viewModel.uiState.startIntervalMinutes)

        // 保存すると120分として保存される
        composeTestRule.onNodeWithText(string(R.string.save)).performClick()
        composeTestRule.waitUntil(5_000) { viewModel.uiState.isSaved }

        val count = runBlocking { repository.observeAll().first().size }
        assertEquals(1, count)
        val savedSchedule = runBlocking { repository.observeAll().first().first() }
        assertEquals(120, savedSchedule.startIntervalMinutes)
    }

    // ラベルを設定して保存するとリポジトリへ反映されることを保証する
    @Test
    fun ラベルを設定して保存すると復元される() {
        lateinit var newViewModel: AlarmEditViewModel
        composeTestRule.setContent {
            newViewModel = remember { AlarmEditViewModel(repository, testAppContext(), null) }
            AlarmEditScreen(viewModel = newViewModel, onClose = {})
        }
        newViewModel.setLabel("朝のアラーム")
        composeTestRule.onNodeWithText(string(R.string.save)).performClick()
        composeTestRule.waitUntil(5_000) { newViewModel.uiState.isSaved }

        val savedSchedule = runBlocking { repository.observeAll().first().first() }
        assertEquals("朝のアラーム", savedSchedule.label)
    }
}
