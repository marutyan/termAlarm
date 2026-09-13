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
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.ui.alarms.AlarmsScreen
import com.marutyan.termalarm.ui.alarms.AlarmsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 通常アラーム画面(AlarmsScreen)のUIテスト。
 * 通常アラームの表示、タームの除外、見出し文言、アラーム追加ボタン、トグル操作を検証する。
 */
@OptIn(ExperimentalTestApi::class)
class AlarmsScreenTest {

    @get:Rule
    val screenWakeRule = ScreenWakeRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AlarmDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var viewModel: AlarmsViewModel

    @Before
    fun setUp() {
        val (database, repo) = createTestRepository()
        db = database
        repository = repo
        viewModel = AlarmsViewModel(repository)
    }

    // 見出しとサブタイトルが表示されることを保証する
    @Test
    fun 見出しとサブタイトルが表示される() {
        composeTestRule.setContent {
            AlarmsScreen(
                viewModel = viewModel,
                onAddAlarm = {},
                onEditAlarm = {},
            )
        }

        composeTestRule.onNodeWithText(context().getString(R.string.alarms_title)).assertExists()
        composeTestRule.onNodeWithText(context().getString(R.string.alarms_subtitle)).assertExists()
        composeTestRule.onNodeWithText(context().getString(R.string.alarms_add_alarm)).assertExists()
    }

    // 通常アラームが表示され、タームが除外されることを保証する
    @Test
    fun 通常アラームのみが表示されタームは表示されない() {
        val termSchedule = AlarmSchedule(
            id = 1L,
            startMinutes = 7 * 60,
            endMinutes = 9 * 60,
            startIntervalMinutes = 5,
            endIntervalMinutes = 5,
            repeatDays = emptySet(),
            label = "ターム用ラベル",
            enabled = true,
        )
        val singleAlarm = AlarmSchedule(
            id = 2L,
            startMinutes = 8 * 60,
            endMinutes = 8 * 60,
            startIntervalMinutes = 5,
            endIntervalMinutes = 5,
            repeatDays = emptySet(),
            label = "通常アラーム用ラベル",
            enabled = true,
        )
        runBlocking {
            repository.add(termSchedule)
            repository.add(singleAlarm)
        }

        composeTestRule.setContent {
            AlarmsScreen(
                viewModel = viewModel,
                onAddAlarm = {},
                onEditAlarm = {},
            )
        }

        composeTestRule.waitUntilAtLeastOneExists(hasText("通常アラーム用ラベル"), 5_000)
        composeTestRule.onNodeWithText("通常アラーム用ラベル").assertExists()
        composeTestRule.onNodeWithText("ターム用ラベル").assertDoesNotExist()
    }

    // トグルスイッチを押すと有効無効が切り替わることを保証する
    @Test
    fun スイッチを押すと有効無効が切り替わる() {
        val singleAlarm = AlarmSchedule(
            id = 0L,
            startMinutes = 12 * 60 + 30,
            endMinutes = 12 * 60 + 30,
            startIntervalMinutes = 5,
            endIntervalMinutes = 5,
            repeatDays = emptySet(),
            label = "昼寝",
            enabled = true,
        )
        val id = runBlocking { repository.add(singleAlarm) }

        composeTestRule.setContent {
            AlarmsScreen(
                viewModel = viewModel,
                onAddAlarm = {},
                onEditAlarm = {},
            )
        }

        composeTestRule.waitUntilAtLeastOneExists(hasText("昼寝"), 5_000)
        val desc = context().getString(R.string.alarms_switch_description, "12:30")
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription(desc)).performClick()

        val updated = runBlocking {
            repository.observeAll().first { list -> list.any { it.id == id && !it.enabled } }
        }
        assertFalse(updated.first { it.id == id }.enabled)
    }

    private fun context() = composeTestRule.activity
}
