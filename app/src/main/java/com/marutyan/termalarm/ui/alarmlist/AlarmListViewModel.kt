package com.marutyan.termalarm.ui.alarmlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.alarm.AlarmScheduler
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * アラーム一覧画面の状態を持つViewModel。RepositoryのFlowをそのままUI状態として公開し、
 * 有効/無効切り替え・削除・「今日はもう止める」の実行を仲介する。
 * Repositoryを変更した直後は必ずAlarmScheduler.reschedule/cancelを呼び直す契約(alarm/AlarmScheduler.kt参照)。
 */
class AlarmListViewModel(private val repository: AlarmRepository, context: Context) : ViewModel() {

    // PendingIntent発行やRoomアクセスにはApplication Contextで十分なため、生成時点で切り替えて保持する
    private val appContext: Context = context.applicationContext

    // DBの変更が自動的に反映される一覧。画面が破棄されてもしばらく購読を維持し、再表示時の再読込を避ける
    val alarms: StateFlow<List<AlarmSchedule>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(id, enabled)
            AlarmScheduler.reschedule(appContext, id)
        }
    }

    fun delete(schedule: AlarmSchedule) {
        viewModelScope.launch {
            repository.delete(schedule)
            AlarmScheduler.cancel(appContext, schedule.id)
        }
    }

    // 「今日はもう止める」を実行する。ゲームの正解判定は呼び出し側(SkipGame画面)で完結させ、
    // ここでは当日終了の書き込みと、次回セッションの再予約を行う(docs/SPEC.md「当日終了の導線」)。
    fun endTodaySession(id: Long) {
        viewModelScope.launch {
            repository.endTodaySession(id, ZonedDateTime.now())
            AlarmScheduler.reschedule(appContext, id)
        }
    }
}

// 依存注入フレームワークを使わないための手作りファクトリ
class AlarmListViewModelFactory(private val repository: AlarmRepository, private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AlarmListViewModel(repository, context) as T
}
