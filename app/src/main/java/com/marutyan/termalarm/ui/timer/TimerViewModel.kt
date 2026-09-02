package com.marutyan.termalarm.ui.timer

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.extendTimer
import com.marutyan.termalarm.domain.pauseTimer
import com.marutyan.termalarm.domain.resetTimer
import com.marutyan.termalarm.domain.resumeTimer
import com.marutyan.termalarm.domain.startTimer
import com.marutyan.termalarm.timer.TimerForegroundService
import com.marutyan.termalarm.timer.TimerScheduler
import com.marutyan.termalarm.timer.formatDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 「延長」ボタン1回あたりの延長幅。docs/SPEC.md「動作中に1分単位で延長できる」に合わせ固定値とする
private const val EXTEND_STEP_MILLIS = 60_000L

/**
 * タイマータブの状態を持つViewModel。RepositoryのFlowをそのままUI状態として公開し、
 * 開始・一時停止・再開・延長・リセット・削除の各操作を仲介する。
 * Repositoryを変更した直後は必ずTimerScheduler.rescheduleとTimerForegroundService.ensureRunningを
 * 呼び直す契約とする(alarm/AlarmListViewModelと同じ方針)。
 */
class TimerViewModel(private val repository: TimerRepository, context: Context) : ViewModel() {

    // PendingIntent発行やサービス起動にはApplication Contextで十分なため、生成時点で切り替えて保持する
    private val appContext: Context = context.applicationContext

    // DBの変更が自動的に反映される一覧。残り時間そのものは画面側でtick(1秒ごと)ごとに再計算する
    val timers: StateFlow<List<TimerState>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 指定した時分秒で新規タイマーを開始する。合計0秒は呼び出し側(画面)がボタンを無効化して防ぐ
    fun start(hours: Int, minutes: Int, seconds: Int) {
        val durationMillis = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
        if (durationMillis <= 0L) return
        viewModelScope.launch {
            val now = SystemClock.elapsedRealtime()
            val nowWall = System.currentTimeMillis()
            // ラベルは開始時に指定した時間の表示("5:00"など)をそのまま使う。複数タイマーを見分けられれば十分で、
            // 個別のラベル入力欄は今回のSPECの要求に無いため作らない
            val label = formatDuration(durationMillis)
            val state = startTimer(
                id = 0L,
                label = label,
                durationMillis = durationMillis,
                nowElapsedRealtime = now,
                nowWallClockMillis = nowWall,
            )
            val id = repository.add(state)
            afterMutation(id)
        }
    }

    fun pause(id: Long) = mutate(id, ::pauseTimer)
    fun resume(id: Long) = mutate(id, ::resumeTimer)
    fun reset(id: Long) = mutate(id, ::resetTimer)

    fun extendOneMinute(id: Long) = mutate(id) { state, now, nowWall ->
        extendTimer(state, EXTEND_STEP_MILLIS, now, nowWall)
    }

    // 削除。FINISHED(鳴動中)の「停止」ボタンも同じ操作として扱う
    // (domain/TimerState.ktの「停止するとタイマー自体を削除する想定」)
    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            TimerScheduler.cancel(appContext, id)
        }
    }

    private fun mutate(id: Long, transform: (TimerState, Long, Long) -> TimerState) {
        viewModelScope.launch {
            val state = repository.getById(id) ?: return@launch
            val now = SystemClock.elapsedRealtime()
            val nowWall = System.currentTimeMillis()
            repository.update(transform(state, now, nowWall))
            afterMutation(id)
        }
    }

    // 状態変更のたびにAlarmManager予約とフォアグラウンドサービスの両方を最新の状態へ合わせ直す
    private suspend fun afterMutation(id: Long) {
        TimerScheduler.reschedule(appContext, id)
        TimerForegroundService.ensureRunning(appContext)
    }
}

// 依存注入フレームワークを使わないための手作りファクトリ
class TimerViewModelFactory(private val repository: TimerRepository, private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = TimerViewModel(repository, context) as T
}
