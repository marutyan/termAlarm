package com.marutyan.termalarm.ui

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.StopwatchRepository
import com.marutyan.termalarm.domain.StopwatchLap
import com.marutyan.termalarm.domain.StopwatchRunState
import com.marutyan.termalarm.domain.StopwatchState
import com.marutyan.termalarm.domain.elapsedMillis
import com.marutyan.termalarm.ui.stopwatch.StopwatchScreen
import com.marutyan.termalarm.ui.stopwatch.StopwatchViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ストップウォッチタブ(StopwatchScreen)を「画面から操作する経路」で保証する(TimerScreenTestと同じ方針)。
 * design/Stopwatch.dc.html に基づく新デザインに合わせて検証する。
 *
 * 経過時間の分秒(66sp)・小数部(34sp)、等幅の3操作ボタン(リセット、ラップ、停止/開始)、
 * およびラップ一覧カードの表示と状態遷移を検証する。
 */
@OptIn(ExperimentalTestApi::class)
class StopwatchScreenTest {
    // 端末がスリープしていてもテストが動くようにする
    @get:Rule
    val screenWakeRule = ScreenWakeRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AlarmDatabase
    private lateinit var repository: StopwatchRepository

    @Before
    fun setUp() {
        val (database, repo) = createTestStopwatchRepository()
        db = database
        repository = repo
    }

    /**
     * テスト終了後の後始末。
     * インメモリDBは閉じない。画面が持つViewModelが保存処理を継続している場合があるため。
     */
    @After
    fun tearDown() {
    }

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    private fun setScreen() {
        composeTestRule.setContent {
            StopwatchScreen(viewModel = remember { StopwatchViewModel(repository, testAppContext()) }, bottomBar = {})
        }
    }

    private fun formatMain(millis: Long): Pair<String, String> {
        val clamped = millis.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val minutes = (clamped % 3_600_000L) / 60_000L
        val seconds = (clamped % 60_000L) / 1000L
        val centis = (clamped % 1000L) / 10L
        val mainPart = if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
        val centisPart = ".%02d".format(centis)
        return mainPart to centisPart
    }

    private fun formatLap(millis: Long): String {
        val clamped = millis.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val minutes = (clamped % 3_600_000L) / 60_000L
        val seconds = (clamped % 60_000L) / 1000L
        val centis = (clamped % 1000L) / 10L
        return if (hours > 0) {
            "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centis)
        } else {
            "%d:%02d.%02d".format(minutes, seconds, centis)
        }
    }

