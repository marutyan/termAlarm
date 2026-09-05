package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.ClockSettingsRepository
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.domain.ClockDisplayMode
import com.marutyan.termalarm.ui.clock.ClockScreen
import com.marutyan.termalarm.ui.clock.ClockViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 時計タブ(ClockScreen)のUI表示と操作を保証する。
 * アナログとデジタルの表示モード切り替え、および端末の日付表示が正しく機能することを検証する。
 */
@OptIn(ExperimentalTestApi::class)
class ClockScreenTest {
    // 端末がスリープしていてもテストが動くようにする
    @get:Rule
    val screenWakeRule = ScreenWakeRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AlarmDatabase
    private lateinit var repository: ClockSettingsRepository
    private lateinit var settingsRepository: SettingsRepository

    /**
     * テストごとに独立したインメモリDBとClockSettingsRepositoryを初期化する。
     * 前のテストの保存データが次のテストに影響しないようにする。
     */
    @Before
    fun setUp() {
        val (database, repo) = createTestClockRepository()
        db = database
        repository = repo
        settingsRepository = SettingsRepository(db.appSettingsDao())
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
     * 時計タブの画面をテストルール上にセットアップする。
     * テスト対象のClockViewModelをインメモリDBに接続した状態で画面を組み立てる。
     */
    private fun setScreen() {
        composeTestRule.setContent {
            ClockScreen(
                viewModel = remember { ClockViewModel(repository, settingsRepository) },
                bottomBar = {},
            )
        }
    }

    // 初期状態のデジタル表示で、現在時刻が表示されデジタルのセグメントボタンが選択されていることを保証する
    @Test
    fun デジタル表示のとき現在時刻が表示される() {
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.clock_display_mode_digital)).assertIsSelected()

        val now = ZonedDateTime.now()
        val currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val prevMinuteTime = now.minusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm"))
        val hasCurrentOrPrev = composeTestRule.onAllNodes(hasText(currentTime)).fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodes(hasText(prevMinuteTime)).fetchSemanticsNodes().isNotEmpty()
        assertTrue("現在時刻の表示が存在すること", hasCurrentOrPrev)
    }

    // 表示モードをアナログへ切り替えると、RepositoryにANALOGが保存され、デジタルの時分表示が非表示になることを保証する
    @Test
    fun アナログに切り替えるとモードが保存されデジタル時刻が非表示になる() {
        setScreen()

        val now = ZonedDateTime.now()
        val currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))

        composeTestRule.onNodeWithText(string(R.string.clock_display_mode_analog)).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { repository.observeDisplayMode().first() == ClockDisplayMode.ANALOG }
        }

        composeTestRule.onNodeWithText(string(R.string.clock_display_mode_analog)).assertIsSelected()
        composeTestRule.onNodeWithText(currentTime).assertDoesNotExist()
    }

    // アナログからデジタルへ切り替え直すと、RepositoryにDIGITALが保存され、時分表示が再表示されることを保証する
    @Test
    fun アナログからデジタルに切り替えるとデジタル表示に戻りRepositoryに保存される() {
        runBlocking { repository.setDisplayMode(ClockDisplayMode.ANALOG) }
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.clock_display_mode_analog)).assertIsSelected()

        composeTestRule.onNodeWithText(string(R.string.clock_display_mode_digital)).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { repository.observeDisplayMode().first() == ClockDisplayMode.DIGITAL }
        }

        composeTestRule.onNodeWithText(string(R.string.clock_display_mode_digital)).assertIsSelected()

        val now = ZonedDateTime.now()
        val currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val prevMinuteTime = now.minusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm"))
        val hasCurrentOrPrev = composeTestRule.onAllNodes(hasText(currentTime)).fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodes(hasText(prevMinuteTime)).fetchSemanticsNodes().isNotEmpty()
        assertTrue("デジタル時刻の表示が再表示されること", hasCurrentOrPrev)
    }

    // 時計タブに現在の日付が「M月d日（E）」の形式で表示されていることを保証する
    @Test
    fun 日付が表示される() {
        setScreen()

        val todayDate = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("M月d日", Locale.JAPANESE))
        composeTestRule.onNodeWithText(todayDate, substring = true).assertExists()
    }
}
