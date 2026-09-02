package com.marutyan.termalarm.ui.skipgame

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.GameQuestion
import com.marutyan.termalarm.domain.GameType
import com.marutyan.termalarm.domain.generateGameQuestion
import com.marutyan.termalarm.domain.judgeGameAnswer
import com.marutyan.termalarm.domain.occurrenceCount
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * 「今日はもう止める」ゲーム画面の状態。questionは出題のたびに切り替わり、
 * 正誤判定と当日終了の実行はdomain.judgeGameAnswer/AlarmRepository.endTodaySessionへ委ねる(UI側でロジックを持たない)。
 */
data class SkipGameUiState(
    val isLoading: Boolean = true,
    val scheduleLabel: String = "",
    val startMinutes: Int = 0,
    val endMinutes: Int = 0,
    val totalOccurrences: Int = 0, // このセッションで今日鳴る予定だった総回数(バナー表示用)
    val question: GameQuestion? = null,
    val justFailed: Boolean = false, // 直前の回答が不正解で出題し直したことを示す(UIの一時的な案内用)
    val isSuccess: Boolean = false,
)

/**
 * 当日終了の前に出すゲームのViewModel。出題(generateGameQuestion)と判定(judgeGameAnswer)は
 * すべてdomain/Game.ktへ委ね、ここでは画面用の状態管理と当日終了の実行だけを行う。
 * hasShakeSensorはAndroid依存(SensorManager)のため呼び出し側(Composable)が判定して渡す。
 */
class SkipGameViewModel(
    private val repository: AlarmRepository,
    private val alarmId: Long,
    private val hasShakeSensor: Boolean,
    private val random: Random = Random.Default,
) : ViewModel() {

    var uiState by mutableStateOf(SkipGameUiState())
        private set

    // センサーが無い端末では「端末を振る」を出題候補から除外する(docs/SPEC.md「ゲームの実装方針」)
    private val excludedTypes: Set<GameType> = if (hasShakeSensor) emptySet() else setOf(GameType.SHAKE_DEVICE)

    init {
        viewModelScope.launch {
            val schedule = repository.getById(alarmId)
            uiState = uiState.copy(
                isLoading = false,
                scheduleLabel = schedule?.label.orEmpty(),
                startMinutes = schedule?.startMinutes ?: 0,
                endMinutes = schedule?.endMinutes ?: 0,
                totalOccurrences = schedule?.let { occurrenceCount(it) } ?: 0,
                question = generateGameQuestion(random, excludedTypes),
            )
        }
    }

    /**
     * ユーザーの回答を判定する。answerはdocs/SPEC.md「ゲームの回答形式」の表に従って
     * 呼び出し側(各ゲーム画面)が種類ごとの形式に整えてから渡す。
     * 正解なら当日終了を実行し、不正解なら新しい問題を出し直す(SPEC「失敗ならやり直し」)。
     */
    fun submitAnswer(answer: String) {
        val question = uiState.question ?: return
        if (judgeGameAnswer(question, answer)) {
            viewModelScope.launch {
                repository.endTodaySession(alarmId, ZonedDateTime.now())
                uiState = uiState.copy(isSuccess = true)
            }
        } else {
            uiState = uiState.copy(question = generateGameQuestion(random, excludedTypes), justFailed = true)
        }
    }

    fun consumeFailureNotice() { uiState = uiState.copy(justFailed = false) }
}

// 依存注入フレームワークを使わないための手作りファクトリ
class SkipGameViewModelFactory(
    private val repository: AlarmRepository,
    private val alarmId: Long,
    private val hasShakeSensor: Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SkipGameViewModel(repository, alarmId, hasShakeSensor) as T
}
