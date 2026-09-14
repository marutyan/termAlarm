package com.marutyan.termalarm.ui.alarmedit

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.alarm.AlarmScheduler
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.ChallengeLevel
import com.marutyan.termalarm.domain.ChallengeTiming
import com.marutyan.termalarm.domain.occurrenceCount
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.launch

/** 間隔選択チップに並べる既定値。これ以外の値は「他」から任意指定する。 */
val INTERVAL_PRESETS_MINUTES = listOf(1, 3, 5, 10, 15)

/** 任意指定できる間隔の最小値(1分)。0分以下のアラーム鳴動を防ぐために必要。 */
const val CUSTOM_INTERVAL_MIN = 1

/** 任意指定できる間隔の最大値(120分)。極端に長い間隔による計算不正を防ぐために必要。 */
const val CUSTOM_INTERVAL_MAX = 120

/** 保存を妨げる入力エラーの種類。ViewModelに文字列を持たせずUI側で解決するために用いる。 */
enum class AlarmEditValidationError {
    /** 間隔が正の値でない */
    INTERVAL_NOT_POSITIVE,
    /** 間隔が範囲に対して大きすぎて2回以上鳴らない */
    INTERVAL_TOO_LARGE,
    /** 不正なカスタム間隔 */
    CUSTOM_INTERVAL_INVALID,
}

/**
 * ターム編集画面の入力状態を表すデータクラス。
 * 新規作成(id=null)と既存編集(id!=null)の両方を保持し、加速間隔や二度寝チェックなどの全設定を管理する。
 */
data class AlarmEditUiState(
    val id: Long? = null,
    val startMinutes: Int = 7 * 60,
    val endMinutes: Int = 9 * 60,
    val isSingleAlarm: Boolean = false,
    val isVariableInterval: Boolean = false,
    val startIntervalMinutes: Int = 5,
    val endIntervalMinutes: Int = 5,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
    val challengeTiming: ChallengeTiming = ChallengeTiming.NEVER,
    val challenge: ChallengeLevel = ChallengeLevel.EASY,
    val wakeCheck: Boolean = false,
    val isLoading: Boolean = false,
    val validationError: AlarmEditValidationError? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
) {
    /** 互換用プロパティ。等間隔時や開始間隔を取得するために用いる。 */
    val intervalMinutes: Int get() = startIntervalMinutes

    /** 互換用セカンダリコンストラクタ。単一の間隔指定からUI状態を構築するために用いる。 */
    constructor(
        id: Long? = null,
        startMinutes: Int = 7 * 60,
        endMinutes: Int = 9 * 60,
        intervalMinutes: Int,
        repeatDays: Set<DayOfWeek> = emptySet(),
        label: String = "",
        enabled: Boolean = true,
        challengeTiming: ChallengeTiming = ChallengeTiming.NEVER,
        challenge: ChallengeLevel = ChallengeLevel.EASY,
        wakeCheck: Boolean = false,
        isLoading: Boolean = false,
        validationError: AlarmEditValidationError? = null,
        isSaved: Boolean = false,
        isDeleted: Boolean = false,
        isSingleAlarm: Boolean = false,
    ) : this(
        id = id,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        isSingleAlarm = isSingleAlarm,
        isVariableInterval = false,
        startIntervalMinutes = intervalMinutes,
        endIntervalMinutes = intervalMinutes,
        repeatDays = repeatDays,
        label = label,
        enabled = enabled,
        challengeTiming = challengeTiming,
        challenge = challenge,
        wakeCheck = wakeCheck,
        isLoading = isLoading,
        validationError = validationError,
        isSaved = isSaved,
        isDeleted = isDeleted,
    )

    /**
     * UI状態を永続化・計算用のAlarmScheduleへ変換する。
     * skippedSessionStartは編集画面から変更しないため既存値をそのまま保持する。
     */
    fun toSchedule(existingSkippedSessionStart: LocalDate?): AlarmSchedule = AlarmSchedule(
        id = id ?: 0L,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        startIntervalMinutes = startIntervalMinutes,
        endIntervalMinutes = if (isVariableInterval) endIntervalMinutes else startIntervalMinutes,
        repeatDays = repeatDays,
        label = label,
        enabled = enabled,
        skippedSessionStart = existingSkippedSessionStart,
        challengeTiming = challengeTiming,
        challenge = challenge,
        wakeCheck = wakeCheck,
    )
}

