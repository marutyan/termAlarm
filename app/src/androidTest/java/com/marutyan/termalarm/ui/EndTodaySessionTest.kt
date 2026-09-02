package com.marutyan.termalarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.GameQuestion
import com.marutyan.termalarm.ui.alarmlist.AlarmListScreen
import com.marutyan.termalarm.ui.alarmlist.AlarmListViewModel
import com.marutyan.termalarm.ui.skipgame.SkipGameScreen
import com.marutyan.termalarm.ui.skipgame.SkipGameViewModel
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 「今日はもう止める」の当日終了とゲームの振る舞いを保証する。
 * skipGame=falseは確認だけで終了し、skipGame=trueはゲーム画面を経由し、正解のときだけ当日終了が実行される。
 *
 * ゲームは出題のたびに6種類からランダムに1つ選ばれ、種類ごとに画面が異なる(docs/SPEC.md「ゲーム」)。
 * このテストではSkipGameViewModelがRandomをコンストラクタ引数で受け取れる(既定はRandom.Default)ことを使い、
 * テストからRandom(seed)を渡して出題を固定し、その上でviewModel.uiState.questionを直接読んで
 * 実際に出た種類ごとに正しい操作を行う(種類による分岐)。「端末を振る」はセンサー入力を実機テストから
 * 安定して再現できないため、hasShakeSensor=falseを渡して出題候補から除外する(これはSkipGameViewModel自身が
 * 持つ既存の仕組みで、センサー無し端末と同じ扱いにするだけであり本番のゲーム内容を変えるものではない)。
 */
@OptIn(ExperimentalTestApi::class)
class EndTodaySessionTest {
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

    // skipGame=falseのアラームは、一覧から「今日はもう止める」→確認ダイアログの承認だけでゲーム無しに完了することを保証する
    @Test
    fun skipGameがオフなら確認だけで当日終了する() {
        val id = runBlocking { repository.add(defaultTestSchedule(skipGame = false)) }
        var navigatedToSkipGame = false
        composeTestRule.setContent {
            AlarmListScreen(
                viewModel = remember { AlarmListViewModel(repository, testAppContext()) },
                onAddAlarm = {},
                onEditAlarm = {},
                onOpenAbout = {},
                onNavigateToSkipGame = { navigatedToSkipGame = true },
                exactAlarmBanner = {},
                notificationPermissionBanner = {},
            )
        }
        composeTestRule.onNodeWithText(string(R.string.ringing_skip_today)).performClick()
        // skipGame=falseなのでゲーム画面へは遷移せず、確認ダイアログが出るはず
        assertTrue(!navigatedToSkipGame)
        composeTestRule.onNodeWithText(string(R.string.end_today_session_confirm)).performClick()

        composeTestRule.waitUntil(5_000) { runBlocking { repository.getById(id)?.skippedSessionStart != null } }
    }

    // skipGame=trueのアラームは、一覧の「今日はもう止める」から確認ダイアログを経ずゲーム画面へ遷移することを保証する
    @Test
    fun skipGameがオンならゲーム画面へ遷移する() {
        runBlocking { repository.add(defaultTestSchedule(skipGame = true)) }
        var navigatedId: Long? = null
        composeTestRule.setContent {
            AlarmListScreen(
                viewModel = remember { AlarmListViewModel(repository, testAppContext()) },
                onAddAlarm = {},
                onEditAlarm = {},
                onOpenAbout = {},
                onNavigateToSkipGame = { id -> navigatedId = id },
                exactAlarmBanner = {},
                notificationPermissionBanner = {},
            )
        }
        composeTestRule.onNodeWithText(string(R.string.ringing_skip_today)).performClick()
        // 確認ダイアログを経由せず直接遷移するので、確認ボタンは存在しない
        composeTestRule.onNodeWithText(string(R.string.end_today_session_confirm)).assertDoesNotExist()
        assertNotNull(navigatedId)
    }

