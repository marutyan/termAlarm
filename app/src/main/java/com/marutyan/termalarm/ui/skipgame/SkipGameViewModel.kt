package com.marutyan.termalarm.ui.skipgame

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.alarm.AlarmScheduler
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.GameQuestion
import com.marutyan.termalarm.domain.GameType
import com.marutyan.termalarm.domain.challengeQuestionCount
import com.marutyan.termalarm.domain.generateGameQuestion
import com.marutyan.termalarm.domain.judgeGameAnswer
import com.marutyan.termalarm.domain.occurrenceCount
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import com.marutyan.termalarm.ui.common.clockTimePattern
import com.marutyan.termalarm.ui.common.formatRangeAndInterval
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/**
 * 解除チャレンジ（ミニゲーム）画面のUI状態。
 * 上部の鳴動・ターム情報、複数問の出題進捗、現在の出題問題、および成否状態を保持するために用いる。
 */
data class SkipGameUiState(
    /** データ読み込み中かどうかを表す。初期化完了までインジケータを表示するために用いる。 */
    val isLoading: Boolean = true,
    /** アラームのラベル文字列。画面ヘッダーやダイアログで対象タームを識別するために用いる。 */
    val scheduleLabel: String = "",
    /** タームの開始時刻（0〜1439分）。範囲表示を組み立てるために用いる。 */
    val startMinutes: Int = 0,
    /** タームの終了時刻（0〜1439分）。範囲表示を組み立てるために用いる。 */
    val endMinutes: Int = 0,
    /** ターム全体の総鳴動回数。目盛バーの総スロット数表示に用いる。 */
    val totalOccurrences: Int = 0,
    /** 現在の鳴動回（1始まり）。「4回目 / 25回」の現在値を表示するために用いる。 */
    val currentOccurrence: Int = 1,
    /** 現在の鳴動インデックス（0始まり）。目盛バーの現在位置や出題数計算に用いる。 */
    val currentOccurrenceIndex: Int = 0,
    /** 鳴動時刻のフォーマット済み文字列（例: "7:15"）。大きく表示する時刻に用いる。 */
    val occurrenceTimeString: String = "",
    /** 範囲と間隔のフォーマット済み文字列（例: "7:00 – 9:00 · 5分ごと"）。サブ情報表示に用いる。 */
    val rangeAndIntervalText: String = "",
    /** 今回のチャレンジで解くべき問題の総数。進捗表示や全問正解判定に用いる。 */
    val totalQuestions: Int = 1,
    /** 現在挑戦中の問題番号インデックス（0始まり）。「3問中1問目」の表示や進捗管理に用いる。 */
    val currentQuestionIndex: Int = 0,
    /** 現在出題中の問題データ。種類別のゲーム画面を描画するために用いる。 */
    val question: GameQuestion? = null,
    /** 直前の回答が不正解だったことを示すフラグ。再試行時の案内や通知表示に用いる。 */
    val justFailed: Boolean = false,
    /** 全問正解してチャレンジを達成したことを示すフラグ。画面終了や停止処理のトリガーに用いる。 */
    val isSuccess: Boolean = false,
    /** ターム終了目的のチャレンジかどうかを表す。全問正解時に当日終了を実行するかどうかの切り替えに用いる。 */
    val isTermEnd: Boolean = false,
)

/**
 * 解除チャレンジ画面のViewModel。
 * 設定や端末センサーに応じた問題生成、正誤判定、複数問題の進行管理、および終了処理を担う。
 */
