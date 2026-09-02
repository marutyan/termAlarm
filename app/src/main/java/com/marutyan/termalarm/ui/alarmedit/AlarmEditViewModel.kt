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
import com.marutyan.termalarm.domain.occurrenceCount
import java.time.DayOfWeek
import kotlinx.coroutines.launch

// 間隔選択チップに並べる既定値。これ以外の値は「その他」を選んだ扱いにする。
// 1分は実用の場面が乏しいうえ、鳴り止んですぐ次が鳴るため候補から外している
val INTERVAL_PRESETS_MINUTES = listOf(3, 5, 10, 15, 30)

/** 保存を妨げる入力エラーの種類。文言はComposable側でstringResourceへ変換する(ViewModelに文字列を持たせない)。 */
enum class AlarmEditValidationError { INTERVAL_NOT_POSITIVE, INTERVAL_TOO_LARGE, CUSTOM_INTERVAL_INVALID, SNOOZE_OUT_OF_RANGE }

// スヌーズ分数として許す範囲(1分〜60分)。0や負値、極端に長い値を保存できないようにする
const val SNOOZE_MINUTES_MIN = 1
const val SNOOZE_MINUTES_MAX = 60

/**
 * アラーム編集画面の入力状態。新規作成(id=null)と既存編集(id!=null)の両方をこの1つの形で表す。
 * skipRequiresApp/skipGame/snoozeMinutesはdocs/SPEC.md「誤操作の防止と当日終了」で追加された3設定。
 */
data class AlarmEditUiState(
    val id: Long? = null,
    val startMinutes: Int = 7 * 60,
    val endMinutes: Int = 9 * 60,
    val intervalMinutes: Int = 5,
    val useCustomInterval: Boolean = false,
    val customIntervalText: String = "5",
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
    val skipRequiresApp: Boolean = true,
    val skipGame: Boolean = false,
    val snoozeEnabled: Boolean = false,
    val snoozeMinutes: Int = 10,
    val isLoading: Boolean = false,
    val validationError: AlarmEditValidationError? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
) {
    // プレビュー・保存に使うAlarmSchedule。skippedSessionStartは編集画面から変更しないため既存値をそのまま保つ
    fun toSchedule(existingSkippedSessionStart: java.time.LocalDate?): AlarmSchedule = AlarmSchedule(
        id = id ?: 0L,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        intervalMinutes = intervalMinutes,
        repeatDays = repeatDays,
        label = label,
        soundUri = soundUri,
        vibrate = vibrate,
        enabled = enabled,
        skippedSessionStart = existingSkippedSessionStart,
        skipRequiresApp = skipRequiresApp,
        skipGame = skipRequiresApp && skipGame, // skipRequiresAppがfalseならskipGameは無視する(SPEC)
        snoozeMinutes = if (snoozeEnabled) snoozeMinutes else null,
    )
}

/**
 * アラーム追加・編集画面のViewModel。既存アラームの読込、入力検証、保存・削除を担う。
 * 依存注入フレームワークは使わず、コンストラクタ引数とファクトリで組み立てる。
 */