/**
 * ターム追加・編集画面のViewModel。
 * スケジュールの読み込み、各入力値の更新、入力検証、保存および削除処理を担う。
 */
class AlarmEditViewModel(
    private val repository: AlarmRepository,
    context: Context,
    alarmId: Long?,
    isSingleAlarm: Boolean = false,
) : ViewModel() {

    private val appContext: Context = context.applicationContext

    var uiState by mutableStateOf(
        AlarmEditUiState(
            id = alarmId,
            isLoading = alarmId != null,
            isSingleAlarm = isSingleAlarm,
            endMinutes = if (isSingleAlarm && alarmId == null) 7 * 60 else 9 * 60,
        ),
    )
        private set

    private var loadedSkippedSessionStart: LocalDate? = null

    init {
        val id = alarmId
        if (id == null) {
            uiState = uiState.copy(isLoading = false)
        } else {
            viewModelScope.launch {
                val schedule = repository.getById(id)
                uiState = if (schedule != null) {
                    loadedSkippedSessionStart = schedule.skippedSessionStart
                    val isVariable = schedule.startIntervalMinutes != schedule.endIntervalMinutes
                    val single = isSingleAlarm || (schedule.startMinutes == schedule.endMinutes)
                    AlarmEditUiState(
                        id = schedule.id,
                        startMinutes = schedule.startMinutes,
                        endMinutes = if (single) schedule.startMinutes else schedule.endMinutes,
                        isSingleAlarm = single,
                        isVariableInterval = isVariable,
                        startIntervalMinutes = schedule.startIntervalMinutes,
                        endIntervalMinutes = schedule.endIntervalMinutes,
                        repeatDays = schedule.repeatDays,
                        label = schedule.label,
                        enabled = schedule.enabled,
                        challengeTiming = schedule.challengeTiming,
                        challenge = schedule.challenge,
                        wakeCheck = schedule.wakeCheck,
                        isLoading = false,
                    )
                } else {
                    uiState.copy(isLoading = false)
                }
            }
        }
    }

    /**
     * 通常アラーム用の単一時刻(0..1439分)を設定する。
     * 開始時刻と終了時刻に同じ値を設定し、1回のみ鳴動するアラームとして整合性を保つために用いる。
     */
    fun setSingleMinutes(minutes: Int) {
        uiState = revalidate(uiState.copy(startMinutes = minutes, endMinutes = minutes))
    }

    /** 開始時刻(0..1439分)を設定する。 */
    fun setStartMinutes(minutes: Int) {
        uiState = revalidate(uiState.copy(startMinutes = minutes))
    }

    /** 終了時刻(0..1439分)を設定する。 */
    fun setEndMinutes(minutes: Int) {
        uiState = revalidate(uiState.copy(endMinutes = minutes))
    }

    /** 等間隔(単一間隔)を設定する。 */
    fun setConstantInterval(minutes: Int) {
        val clamped = minutes.coerceIn(CUSTOM_INTERVAL_MIN, CUSTOM_INTERVAL_MAX)
        uiState = revalidate(
            uiState.copy(
                isVariableInterval = false,
                startIntervalMinutes = clamped,
                endIntervalMinutes = clamped,
            ),
        )
    }

    /** カスタム間隔を設定する(setConstantIntervalの別名)。 */
    fun setCustomInterval(minutes: Int) {
        setConstantInterval(minutes)
    }

    /** プリセット間隔を選択する。 */
    fun selectPresetInterval(minutes: Int) {
        setConstantInterval(minutes)
    }

    /** 加速間隔(開始・終了間隔)を設定する。 */
    fun setVariableInterval(startInterval: Int, endInterval: Int) {
        val clampedStart = startInterval.coerceIn(CUSTOM_INTERVAL_MIN, CUSTOM_INTERVAL_MAX)
        val clampedEnd = endInterval.coerceIn(CUSTOM_INTERVAL_MIN, CUSTOM_INTERVAL_MAX)
        uiState = revalidate(
            uiState.copy(
                isVariableInterval = true,
                startIntervalMinutes = clampedStart,
                endIntervalMinutes = clampedEnd,
            ),
        )
    }

    /** 間隔の種別(等間隔/加速)と値を一括設定する。 */
    fun setInterval(isVariable: Boolean, startInterval: Int, endInterval: Int) {
        val clampedStart = startInterval.coerceIn(CUSTOM_INTERVAL_MIN, CUSTOM_INTERVAL_MAX)
        val clampedEnd = endInterval.coerceIn(CUSTOM_INTERVAL_MIN, CUSTOM_INTERVAL_MAX)
        uiState = revalidate(
            uiState.copy(
                isVariableInterval = isVariable,
                startIntervalMinutes = clampedStart,
                endIntervalMinutes = if (isVariable) clampedEnd else clampedStart,
            ),
        )
    }

    /** 曜日の有効・無効をトグルする。 */
    fun toggleDay(day: DayOfWeek) {
        val days = uiState.repeatDays.toMutableSet().apply {
            if (!add(day)) remove(day)
        }
        uiState = uiState.copy(repeatDays = days)
    }

    /** タームの有効・無効を切り替える。 */
    fun setEnabled(enabled: Boolean) {
        uiState = uiState.copy(enabled = enabled)
    }

    /** タームのラベルを設定する。 */
    fun setLabel(label: String) {
        uiState = uiState.copy(label = label)
    }

    /** 解除チャレンジのタイミングと難易度を設定する。 */
    fun setChallenge(timing: ChallengeTiming, level: ChallengeLevel) {
        uiState = uiState.copy(challengeTiming = timing, challenge = level)
    }

    /** 解除チャレンジの出題タイミングを設定する。 */
    fun setChallengeTiming(timing: ChallengeTiming) {
        uiState = uiState.copy(challengeTiming = timing)
    }

    /** 解除チャレンジの難易度を設定する。 */
    fun setChallenge(level: ChallengeLevel) {
        uiState = uiState.copy(challenge = level)
    }

    /** 二度寝チェックの有無を設定する。 */
    fun setWakeCheck(wakeCheck: Boolean) {
        uiState = uiState.copy(wakeCheck = wakeCheck)
    }

    /**
     * 保存前の入力検証を行う。
     * domain.occurrenceCountを用いて、鳴動回数が1回以上存在するかを確かめる。
     */
    private fun revalidate(state: AlarmEditUiState): AlarmEditUiState {
        val error = when {
            state.startIntervalMinutes <= 0 || (state.isVariableInterval && state.endIntervalMinutes <= 0) ->
                AlarmEditValidationError.INTERVAL_NOT_POSITIVE
            state.startMinutes != state.endMinutes && occurrenceCount(state.toSchedule(null)) <= 1 ->
                AlarmEditValidationError.INTERVAL_TOO_LARGE
            else -> null
        }
        return state.copy(validationError = error)
    }

    /** タームの変更または新規登録を保存する。 */
    fun save() {
        val validated = revalidate(uiState)
        uiState = validated
        if (validated.validationError != null) return
        viewModelScope.launch {
            val schedule = validated.toSchedule(loadedSkippedSessionStart)
            val savedId = if (validated.id == null) {
                repository.add(schedule)
            } else {
                repository.update(schedule)
                validated.id
            }
            AlarmScheduler.reschedule(appContext, savedId)
            // 登録したidを持たせる。持たせないと、続けてもう一度押したときにもう1件できてしまう
            uiState = uiState.copy(id = savedId, isSaved = true)
        }
    }

    /** タームを削除する。 */
    fun delete() {
        val id = uiState.id ?: return
        viewModelScope.launch {
            repository.delete(uiState.toSchedule(loadedSkippedSessionStart).copy(id = id))
            AlarmScheduler.cancel(appContext, id)
            uiState = uiState.copy(isDeleted = true)
        }
    }
}

/** 依存注入を用いずにViewModelを生成するファクトリクラス。通常アラームとしての起動フラグを保持する。 */
class AlarmEditViewModelFactory(
    private val repository: AlarmRepository,
    private val context: Context,
    private val alarmId: Long?,
    private val isSingleAlarm: Boolean = false,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AlarmEditViewModel(repository, context, alarmId, isSingleAlarm) as T
}
