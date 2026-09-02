package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.WorldClockRepository
import com.marutyan.termalarm.domain.ClockDisplayMode
import com.marutyan.termalarm.ui.clock.ClockScreen
import com.marutyan.termalarm.ui.clock.ClockViewModel
import com.marutyan.termalarm.ui.clock.cityDisplayName
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 時計タブ(ClockScreen)を「画面から操作する経路」で保証する(TimerScreenTest/StopwatchScreenTestと同じ方針)。
 *
 * 時差の数値計算そのものはWorldClockTest(JVM単体テスト)がZoneId/Instantを固定して検証済みのため、
 * ここでは「操作した結果、都市一覧やRepositoryの状態が正しく変わるか」「時差が(同じ時刻ではなく)
 * 何かしら表示されるか」までを見る。実機の現在時刻(デジタル表示のHH:mm)はテスト実行中も進み続けるため、
 * チェックのたびに期待値を計算し直すことで実機時刻に依存しないようにする。
 *
 * 日本語環境での都市名表示(cityDisplayName、android.icu.text.TimeZoneNames)はJVM単体テストでは動かせないため、
 * ここで実機を使って検証する。テスト実行機のロケールはja-JPであることを確認済み("Asia/Tokyo"→"東京")。
 */
@OptIn(ExperimentalTestApi::class)
class ClockScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AlarmDatabase
    private lateinit var repository: WorldClockRepository

    @Before
    fun setUp() {
        val (database, repo) = createTestWorldClockRepository()
        db = database
        repository = repo
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    private fun setScreen() {
        composeTestRule.setContent {
            ClockScreen(viewModel = remember { ClockViewModel(repository) }, bottomBar = {})
        }
    }

    // 都市が1件も無いとき、案内文が表示されることを保証する
    @Test
    fun 都市が無いときの表示() {
        setScreen()
        composeTestRule.onNodeWithText(string(R.string.clock_city_list_empty)).assertExists()
    }

    // アナログ⇔デジタルの切り替えで、Repositoryの表示設定とデジタル時刻表示の有無が入れ替わることを保証する。
    // デジタル表示(HH:mm)は実機時刻が進み続けるため、チェックのたびに期待値を計算し直す
    @Test
    fun アナログとデジタルを切り替えられる() {
        setScreen()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(ZonedDateTime.now().format(formatter)).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(string(R.string.clock_display_mode_analog)).performClick()
        composeTestRule.waitUntil(5_000) { runBlocking { repository.observeDisplayMode().first() == ClockDisplayMode.ANALOG } }
        composeTestRule.onNodeWithText(string(R.string.clock_display_mode_analog)).assertIsSelected()
        // アナログ表示はCanvas描画のみでTextノードを持たないため、デジタル書式の表示が消えていることを確認する
        composeTestRule.onNodeWithText(ZonedDateTime.now().format(formatter)).assertDoesNotExist()

        composeTestRule.onNodeWithText(string(R.string.clock_display_mode_digital)).performClick()
        composeTestRule.waitUntil(5_000) { runBlocking { repository.observeDisplayMode().first() == ClockDisplayMode.DIGITAL } }
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(ZonedDateTime.now().format(formatter)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // FABから都市を追加すると一覧に現れ、Repositoryにも保存されることを保証する。
    // 検索欄にはzoneIdの一部("Tokyo")を入力する(表示名はロケール依存だがzoneIdは常に"Asia/Tokyo"のため)
    @Test
    fun 都市を追加すると一覧に現れる() {
        setScreen()
        composeTestRule.onNodeWithText(string(R.string.clock_city_list_empty)).assertExists()

        composeTestRule.onNodeWithContentDescription(string(R.string.clock_add_city)).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText(string(R.string.clock_add_city_title)), 5_000)

        composeTestRule.onNode(hasSetTextAction()).performTextInput("Tokyo")
        composeTestRule.waitUntilAtLeastOneExists(hasText("Asia/Tokyo"), 5_000)
        composeTestRule.onNodeWithText("Asia/Tokyo").performClick()

        composeTestRule.waitUntil(5_000) { runBlocking { repository.observeCities().first().size == 1 } }
        composeTestRule.onNodeWithText(string(R.string.clock_city_list_empty)).assertDoesNotExist()
        // 日本語ロケールでは識別子("Asia/Tokyo")ではなく読める都市名("東京")で表示されることを確認する
        composeTestRule.onNodeWithText("東京").assertExists()

        val saved = runBlocking { repository.observeCities().first().single() }
        assertEquals("Asia/Tokyo", saved.zoneId)
    }

    // 都市を削除すると一覧から消え、Repositoryからも消えることを保証する
    @Test
    fun 都市を削除すると消える() {
        runBlocking { repository.addCity("Asia/Tokyo") }
        setScreen()
        composeTestRule.onNodeWithText("東京").assertExists()

        composeTestRule.onNodeWithContentDescription(string(R.string.clock_delete_city)).performClick()

        composeTestRule.waitUntil(5_000) { runBlocking { repository.observeCities().first().isEmpty() } }
        composeTestRule.onNodeWithText("東京").assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.clock_city_list_empty)).assertExists()
    }

    // 先頭の都市の「下へ移動」を押すと、Repository上の並び順と画面上の表示順(縦位置)の両方が入れ替わることを保証する
    @Test
    fun 都市を並べ替えられる() {
        runBlocking {
            repository.addCity("Asia/Tokyo")
            repository.addCity("America/New_York")
        }
        setScreen()
        val tokyoName = cityDisplayName("Asia/Tokyo")
        val nyName = cityDisplayName("America/New_York")
        composeTestRule.onNodeWithText(tokyoName).assertExists()
        composeTestRule.onNodeWithText(nyName).assertExists()

        fun topOf(name: String) = composeTestRule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top
        assertTrue(topOf(tokyoName) < topOf(nyName)) // 追加順どおり東京が先頭にあることを確認してから並べ替える

        composeTestRule.onAllNodesWithContentDescription(string(R.string.clock_move_down))[0].performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking { repository.observeCities().first().map { it.zoneId } == listOf("America/New_York", "Asia/Tokyo") }
        }
        assertTrue(topOf(nyName) < topOf(tokyoName)) // 画面上の表示順も入れ替わっていることを確認する
    }

    // 端末のタイムゾーン(Asia/Tokyo, UTC+9)と大きく異なる都市を追加すると、「同じ時刻」ではなく
    // 具体的な時差が表示されることを保証する。時差の数値の正しさそのものはWorldClockTestの担当とする
    @Test
    fun 追加した都市に時差が表示される() {
        runBlocking { repository.addCity("Etc/GMT+12") } // UTC-12固定、Asia/Tokyo(UTC+9)とは常に21時間差
        setScreen()

        composeTestRule.onNodeWithText(string(R.string.clock_diff_same_time)).assertDoesNotExist()
        assertTrue(composeTestRule.onAllNodesWithText("時間", substring = true).fetchSemanticsNodes().isNotEmpty())
    }
}