class SkipGameViewModel(
    private val repository: AlarmRepository,
    context: Context? = null,
    private val alarmId: Long,
    private val hasShakeSensor: Boolean,
    private val occurrenceIndex: Int? = null,
    private val random: Random = Random.Default,
    private val settingsRepository: com.marutyan.termalarm.data.SettingsRepository? = null,
) : ViewModel() {

    // PendingIntent発行やRoomアクセスにはApplication Contextで十分なため、生成時点で切り替えて保持する
    private val appContext: Context? = context?.applicationContext

    // ターム終了目的かどうか（occurrenceIndexが渡されていない場合はターム終了とみなす）
    private val isTermEndScenario: Boolean = (occurrenceIndex == null)

    var uiState by mutableStateOf(SkipGameUiState(isTermEnd = isTermEndScenario))
        private set

    // 出題可能なゲーム種類の集合。設定値と端末の加速度センサー有無から導出する
    private var allowedTypes: Set<GameType> = GameType.entries.toSet()

    init {
        viewModelScope.launch {
            val schedule = repository.getById(alarmId)
            val settingsRepo = settingsRepository ?: appContext?.let { Repositories.settings(it) }
            val settings = settingsRepo?.observe()?.first() ?: AppSettings()

            // 加速度センサーが無い端末では SHAKE_DEVICE と WALK を除外する
            val enabledGames = settings.enabledGames
            val availableGames = if (hasShakeSensor) {
                enabledGames
            } else {
                enabledGames - setOf(GameType.SHAKE_DEVICE, GameType.WALK)
            }
            allowedTypes = if (availableGames.isNotEmpty()) availableGames else setOf(GameType.ARITHMETIC)

            if (schedule != null) {
                val totalCount = occurrenceCount(schedule)
                val now = ZonedDateTime.now()
                val remainingCount = remainingOccurrenceCount(schedule, now)
                val currentOcc = (totalCount - remainingCount).coerceAtLeast(1)
                val currentIdx = occurrenceIndex ?: (currentOcc - 1).coerceAtLeast(0)

                // 出題数はdomainの関数から取得する
                val totalQ = if (occurrenceIndex != null) {
                    challengeQuestionCount(schedule, occurrenceIndex).coerceAtLeast(1)
                } else {
                    challengeQuestionCount(schedule, 1.0).coerceAtLeast(1)
                }

                val timePattern = try {
                    clockTimePattern(false)
                } catch (e: Throwable) {
                    "H:mm"
                }
                val timeFormatter = DateTimeFormatter.ofPattern(timePattern, Locale.getDefault())
                val timeStr = now.format(timeFormatter)
                val rangeAndInterval = formatRangeAndInterval(schedule, timePattern)

                uiState = uiState.copy(
                    isLoading = false,
                    scheduleLabel = schedule.label,
                    startMinutes = schedule.startMinutes,
                    endMinutes = schedule.endMinutes,
                    totalOccurrences = totalCount,
                    currentOccurrence = currentOcc,
                    currentOccurrenceIndex = currentIdx,
                    occurrenceTimeString = timeStr,
                    rangeAndIntervalText = rangeAndInterval,
                    totalQuestions = totalQ,
                    currentQuestionIndex = 0,
                    question = generateGameQuestion(random, allowedTypes),
                    isTermEnd = isTermEndScenario,
                )
            } else {
                // スケジュールが見つからない場合でも空画面にならないようフォールバック生成を行う
                uiState = uiState.copy(
                    isLoading = false,
                    totalQuestions = 1,
                    question = generateGameQuestion(random, allowedTypes),
                    isTermEnd = isTermEndScenario,
                )
            }
        }
    }

    /**
     * ユーザーの回答を判定する。
     * domain.judgeGameAnswerで正誤を評価し、正解なら次の問題へ進むか全問正解処理を行い、不正解なら再出題する。
     */
    fun submitAnswer(answer: String) {
        val question = uiState.question ?: return
        if (judgeGameAnswer(question, answer)) {
            val nextIndex = uiState.currentQuestionIndex + 1
            if (nextIndex >= uiState.totalQuestions) {
                // 全問正解時の処理
                viewModelScope.launch {
                    if (isTermEndScenario) {
                        // 保存と予約の入れ直し、曜日なしタームのオフまでを1か所へ任せる
                        appContext?.let { AlarmScheduler.onSessionEnded(it, alarmId, ZonedDateTime.now()) }
                    }
                    uiState = uiState.copy(isSuccess = true, justFailed = false)
                }
            } else {
                // 次の問題へ進む
                uiState = uiState.copy(
                    currentQuestionIndex = nextIndex,
                    question = generateGameQuestion(random, allowedTypes),
                    justFailed = false,
                )
            }
        } else {
            // 間違えたら問題をやり直す（アラームは止まらない）
            uiState = uiState.copy(
                question = generateGameQuestion(random, allowedTypes),
                justFailed = true,
            )
        }
    }

    /**
     * 不正解の通知表示を消費してリセットする。
     * スナックバー等の案内表示が重複して出続けるのを防ぐために用いる。
     */
    fun consumeFailureNotice() {
        uiState = uiState.copy(justFailed = false)
    }
}

/**
 * SkipGameViewModelを生成するためのViewModelFactory。
 * リポジトリやコンテキスト、対象アラームID、センサー有無などの依存を注入してインスタンスを構築するために用いる。
 */
class SkipGameViewModelFactory(
    private val repository: AlarmRepository,
    private val context: Context,
    private val alarmId: Long,
    private val hasShakeSensor: Boolean,
    private val occurrenceIndex: Int? = null,
    private val random: Random = Random.Default,
    private val settingsRepository: com.marutyan.termalarm.data.SettingsRepository? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SkipGameViewModel(repository, context, alarmId, hasShakeSensor, occurrenceIndex, random, settingsRepository) as T
}
