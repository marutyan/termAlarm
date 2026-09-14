package com.marutyan.termalarm.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.alarm.AlarmScheduler
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import java.time.DayOfWeek
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ホーム画面のデータ状態と操作イベントを管轄するViewModel。
 * AlarmRepositoryを通じてターム一覧を購読し、トグル状態の永続化を行うために用いる。
 */
class HomeViewModel(
    private val repository: AlarmRepository,
    // 予約を入れ直すために要る。渡されない場合は保存だけ行う（テストで画面だけを動かすため）
    private val appContext: Context? = null,
) : ViewModel() {

    /** 登録されているタームの最新一覧を提供するStateFlow。通常アラーム（開始と終了が同じもの）は除外する。 */
    val terms: StateFlow<List<AlarmSchedule>> = repository.observeAll()
        .map { list -> list.filter { it.startMinutes != it.endMinutes } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    /**
     * 指定されたタームの有効・無効状態を更新する。
     * 保存するだけでなく予約も入れ直す。保存だけだと、切り替えても鳴らない・鳴り続けるため。
     */
    fun toggleEnabled(schedule: AlarmSchedule, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(schedule.id, enabled)
            applySchedule(schedule.id, enabled)
        }
    }

    /**
     * カードの上で押された曜日を、繰り返す曜日へ入れたり外したりする。
     * 編集画面を開かずにその場で直せるようにするために用いる。
     */
    fun toggleDay(schedule: AlarmSchedule, day: DayOfWeek) {
        viewModelScope.launch {
            val days = if (day in schedule.repeatDays) {
                schedule.repeatDays - day
            } else {
                schedule.repeatDays + day
            }
            repository.update(schedule.copy(repeatDays = days))
            applySchedule(schedule.id, schedule.enabled)
        }
    }

    // 保存した内容で予約を入れ直す。無効にしたときは取り消す
    private suspend fun applySchedule(id: Long, enabled: Boolean) {
        val context = appContext ?: return
        if (enabled) {
            AlarmScheduler.reschedule(context, id)
        } else {
            AlarmScheduler.cancel(context, id)
        }
    }
}

/**
 * HomeViewModelのインスタンスを生成するファクトリクラス。
 * 必要な依存リポジトリを注入してViewModelを初期化するために用いる。
 */
class HomeViewModelFactory(
    private val repository: AlarmRepository,
    private val appContext: Context? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(repository, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
