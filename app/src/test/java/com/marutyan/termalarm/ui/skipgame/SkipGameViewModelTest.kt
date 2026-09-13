package com.marutyan.termalarm.ui.skipgame

import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.data.FakeAlarmDao
import com.marutyan.termalarm.data.FakeAppSettingsDao
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.ChallengeLevel
import com.marutyan.termalarm.domain.ChallengeTiming
import com.marutyan.termalarm.domain.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import kotlin.random.Random

/**
 * SkipGameViewModelの単体テスト。
 * 出題数の導出、複数問題の解答進行、不正解時の再試行、およびセンサー有無による出題フィルタリングを検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SkipGameViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var alarmDao: FakeAlarmDao
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var settingsDao: FakeAppSettingsDao
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        alarmDao = FakeAlarmDao()
        alarmRepository = AlarmRepository(alarmDao)
        settingsDao = FakeAppSettingsDao()
        settingsRepository = SettingsRepository(settingsDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `EASY設定では出題数が1問になる`() = runTest {
        val schedule = AlarmSchedule(
            id = 1L,
            startMinutes = 420,
            endMinutes = 480,
            startIntervalMinutes = 10,
            endIntervalMinutes = 10,
            repeatDays = setOf(DayOfWeek.MONDAY),
            label = "起床",
            enabled = true,
            challengeTiming = ChallengeTiming.EVERY_TIME,
            challenge = ChallengeLevel.EASY,
        )
        val id = alarmRepository.add(schedule)

        val viewModel = SkipGameViewModel(
            repository = alarmRepository,
            alarmId = id,
            hasShakeSensor = false,
            occurrenceIndex = 0,
            settingsRepository = settingsRepository,
        )
        advanceUntilIdle()

        val state = viewModel.uiState
        assertFalse(state.isLoading)
        assertEquals(1, state.totalQuestions)
        assertEquals(0, state.currentQuestionIndex)
        assertNotNull(state.question)
    }

    @Test
    fun `HARD設定の最終回では出題数が3問になる`() = runTest {
        val schedule = AlarmSchedule(
            id = 2L,
            startMinutes = 420,
            endMinutes = 450,
            startIntervalMinutes = 10,
            endIntervalMinutes = 10,
            repeatDays = emptySet(),
            label = "起床HARD",
            enabled = true,
            challengeTiming = ChallengeTiming.EVERY_TIME,
            challenge = ChallengeLevel.HARD,
        )
        val id = alarmRepository.add(schedule)

        // 420, 430, 440, 450 -> 全4回、最終回はoccurrenceIndex = 3
        val viewModel = SkipGameViewModel(
            repository = alarmRepository,
            alarmId = id,
            hasShakeSensor = false,
            occurrenceIndex = 3,
            settingsRepository = settingsRepository,
        )
        advanceUntilIdle()

        val state = viewModel.uiState
        assertEquals(3, state.totalQuestions)
    }

    @Test
    fun `正解すると次の問題へ進み全問正解でisSuccessがtrueになる`() = runTest {
        val schedule = AlarmSchedule(
            id = 0L,
            startMinutes = 420,
            endMinutes = 450,
            startIntervalMinutes = 10,
            endIntervalMinutes = 10,
            repeatDays = emptySet(),
            label = "テスト進行",
            enabled = true,
            challengeTiming = ChallengeTiming.EVERY_TIME,
            challenge = ChallengeLevel.HARD,
        )
        val id = alarmRepository.add(schedule)

        val viewModel = SkipGameViewModel(
            repository = alarmRepository,
            alarmId = id,
            hasShakeSensor = false,
            occurrenceIndex = 3,
            settingsRepository = settingsRepository,
        )
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.totalQuestions)
        assertEquals(0, viewModel.uiState.currentQuestionIndex)

        // 1問目正解
        val q1 = viewModel.uiState.question!!
        viewModel.submitAnswer(q1.correctAnswer)
        assertEquals(1, viewModel.uiState.currentQuestionIndex)
        assertFalse(viewModel.uiState.isSuccess)

        // 2問目正解
        val q2 = viewModel.uiState.question!!
        viewModel.submitAnswer(q2.correctAnswer)
        assertEquals(2, viewModel.uiState.currentQuestionIndex)
        assertFalse(viewModel.uiState.isSuccess)

        // 3問目正解
        val q3 = viewModel.uiState.question!!
        viewModel.submitAnswer(q3.correctAnswer)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.isSuccess)
    }

    @Test
    fun `不正解の場合は同じ問題数にとどまりjustFailedがtrueになる`() = runTest {
        val schedule = AlarmSchedule(
            id = 4L,
            startMinutes = 420,
            endMinutes = 480,
            startIntervalMinutes = 10,
            endIntervalMinutes = 10,
            repeatDays = emptySet(),
            label = "テスト不正解",
            enabled = true,
            challengeTiming = ChallengeTiming.EVERY_TIME,
            challenge = ChallengeLevel.EASY,
        )
        val id = alarmRepository.add(schedule)

        val viewModel = SkipGameViewModel(
            repository = alarmRepository,
            alarmId = id,
            hasShakeSensor = false,
            occurrenceIndex = 0,
            settingsRepository = settingsRepository,
        )
        advanceUntilIdle()

        // 誤った回答を提出
        viewModel.submitAnswer("WRONG_ANSWER_12345")
        val state = viewModel.uiState
        assertFalse(state.isSuccess)
        assertTrue(state.justFailed)
        assertEquals(0, state.currentQuestionIndex)
    }

    @Test
    fun `加速度センサーが無い端末ではSHAKE_DEVICEとWALKが出題されない`() = runTest {
        val schedule = AlarmSchedule(
            id = 5L,
            startMinutes = 420,
            endMinutes = 480,
            startIntervalMinutes = 10,
            endIntervalMinutes = 10,
            repeatDays = emptySet(),
            label = "センサーテスト",
            enabled = true,
            challengeTiming = ChallengeTiming.EVERY_TIME,
            challenge = ChallengeLevel.EASY,
        )
        val id = alarmRepository.add(schedule)

        // 全種類のゲームを有効にしてテスト
        settingsRepository.update(AppSettings(enabledGames = GameType.entries.toSet()))

        // 100問生成してSHAKE_DEVICEやWALKが出ないことを確認
        for (seed in 0..99) {
            val viewModel = SkipGameViewModel(
                repository = alarmRepository,
                alarmId = id,
                hasShakeSensor = false,
                occurrenceIndex = 0,
                random = Random(seed),
                settingsRepository = settingsRepository,
            )
            advanceUntilIdle()
            val question = viewModel.uiState.question!!
            assertTrue(question.type != GameType.SHAKE_DEVICE)
            assertTrue(question.type != GameType.WALK)
        }
    }
}