class AlarmEditViewModel(
    private val repository: AlarmRepository,
    context: Context,
    alarmId: Long?,
) : ViewModel() {

    // PendingIntent発行やRoomアクセスにはApplication Contextで十分なため、生成時点で切り替えて保持する
    private val appContext: Context = context.applicationContext

    var uiState by mutableStateOf(AlarmEditUiState(id = alarmId, isLoading = alarmId != null))
        private set

    // 保存時にskippedSessionStartを保つため、読み込んだ既存スケジュールを保持しておく
    private var loadedSkippedSessionStart: java.time.LocalDate? = null

    init {
        val id = alarmId
        if (id != null) {
            viewModelScope.launch {
                val schedule = repository.getById(id)
                uiState = if (schedule != null) {
                    loadedSkippedSessionStart = schedule.skippedSessionStart
                    AlarmEditUiState(
                        id = schedule.id,
                        startMinutes = schedule.startMinutes,
                        endMinutes = schedule.endMinutes,
                        intervalMinutes = schedule.intervalMinutes,
                        useCustomInterval = schedule.intervalMinutes !in INTERVAL_PRESETS_MINUTES,
                        customIntervalText = schedule.intervalMinutes.toString(),
                        repeatDays = schedule.repeatDays,
                        label = schedule.label,
                        soundUri = schedule.soundUri,
                        vibrate = schedule.vibrate,
                        enabled = schedule.enabled,
                        skipRequiresApp = schedule.skipRequiresApp,
                        skipGame = schedule.skipGame,
                        snoozeEnabled = schedule.snoozeMinutes != null,
                        snoozeMinutes = schedule.snoozeMinutes ?: 10,
                        isLoading = false,
                    )
                } else {
                    uiState.copy(isLoading = false)
                }
            }
        }
    }

    fun setStartMinutes(minutes: Int) { uiState = revalidate(uiState.copy(startMinutes = minutes)) }
    fun setEndMinutes(minutes: Int) { uiState = revalidate(uiState.copy(endMinutes = minutes)) }

    fun selectPresetInterval(minutes: Int) {
        uiState = revalidate(uiState.copy(intervalMinutes = minutes, useCustomInterval = false, customIntervalText = minutes.toString()))
    }

    fun selectCustomInterval() {
        uiState = uiState.copy(useCustomInterval = true)
    }

    fun setCustomIntervalText(text: String) {
        val minutes = text.toIntOrNull()
        uiState = if (minutes == null) {
            uiState.copy(customIntervalText = text, validationError = AlarmEditValidationError.CUSTOM_INTERVAL_INVALID)
        } else {
            revalidate(uiState.copy(customIntervalText = text, intervalMinutes = minutes))
        }
    }

    fun toggleDay(day: DayOfWeek) {
        val days = uiState.repeatDays.toMutableSet().apply { if (!add(day)) remove(day) }
        uiState = uiState.copy(repeatDays = days)
    }

    fun setLabel(label: String) { uiState = uiState.copy(label = label) }
    fun setSoundUri(uri: String?) { uiState = uiState.copy(soundUri = uri) }
    fun setVibrate(vibrate: Boolean) { uiState = uiState.copy(vibrate = vibrate) }

    fun setSkipRequiresApp(value: Boolean) {
        // オフにするとskipGameは選べなくなるため、あわせてfalseへ戻す(SPEC「skipRequiresAppがオフのときはskipGameを選べない」)
        uiState = uiState.copy(skipRequiresApp = value, skipGame = if (value) uiState.skipGame else false)
    }

    fun setSkipGame(value: Boolean) { uiState = uiState.copy(skipGame = value) }
    fun setSnoozeEnabled(value: Boolean) { uiState = revalidate(uiState.copy(snoozeEnabled = value)) }
    fun setSnoozeMinutes(minutes: Int) { uiState = revalidate(uiState.copy(snoozeMinutes = minutes)) }

    // 保存前の入力検証。UI側で計算式を再実装しないよう、判定にはdomain.occurrenceCountを使う(SPEC「要約はdomainの関数を使う」の精神を検証にも適用)
    private fun revalidate(state: AlarmEditUiState): AlarmEditUiState {
        val error = when {
            state.intervalMinutes <= 0 -> AlarmEditValidationError.INTERVAL_NOT_POSITIVE
            state.startMinutes != state.endMinutes && occurrenceCount(state.toSchedule(null)) <= 1 ->
                AlarmEditValidationError.INTERVAL_TOO_LARGE
            state.snoozeEnabled && state.snoozeMinutes !in SNOOZE_MINUTES_MIN..SNOOZE_MINUTES_MAX ->
                AlarmEditValidationError.SNOOZE_OUT_OF_RANGE
            else -> null
        }
        return state.copy(validationError = error)
    }

    fun save() {
        val validated = revalidate(uiState)
        uiState = validated
        if (validated.validationError != null) return
        viewModelScope.launch {
            val schedule = validated.toSchedule(loadedSkippedSessionStart)
            // 新規保存はadd()が採番したidを使う。保存前のid(null)のままではAlarmManagerへ登録できない
            val savedId = if (validated.id == null) repository.add(schedule) else { repository.update(schedule); validated.id }
            AlarmScheduler.reschedule(appContext, savedId)
            uiState = uiState.copy(isSaved = true)
        }
    }

    fun delete() {
        val id = uiState.id ?: return
        viewModelScope.launch {
            repository.delete(uiState.toSchedule(loadedSkippedSessionStart).copy(id = id))
            AlarmScheduler.cancel(appContext, id)
            uiState = uiState.copy(isDeleted = true)
        }
    }
}

// 依存注入フレームワークを使わないための手作りファクトリ
class AlarmEditViewModelFactory(
    private val repository: AlarmRepository,
    private val context: Context,
    private val alarmId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AlarmEditViewModel(repository, context, alarmId) as T
}
