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
import com.marutyan.termalarm.stopwatch.formatElapsed
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
 *
 * 経過時間・時差の計算そのものはStopwatchCalculatorTest(JVM単体テスト)が固定した時刻で検証済みのため、
 * ここでは「操作した結果、画面の表示やRepositoryに保存された状態が正しく変わるか」までを見る。
 * RUNNING中は実機の時計が進む速さと100msごとの再描画タイミングに依存し表示が絶えず変わるため、
 * RUNNING中の表示は「初期値(0:00.00)から動き出したか」「ボタンが切り替わったか」だけを見て、
 * 具体的な経過時間の桁は検証しない。PAUSED/IDLEやラップ済みの値は時間経過の影響を受けない
 * (記録された瞬間の値のまま変わらない)ため、そこだけは表示文字列も確定的に検証する。
 */
@OptIn(ExperimentalTestApi::class)
class StopwatchScreenTest {
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

    @After
    fun tearDown() {
        db.close()
    }

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    private fun setScreen() {
        composeTestRule.setContent {
            StopwatchScreen(viewModel = remember { StopwatchViewModel(repository, testAppContext()) }, bottomBar = {})
        }
    }

    // 何も操作していない(IDLE)とき、経過時間は0:00.00のまま、開始ボタンとラップ空表示が出ていることを保証する
    @Test
    fun 何もしていないときの表示() {
        setScreen()
        composeTestRule.onNodeWithText(formatElapsed(0L, includeCentiseconds = true)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_laps_empty)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_pause)).assertDoesNotExist()
    }

    // 「開始」を押すとRepositoryの状態がRUNNINGになり、ボタンが一時停止・ラップへ切り替わり、
    // 表示が初期値(0:00.00)のまま止まっていないことを保証する
    @Test
    fun 開始すると計測が始まる() {
        setScreen()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).performClick()

        composeTestRule.waitUntil(5_000) { runBlocking { repository.getStateOnce().runState == StopwatchRunState.RUNNING } }
        composeTestRule.onNodeWithText(string(R.string.stopwatch_pause)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_lap)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).assertDoesNotExist()

        // RUNNING中は100msごとに再描画されるため、初期値の表示がいずれ消えることで「動き出した」ことを確認する
        composeTestRule.waitUntil(2_000) {
            composeTestRule.onAllNodesWithText(formatElapsed(0L, includeCentiseconds = true)).fetchSemanticsNodes().isEmpty()
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
        composeTestRule.onNodeWithText(formatElapsed(frozen, includeCentiseconds = true)).assertExists()

        SystemClock.sleep(300) // 一時停止中に時間が経っても値が変わらないことを確認するための待機
        val stillFrozen = runBlocking { repository.getStateOnce().accumulatedMillis }
        assertEquals(frozen, stillFrozen)
        composeTestRule.onNodeWithText(formatElapsed(frozen, includeCentiseconds = true)).assertExists()
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
        composeTestRule.onNodeWithText(formatElapsed(5_000L, includeCentiseconds = true)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_resume)).performClick()

        composeTestRule.waitUntil(5_000) { runBlocking { repository.getStateOnce().runState == StopwatchRunState.RUNNING } }
        composeTestRule.onNodeWithText(string(R.string.stopwatch_pause)).assertExists()

        SystemClock.sleep(300) // 再開後に経過時間が進んでいることを確認するための待機
        val after = runBlocking { repository.getStateOnce() }
        val nowElapsed = elapsedMillis(after, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        assertTrue(nowElapsed > 5_000L)
    }

    // ラップを2回刻むと一覧に2件現れ、各ラップの時間とその時点の合計が表示されることを保証する
    // (記録済みのラップの値は時間経過で変わらないため、桁まで確定的に検証できる)
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
        composeTestRule.onNodeWithText(string(R.string.stopwatch_laps_empty)).assertDoesNotExist()
        laps.forEach { lap: StopwatchLap ->
            val numberText = composeTestRule.activity.getString(R.string.stopwatch_lap_number, lap.lapNumber)
            val totalText = composeTestRule.activity.getString(
                R.string.stopwatch_lap_total,
                formatElapsed(lap.totalMillis, includeCentiseconds = true),
            )
            val lapTimeText = formatElapsed(lap.lapMillis, includeCentiseconds = true)
            // メイン表示とラップ時間・合計時間が偶然同じ文字列になっても壊れないよう、単一ノード前提のonNodeWithTextは使わない
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
        // 1件目のラップはlapMillis==totalMillisになるため、メイン表示とラップ行の両方が同じ文字列になる
        // (単一ノード前提のonNodeWithTextは使わない)
        assertTrue(
            composeTestRule.onAllNodesWithText(formatElapsed(12_345L, includeCentiseconds = true))
                .fetchSemanticsNodes().size == 2,
        )
        composeTestRule.onNodeWithText(string(R.string.stopwatch_laps_empty)).assertDoesNotExist()

        composeTestRule.onNodeWithText(string(R.string.stopwatch_reset)).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking {
                val state = repository.getStateOnce()
                state.accumulatedMillis == 0L && state.runState == StopwatchRunState.IDLE
            }
        }
        val lapsAfterReset = runBlocking { repository.getLapsOnce() }
        assertTrue(lapsAfterReset.isEmpty())
        composeTestRule.onNodeWithText(formatElapsed(0L, includeCentiseconds = true)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_laps_empty)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.stopwatch_start)).assertExists()
    }
}
