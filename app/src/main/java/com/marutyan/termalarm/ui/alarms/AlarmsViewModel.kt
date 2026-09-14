package com.marutyan.termalarm.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.alarm.ScheduleStore
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 通常アラーム画面のデータ状態と操作イベントを管轄するViewModel。
 * リポジトリから単発アラーム（開始と終了が同一のスケジュール）のみを購読し、有効・無効の切り替えを行う。
 */
class AlarmsViewModel(
    private val repository: AlarmRepository,
    // 保存と予約の入れ直しを対で行う窓口。ホームの一覧と同じものを使う
    private val scheduleStore: ScheduleStore,
) : ViewModel() {

    /** 登録されている通常アラームの最新一覧を提供するStateFlow。開始と終了が異なるタームは除外する。 */
    val alarms: StateFlow<List<AlarmSchedule>> = repository.observeAll()
        .map { list -> list.filter { it.startMinutes == it.endMinutes } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    /**
     * 指定された通常アラームの有効・無効状態を更新する。
     * 保存するだけでなく予約も入れ直す。保存だけだと、オンにしても鳴らず、オフにしても鳴り続ける。
     */
    fun toggleEnabled(schedule: AlarmSchedule, enabled: Boolean) {
        viewModelScope.launch { scheduleStore.setEnabled(schedule.id, enabled) }
    }
}

/**
 * AlarmsViewModelのインスタンスを生成するファクトリクラス。
 * 必要なAlarmRepositoryを注入してViewModelを初期化するために用いる。
 */
class AlarmsViewModelFactory(
    private val repository: AlarmRepository,
    private val scheduleStore: ScheduleStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmsViewModel::class.java)) {
            return AlarmsViewModel(repository, scheduleStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
