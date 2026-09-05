package com.marutyan.termalarm.ui

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.startTimer
import com.marutyan.termalarm.ui.timer.TimerScreen
import com.marutyan.termalarm.ui.timer.TimerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * タイマータブ(TimerScreen)を「画面から操作する経路」で保証する。PMがDBへ直接書き込んで確認しようとして
 * 失敗した(アプリの停止状態やブロードキャストの制約)ため、実際にボタンを押す経路をComposeテストで固める。
 *
 * 残り時間の秒単位の計算そのものはTimerDomainTestが固定した時刻で検証するため、ここでは
 * 「操作した結果、一覧の表示や永続化されたTimerStateが正しく変わるか」までを見る。
 * RUNNING中のタイマーの残り時間表示は実機の時計が進む速さに依存し1秒ごとにしか更新されないため、
 * 表示中の秒数そのものは検証せず、Repositoryに保存された値(totalMillis/remainingMillisAtAnchor/runState)
 * で判定する。PAUSED/FINISHEDは時間経過の影響を受けない値のため、そこだけは表示文字列も確定的に検証できる。
 */
@OptIn(ExperimentalTestApi::class)
class TimerScreenTest {
    // 端末がスリープしていてもテストが動くようにする
    @get:Rule
    val screenWakeRule = ScreenWakeRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: AlarmDatabase
    private lateinit var repository: TimerRepository

    @Before
    fun setUp() {
        val (database, repo) = createTestTimerRepository()
        db = database
        repository = repo
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

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    private fun setScreen() {
        composeTestRule.setContent {
            TimerScreen(viewModel = remember { TimerViewModel(repository, testAppContext()) }, bottomBar = {})
        }
    }

    // 動作中のタイマーが1件も無いとき、案内文が表示されることを保証する
    @Test
    fun タイマーが無いとき案内文が表示される() {
        setScreen()
        composeTestRule.onNodeWithText(string(R.string.timer_list_empty)).assertExists()
    }

    // テンキーで5分00秒(5 → 0 → 0)を入力して開始すると、一覧に1件現れ空表示が消えることを保証する。
    // 時分秒の入力は右下のFABから開くテンキー画面(TimerAddScreen)で行う。
    @Test
    fun 開始すると一覧に1件現れる() {
        setScreen()
        composeTestRule.onNodeWithContentDescription(string(R.string.timer_add)).performClick()
        composeTestRule.onNodeWithText("5").performClick()
        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.timer_start)).performClick()

        composeTestRule.waitUntil(5_000) { runBlocking { repository.observeAll().first().size == 1 } }
        composeTestRule.onNodeWithText(string(R.string.timer_list_empty)).assertDoesNotExist()