    // ゲームに正解すると当日終了(skippedSessionStartの書き込み)が実行されることを保証する
    @Test
    fun ゲームに正解すると当日終了が実行される() {
        val id = runBlocking { repository.add(defaultTestSchedule(skipGame = true)) }
        lateinit var viewModel: SkipGameViewModel
        composeTestRule.setContent {
            viewModel = remember { SkipGameViewModel(repository, testAppContext(), id, hasShakeSensor = false, random = Random(0)) }
            SkipGameScreen(viewModel = viewModel, onClose = {})
        }
        composeTestRule.waitUntil(5_000) { viewModel.uiState.question != null }
        answerCorrectly(viewModel.uiState.question!!)

        composeTestRule.waitUntil(5_000) { viewModel.uiState.isSuccess }
        val schedule = runBlocking { repository.getById(id) }
        assertNotNull(schedule?.skippedSessionStart)
    }

    // ゲームに不正解のときは、当日終了が実行されないことを保証する
    @Test
    fun ゲームに不正解では当日終了が実行されない() {
        val id = runBlocking { repository.add(defaultTestSchedule(skipGame = true)) }
        lateinit var viewModel: SkipGameViewModel
        composeTestRule.setContent {
            viewModel = remember { SkipGameViewModel(repository, testAppContext(), id, hasShakeSensor = false) }
            SkipGameScreen(viewModel = viewModel, onClose = {})
        }
        composeTestRule.waitUntil(5_000) { viewModel.uiState.question != null }
        answerIncorrectly(viewModel.uiState.question!!)

        // 不正解の直後、SkipGameViewModelは新しい問題を出し直すだけで当日終了は実行しない
        composeTestRule.waitUntilAtLeastOneExists(hasText(string(R.string.ringing_skip_today)), 5_000)
        assertTrue(!viewModel.uiState.isSuccess)
        val schedule = runBlocking { repository.getById(id) }
        assertNull(schedule?.skippedSessionStart)
    }

    // 出た問題の種類に応じて、正解となる操作をUI上で行う
    private fun answerCorrectly(question: GameQuestion) {
        val decide = string(R.string.decide)
        when (question) {
            is GameQuestion.Arithmetic -> typeDigitsAndConfirm(question.correctAnswer, decide)
            is GameQuestion.CountShapes -> typeDigitsAndConfirm(question.correctAnswer, decide)
            is GameQuestion.Transcribe -> {
                composeTestRule.onNode(hasSetTextAction()).performTextInput(question.text)
                composeTestRule.onNodeWithText(decide).performClick()
            }
            is GameQuestion.SequentialTap -> (1..12).forEach { n -> composeTestRule.onNodeWithText(n.toString()).performClick() }
            is GameQuestion.ColorWord -> composeTestRule.onNodeWithText(question.correctAnswer).performClick()
            is GameQuestion.ShakeDevice -> error("hasShakeSensor=falseのためSHAKE_DEVICEは出題されないはず")
        }
    }

    // 出た問題の種類に応じて、確実に不正解となる操作をUI上で行う
    private fun answerIncorrectly(question: GameQuestion) {
        val decide = string(R.string.decide)
        when (question) {
            is GameQuestion.Arithmetic -> typeDigitsAndConfirm("999999", decide)
            is GameQuestion.CountShapes -> typeDigitsAndConfirm("999999", decide)
            is GameQuestion.Transcribe -> {
                composeTestRule.onNode(hasSetTextAction()).performTextInput("WRONGWRONG")
                composeTestRule.onNodeWithText(decide).performClick()
            }
            // 最初のタップを2番目の数字にして順序を崩す。SequentialTapGameはこの場合onSubmitを呼ばず最初からやり直しになる
            is GameQuestion.SequentialTap -> composeTestRule.onNodeWithText("2").performClick()
            is GameQuestion.ColorWord -> {
                val wrongChoice = question.choices.first { it != question.correctAnswer }
                composeTestRule.onNodeWithText(wrongChoice).performClick()
            }
            is GameQuestion.ShakeDevice -> error("hasShakeSensor=falseのためSHAKE_DEVICEは出題されないはず")
        }
    }

    // NumericKeypadの数字ボタンを1文字ずつ押してから決定ボタンを押す。
    // 入力中の値がAnswerDisplayにも同じ文字として表示されるため、同じテキストのノードが2つになりうる。
    // キーパッドは常にAnswerDisplayより後に構成されるため、最後のノード(onLast相当)を押す
    private fun typeDigitsAndConfirm(digits: String, decideLabel: String) {
        digits.forEach { ch ->
            val nodes = composeTestRule.onAllNodesWithText(ch.toString())
            nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        }
        composeTestRule.onNodeWithText(decideLabel).performClick()
    }
}