    // 何も操作していない(IDLE)とき、経過時間は0:00と.00、操作ボタン3つ(リセット、ラップ、開始)が並ぶことを保証する
    @Test
    fun 何もしていないときの表示() {
        setScreen()
        composeTestRule.onNodeWithText("0:00").assertExists()
        composeTestRule.onNodeWithText(".00").assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_reset)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_lap)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_pause)).assertDoesNotExist()
    }

    // 「開始」を押すとRepositoryの状態がRUNNINGになり、ボタンが「停止」へ切り替わり、
    // 小数部が初期値(.00)から動き出すことを保証する
    @Test
    fun 開始すると計測が始まる() {
        setScreen()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).performClick()

        composeTestRule.waitUntil(5_000) { runBlocking { repository.getStateOnce().runState == StopwatchRunState.RUNNING } }
        composeTestRule.onNodeWithText(string(R.string.stopwatch_pause)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_lap)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).assertDoesNotExist()

        // RUNNING中は100msごとに再描画されるため、初期値(.00)の表示がいずれ消えることで動き出したことを確認する
        composeTestRule.waitUntil(2_000) {
            composeTestRule.onAllNodesWithText(".00").fetchSemanticsNodes().isEmpty()
        }
    }

    // 一時停止するとRepositoryのaccumulatedMillisが固定され、時間が経っても値も表示も変わらないことを保証する
    @Test
    fun 一時停止すると値が止まる() {
        setScreen()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).performClick()
        composeTestRule.waitUntil(5_000) { runBlocking { repository.getStateOnce().runState == StopwatchRunState.RUNNING } }
        SystemClock.sleep(300) // 一時停止する前に経過時間を少し進めておく

        composeTestRule.onNodeWithText(string(R.string.stopwatch_pause)).performClick()
        composeTestRule.waitUntil(5_000) { runBlocking { repository.getStateOnce().runState == StopwatchRunState.PAUSED } }

        val frozen = runBlocking { repository.getStateOnce().accumulatedMillis }
        val (mainPart, centisPart) = formatMain(frozen)
        composeTestRule.onNodeWithText(mainPart).assertExists()
        composeTestRule.onNodeWithText(centisPart).assertExists()

        SystemClock.sleep(300) // 一時停止中に時間が経っても値が変わらないことを確認するための待機
        val stillFrozen = runBlocking { repository.getStateOnce().accumulatedMillis }
        assertEquals(frozen, stillFrozen)
        composeTestRule.onNodeWithText(mainPart).assertExists()
        composeTestRule.onNodeWithText(centisPart).assertExists()
    }

    // PAUSED状態から再開すると、Repository上の経過時間(elapsedMillis)が再び進むことを保証する
    @Test
    fun 再開すると再び進む() {
        runBlocking {
            repository.updateState(
                StopwatchState(
                    accumulatedMillis = 5_000L,
                    anchorElapsedRealtime = SystemClock.elapsedRealtime(),
                    anchorWallClockMillis = System.currentTimeMillis(),
                    runState = StopwatchRunState.PAUSED,
                ),
            )
        }
        setScreen()
        val (mainPart, centisPart) = formatMain(5_000L)
        composeTestRule.onNodeWithText(mainPart).assertExists()
        composeTestRule.onNodeWithText(centisPart).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).performClick()

        composeTestRule.waitUntil(5_000) { runBlocking { repository.getStateOnce().runState == StopwatchRunState.RUNNING } }
        composeTestRule.onNodeWithText(string(R.string.stopwatch_pause)).assertExists()

        SystemClock.sleep(300) // 再開後に経過時間が進んでいることを確認するための待機
        val after = runBlocking { repository.getStateOnce() }
        val nowElapsed = elapsedMillis(after, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        assertTrue(nowElapsed > 5_000L)
    }

    // ラップを2回刻むと一覧カードに2件現れ、番号・そのラップの時間・その時点の合計が表示されることを保証する
    @Test
    fun ラップを刻むと一覧に現れる() {
        setScreen()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).performClick()
        composeTestRule.waitUntil(5_000) { runBlocking { repository.getStateOnce().runState == StopwatchRunState.RUNNING } }

        composeTestRule.onNodeWithText(string(R.string.stopwatch_lap)).performClick()
        composeTestRule.waitUntil(5_000) { runBlocking { repository.getLapsOnce().size == 1 } }
        composeTestRule.onNodeWithText(string(R.string.stopwatch_lap)).performClick()
        composeTestRule.waitUntil(5_000) { runBlocking { repository.getLapsOnce().size == 2 } }

        val laps = runBlocking { repository.getLapsOnce() }
        assertEquals(2, laps.size)
        laps.forEach { lap: StopwatchLap ->
            val numberText = lap.lapNumber.toString()
            val totalText = formatLap(lap.totalMillis)
            val lapTimeText = formatLap(lap.lapMillis)
            assertTrue(composeTestRule.onAllNodesWithText(numberText).fetchSemanticsNodes().isNotEmpty())
            assertTrue(composeTestRule.onAllNodesWithText(totalText).fetchSemanticsNodes().isNotEmpty())
            assertTrue(composeTestRule.onAllNodesWithText(lapTimeText).fetchSemanticsNodes().isNotEmpty())
        }
    }

    // リセットすると経過時間が0に戻り、ラップも全て消えることを保証する
    @Test
    fun リセットすると0に戻る() {
        runBlocking {
            repository.updateState(
                StopwatchState(
                    accumulatedMillis = 12_345L,
                    anchorElapsedRealtime = SystemClock.elapsedRealtime(),
                    anchorWallClockMillis = System.currentTimeMillis(),
                    runState = StopwatchRunState.PAUSED,
                ),
            )
            repository.addLap(StopwatchLap(lapNumber = 1, lapMillis = 12_345L, totalMillis = 12_345L))
        }
        setScreen()
        val (initMain, initCentis) = formatMain(12_345L)
        composeTestRule.onNodeWithText(initMain).assertExists()
        composeTestRule.onNodeWithText(initCentis).assertExists()

        composeTestRule.onNodeWithText(string(R.string.stopwatch_reset)).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking {
                val state = repository.getStateOnce()
                state.accumulatedMillis == 0L && state.runState == StopwatchRunState.IDLE
            }
        }
        val lapsAfterReset = runBlocking { repository.getLapsOnce() }
        assertTrue(lapsAfterReset.isEmpty())
        composeTestRule.onNodeWithText("0:00").assertExists()
        composeTestRule.onNodeWithText(".00").assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).assertExists()
    }
}
