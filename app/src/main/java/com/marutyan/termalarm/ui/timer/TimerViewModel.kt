package com.marutyan.termalarm.ui.timer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.timer.TimerActions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * タイマータブの状態を持つViewModel。RepositoryのFlowをそのままUI状態として公開し、
 * 開始・一時停止・再開・延長・リセット・削除の各操作はTimerActionsへ渡すだけにする。
 * 保存・予約の入れ直し・通知の出し直し・サービスの起動停止が必ず揃うようにするため、
 * 画面側に手順を書き写さない。
 */
class TimerViewModel(private val repository: TimerRepository, context: Context) : ViewModel() {

    // PendingIntent発行やサービス起動にはApplication Contextで十分なため、生成時点で切り替えて保持する
    private val appContext: Context = context.applicationContext

    // DBの変更が自動的に反映される一覧。残り時間そのものは画面側でtick(1秒ごと)ごとに再計算する。
    // 読み込みが終わるまではnullを流す。先に空リストを流すと、画面が「0件」と判断して
    // 新規作成の画面を一瞬出してしまうため、「まだ分からない」を空と区別できるようにしている
    val timers: StateFlow<List<TimerState>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 指定した時分秒で新規タイマーを開始する。合計0秒は呼び出し側(画面)がボタンを無効化して防ぐ
    fun start(hours: Int, minutes: Int, seconds: Int) {
        val durationMillis = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
        if (durationMillis <= 0L) return
        viewModelScope.launch { TimerActions.start(appContext, durationMillis) }
    }

    fun pause(id: Long) = run { TimerActions.pause(appContext, id) }
    fun resume(id: Long) = run { TimerActions.resume(appContext, id) }
    fun reset(id: Long) = run { TimerActions.stop(appContext, id) }
    fun extendOneMinute(id: Long) = run { TimerActions.extendOneMinute(appContext, id) }

    // 削除。FINISHED(鳴動中)の「停止」ボタンも同じ操作として扱う
    // (domain/TimerState.ktの「停止するとタイマー自体を削除する想定」)
    fun delete(id: Long) {
        // 手順をここへ書き写さない。通知の側と同じ道を通す
        viewModelScope.launch { TimerActions.delete(appContext, id) }
    }

    // 画面からの操作も、通知のボタンと同じ道(TimerActions)を通す。
    // 保存・予約の入れ直し・通知の出し直し・サービスの起動停止を、画面側へ書き写さないため
    private fun run(action: suspend () -> Unit) {
        viewModelScope.launch { action() }
    }
}

// 依存注入フレームワークを使わないための手作りファクトリ
class TimerViewModelFactory(private val repository: TimerRepository, private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = TimerViewModel(repository, context) as T
}
