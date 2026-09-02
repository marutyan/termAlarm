package com.marutyan.termalarm.ui.stopwatch

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.data.StopwatchRepository
import com.marutyan.termalarm.domain.StopwatchLap
import com.marutyan.termalarm.domain.StopwatchRunState
import com.marutyan.termalarm.domain.StopwatchState
import com.marutyan.termalarm.domain.elapsedMillis
import com.marutyan.termalarm.domain.pauseStopwatch
import com.marutyan.termalarm.domain.recordLap
import com.marutyan.termalarm.domain.resetStopwatch
import com.marutyan.termalarm.domain.resumeStopwatch
import com.marutyan.termalarm.domain.startStopwatch
import com.marutyan.termalarm.stopwatch.StopwatchForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ストップウォッチタブの状態を持つViewModel。RepositoryのFlowをそのままUI状態として公開し、
 * 開始・一時停止・再開・ラップ・リセットの各操作を仲介する。状態を書き換えたあとは
 * StopwatchForegroundService.ensureRunningを呼び直し、RUNNINGになった場合だけ通知が出る契約とする
 * (timer機能のTimerViewModelと同じ方針)。
 */
class StopwatchViewModel(private val repository: StopwatchRepository, context: Context) : ViewModel() {

    // PendingIntent発行やサービス起動にはApplication Contextで十分なため、生成時点で切り替えて保持する
    private val appContext: Context = context.applicationContext

    // DBの変更が自動的に反映される状態。経過時間そのものは画面側でtick(100ms/1sごと)ごとに再計算する
    val state: StateFlow<StopwatchState> = repository.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StopwatchState.INITIAL)

    val laps: StateFlow<List<StopwatchLap>> = repository.observeLaps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun start() = viewModelScope.launch {
        val (now, nowWall) = nowPair()
        repository.updateState(startStopwatch(now, nowWall))
        StopwatchForegroundService.ensureRunning(appContext)
    }

    fun pause() = mutate(::pauseStopwatch)

    fun resume() = viewModelScope.launch {
        val current = repository.getStateOnce()
        val (now, nowWall) = nowPair()
        repository.updateState(resumeStopwatch(current, now, nowWall))
        StopwatchForegroundService.ensureRunning(appContext)
    }

    // リセットは経過時間を0に戻すと同時にラップも全て消す(docs/SPEC.md「リセット」)
    fun reset() = viewModelScope.launch {
        val (now, nowWall) = nowPair()
        repository.clearLaps()
        repository.updateState(resetStopwatch(now, nowWall))
    }

    // 動作中(RUNNING)以外での呼び出しは無視する。画面側もRUNNING時だけボタンを出すが、二重の安全策とする
    fun lap() = viewModelScope.launch {
        val current = repository.getStateOnce()
        if (current.runState != StopwatchRunState.RUNNING) return@launch
        val (now, nowWall) = nowPair()
        val total = elapsedMillis(current, now, nowWall)
        repository.addLap(recordLap(total, repository.getLapsOnce()))
    }

    private fun mutate(transform: (StopwatchState, Long, Long) -> StopwatchState) {
        viewModelScope.launch {
            val current = repository.getStateOnce()
            val (now, nowWall) = nowPair()
            repository.updateState(transform(current, now, nowWall))
        }
    }

    private fun nowPair(): Pair<Long, Long> = SystemClock.elapsedRealtime() to System.currentTimeMillis()
}

// 依存注入フレームワークを使わないための手作りファクトリ
class StopwatchViewModelFactory(private val repository: StopwatchRepository, private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = StopwatchViewModel(repository, context) as T
}