        val saved = runBlocking { repository.observeAll().first().single() }
        assertEquals(300_000L, saved.totalMillis) // 5分0秒 = 300,000ms
        assertEquals(TimerRunState.RUNNING, saved.runState)
    }

    // 2件開始すると両方が一覧に出て、それぞれ独立した状態(合計時間・実行状態)を持つことを保証する
    @Test
    fun 複数開始すると両方一覧に出てそれぞれ独立している() {
        setScreen()
        val addDescription = string(R.string.timer_add)
        val startDescription = string(R.string.timer_start)

        // 1件目はテンキーで 5分00秒 (5 → 0 → 0) を入力して開始する
        composeTestRule.onNodeWithContentDescription(addDescription).performClick()
        composeTestRule.onNodeWithText("5").performClick()
        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule.onNodeWithContentDescription(startDescription).performClick()
        composeTestRule.waitUntil(5_000) { runBlocking { repository.observeAll().first().size == 1 } }

        // 2件目はテンキーで 3分00秒 (3 → 0 → 0) を入力して開始する
        composeTestRule.onNodeWithContentDescription(addDescription).performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule.onNodeWithContentDescription(startDescription).performClick()
        composeTestRule.waitUntil(5_000) { runBlocking { repository.observeAll().first().size == 2 } }

        val all = runBlocking { repository.observeAll().first() }
        assertEquals(setOf(300_000L, 180_000L), all.map { it.totalMillis }.toSet())
        assertTrue(all.all { it.runState == TimerRunState.RUNNING })

        // 一方だけ一時停止しても、他方はRUNNINGのままであることを確認する(独立した状態を持つ証拠)。
        // 一時停止/再開ボタンは新デザインではアイコンのみのため、文言ではなくcontentDescriptionで探す
        composeTestRule.onAllNodesWithContentDescription(string(R.string.timer_pause))[0].performClick()
        composeTestRule.waitUntil(5_000) {
            runBlocking { repository.observeAll().first().count { it.runState == TimerRunState.PAUSED } == 1 }
        }
        val afterPause = runBlocking { repository.observeAll().first() }
        assertEquals(1, afterPause.count { it.runState == TimerRunState.RUNNING })
        assertEquals(1, afterPause.count { it.runState == TimerRunState.PAUSED })
    }

    // 一時停止するとPAUSEDになりボタンが「再開」に変わること、再開するとRUNNINGへ戻ることを保証する。
    // 一時停止/再開ボタンは新デザインではアイコンのみのため、文言ではなくcontentDescriptionで探す
    @Test
    fun 一時停止と再開() {
        // タイマーの連続アニメーションによりComposeがアイドル状態にならずタイムアウトするため、クロックの自動進行を止めて手動で進める
        composeTestRule.mainClock.autoAdvance = false
        val id = runBlocking {
            repository.add(startTimer(0L, "5:00", 300_000L, SystemClock.elapsedRealtime(), System.currentTimeMillis()))
        }
        setScreen()
        composeTestRule.mainClock.advanceTimeBy(500)

        composeTestRule.onNodeWithContentDescription(string(R.string.timer_pause)).performClick()
        // 保存はコルーチンで進むため、Composeのクロックを進めるだけでは終わらない。
        // 保存の完了を確かめてから、画面の描き直しを進める
        awaitRunState(id, TimerRunState.PAUSED)
        // 画面は「残り時間が次の秒へ変わる瞬間」に合わせて描き直すため、
        // 1秒分を確実に跨げるだけクロックを進める
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.onNodeWithContentDescription(string(R.string.timer_resume)).assertExists()

        composeTestRule.onNodeWithContentDescription(string(R.string.timer_resume)).performClick()
        awaitRunState(id, TimerRunState.RUNNING)
        composeTestRule.mainClock.advanceTimeBy(2_000)
        // 再開後のUI再描画(recomposition)を完了させるためクロックを進める
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNodeWithContentDescription(string(R.string.timer_pause)).assertExists()
    }

    // 「+1分」を押すと合計時間・残り時間の両方が60秒(60000ms)増えることを保証する
    @Test
    fun 延長すると60秒増える() {
        val id = runBlocking {
            repository.add(startTimer(0L, "1:00", 60_000L, SystemClock.elapsedRealtime(), System.currentTimeMillis()))
        }
        setScreen()

        // 延長ボタンは新デザインでは「+1:00」表記のため、timer_extend_one_minute_button で探す
        composeTestRule.onNodeWithText(string(R.string.timer_extend_one_minute_button)).performClick()
        composeTestRule.waitUntil(5_000) { runBlocking { repository.getById(id)?.totalMillis == 120_000L } }

        val extended = runBlocking { repository.getById(id)!! }
        assertEquals(120_000L, extended.totalMillis)
        // クリックまでに経過したわずかな時間の分だけ60000msより少し多いはず
        assertTrue(extended.remainingMillisAtAnchor in 60_000L..120_000L)
    }

    // リセットすると、経過して減っていた残り時間が最初に指定した時間(totalMillis)へ戻ることを保証する
    @Test
    fun リセットすると最初の時間に戻る() {
        // 5分のうち4分50秒経過してPAUSEDになった状態を直接作る(実機の時計の経過を待たない)
        val id = runBlocking {
            repository.add(
                TimerState(
                    id = 0L,
                    label = "5:00",
                    totalMillis = 300_000L,
                    remainingMillisAtAnchor = 10_000L,
                    anchorElapsedRealtime = SystemClock.elapsedRealtime(),
                    anchorWallClockMillis = System.currentTimeMillis(),
                    runState = TimerRunState.PAUSED,
                ),
            )
        }
        setScreen()
        // リセットボタンは新デザインでは円を描く矢印アイコンのため、contentDescriptionで探す
        composeTestRule.onNodeWithContentDescription(string(R.string.timer_reset)).performClick()

        composeTestRule.waitUntil(5_000) { runBlocking { repository.getById(id)?.remainingMillisAtAnchor == 300_000L } }
        val reset = runBlocking { repository.getById(id)!! }
        assertEquals(300_000L, reset.remainingMillisAtAnchor)
        assertEquals(TimerRunState.PAUSED, reset.runState)
        // PAUSEDは時間経過の影響を受けないため、表示文字列も確定的に検証できる
        // (ラベルと残り時間表示の両方が"5:00"になるため、単一ノード前提のonNodeWithTextは使わない)
        assertTrue(composeTestRule.onAllNodesWithText("5:00").fetchSemanticsNodes().isNotEmpty())
    }

    // 削除すると一覧から消えることを保証する
    @Test
    fun 削除すると一覧から消える() {
        runBlocking {
            repository.add(startTimer(0L, "5:00", 300_000L, SystemClock.elapsedRealtime(), System.currentTimeMillis()))
        }
        setScreen()

        composeTestRule.onNodeWithContentDescription(string(R.string.timer_delete)).performClick()

        composeTestRule.waitUntil(5_000) { runBlocking { repository.observeAll().first().isEmpty() } }
        composeTestRule.onNodeWithText(string(R.string.timer_list_empty)).assertExists()
    }

    /**
     * 保存が終わって指定の状態になるまで待つ。
     * この画面は数字が動き続けるためComposeが「暇な状態」にならず、
     * waitUntilやwaitForIdleでは待てない。Repositoryを直接見て待つ。
     */
    private fun awaitRunState(id: Long, expected: TimerRunState) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (runBlocking { repository.getById(id)?.runState } == expected) return
            Thread.sleep(50)
        }
        assertEquals(expected, runBlocking { repository.getById(id)?.runState })
    }
}
