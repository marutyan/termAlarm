package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
 * アラーム編集画面(AlarmEditScreen)単体の振る舞いを保証する。
 * 回数プレビューの追従・入力検証・「止めにくさ」3設定の既定値と相互作用・保存後の復元を対象にする。
 * TimePickerのダイヤルは実機ジェスチャーでは不安定なため、開始・終了時刻の変更は
 * AlarmEditViewModelの公開メソッド(setStartMinutes/setEndMinutes)を直接呼ぶことで安定させる
 * (これらはTimePickerダイアログのOKボタンが最終的に呼ぶのと同じメソッドであり、
 * 検証したいのは「ViewModelの状態がPreviewBannerへ正しく反映されるか」であってダイヤル操作そのものではない)。
 */
@OptIn(ExperimentalTestApi::class)
class AlarmEditScreenTest {
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

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    // 間隔チップを変えると「N回鳴ります」のプレビューが追従することを保証する
    @Test
    fun 間隔を変えると回数プレビューが追従する() {
        composeTestRule.setContent {
            AlarmEditScreen(viewModel = AlarmEditViewModel(repository, testAppContext(), null), onClose = {})
        }
        composeTestRule.onNodeWithText("7:00 から 9:00 まで 25回 鳴ります").assertExists()

        composeTestRule.onNodeWithText("10分").performClick()
        composeTestRule.onNodeWithText("7:00 から 9:00 まで 13回 鳴ります").assertExists()
    }

    // 開始と終了が同じ時刻になると、プレビューが単発(1回)表示に退化することを保証する
    @Test
    fun 開始と終了が同じとき1回になる() {
        lateinit var viewModel: AlarmEditViewModel
        composeTestRule.setContent {
            viewModel = AlarmEditViewModel(repository, testAppContext(), null)
            AlarmEditScreen(viewModel = viewModel, onClose = {})
        }
        composeTestRule.runOnIdle {
            viewModel.setStartMinutes(7 * 60)
            viewModel.setEndMinutes(7 * 60)
        }
        composeTestRule.onNodeWithText("7:00 に 1回 鳴ります").assertExists()
    }

    // 不正な間隔(0)を入力すると保存できず、理由が表示されることを保証する
    @Test
    fun 不正な間隔は保存できず理由が表示される() {
        composeTestRule.setContent {
            AlarmEditScreen(viewModel = AlarmEditViewModel(repository, testAppContext(), null), onClose = {})
        }
        composeTestRule.onNodeWithText(string(R.string.interval_custom_label)).performClick() // 「その他」
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("0")

        composeTestRule.onNodeWithText(string(R.string.error_interval_not_positive)).assertExists()

        composeTestRule.onNodeWithText(string(R.string.save)).performClick()
        // 保存が拒否され編集画面のままであることを、新規作成タイトルがまだ表示されていることで確認する
        composeTestRule.onNodeWithText(string(R.string.edit_title_new)).assertExists()
        val count = runBlocking { repository.observeAll().first().size }
        assertEquals(0, count)
    }

    // 「止めにくさ」の既定値(skipRequiresApp=オン/skipGame=オフ/snooze=オフ)を保証する
    @Test
    fun 止めにくさの既定値() {
        composeTestRule.setContent {
            AlarmEditScreen(viewModel = AlarmEditViewModel(repository, testAppContext(), null), onClose = {})
        }
        composeTestRule.switchNear(string(R.string.skip_requires_app_title)).assertIsOn()
        composeTestRule.switchNear(string(R.string.skip_game_title)).assertIsOff()
        composeTestRule.switchNear(string(R.string.snooze_title)).assertIsOff()
    }

    // skipRequiresAppをオフにするとskipGameが選べなくなる(オフのまま操作不能になる)ことを保証する
    @Test
    fun skipRequiresAppをオフにするとskipGameを選べなくなる() {
        composeTestRule.setContent {
            AlarmEditScreen(viewModel = AlarmEditViewModel(repository, testAppContext(), null), onClose = {})
        }
        val skipRequiresAppTitle = string(R.string.skip_requires_app_title)
        val skipGameTitle = string(R.string.skip_game_title)

        composeTestRule.switchNear(skipRequiresAppTitle).performClick()

        composeTestRule.switchNear(skipRequiresAppTitle).assertIsOff()
        composeTestRule.switchNear(skipGameTitle).assertIsOff()
        composeTestRule.switchNear(skipGameTitle).assert(isNotEnabled())
        composeTestRule.onNodeWithText(string(R.string.skip_game_disabled_reason)).assertExists()
    }

    // 「止めにくさ」の設定を変えて保存し、再度開いたときに復元されることを保証する。
    // AndroidComposeTestRuleはsetContentを1テストにつき1回しか呼べないため、
    // 保存後の「開き直し」は同じsetContent内でreopenIdを切り替えて表現する
    @Test
    fun 止めにくさの設定を変えて保存すると復元される() {
        lateinit var newViewModel: AlarmEditViewModel
        val reopenId = mutableStateOf<Long?>(null)
        composeTestRule.setContent {
            val id by reopenId
            if (id == null) {
                newViewModel = remember { AlarmEditViewModel(repository, testAppContext(), null) }
                AlarmEditScreen(viewModel = newViewModel, onClose = {})
            } else {
                AlarmEditScreen(viewModel = remember(id) { AlarmEditViewModel(repository, testAppContext(), id) }, onClose = {})
            }
        }
        val skipGameTitle = string(R.string.skip_game_title)
        val snoozeTitle = string(R.string.snooze_title)

        // skipRequiresAppはオンのままskipGameだけをオンにする(オフのままだと選べないため)
        composeTestRule.switchNear(skipGameTitle).performClick()
        composeTestRule.switchNear(snoozeTitle).performClick()
        composeTestRule.onNodeWithText(string(R.string.snooze_minutes_label)).assertExists() // スヌーズをオンにすると分数入力欄が現れる

        composeTestRule.onNodeWithText(string(R.string.save)).performClick()
        composeTestRule.waitUntil(5_000) { newViewModel.uiState.isSaved }

        val savedSchedule = runBlocking { repository.observeAll().first().first() }
        assertEquals(true, savedSchedule.skipGame)
        assertEquals(10, savedSchedule.snoozeMinutes)

        composeTestRule.runOnIdle { reopenId.value = savedSchedule.id }
        composeTestRule.waitUntilAtLeastOneExists(hasText(string(R.string.edit_title_existing)), 5_000)
        composeTestRule.switchNear(skipGameTitle).assertIsOn()
        composeTestRule.switchNear(snoozeTitle).assertIsOn()
    }
}
