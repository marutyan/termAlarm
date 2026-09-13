package com.marutyan.termalarm.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ホーム画面のデータ状態と操作イベントを管轄するViewModel。
 * AlarmRepositoryを通じてターム一覧を購読し、トグル状態の永続化を行うために用いる。
 */
class HomeViewModel(
    private val repository: AlarmRepository,
) : ViewModel() {

    /** 登録されている全タームの最新一覧を提供するStateFlow。 */
    val terms: StateFlow<List<AlarmSchedule>> = repository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    /**
     * 指定されたタームの有効・無効状態を更新する。
     * リポジトリ経由でデータベース内のenabledフラグを書き換えるために用いる。
     */
    fun toggleEnabled(schedule: AlarmSchedule, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(schedule.id, enabled)
        }
    }
}

/**
 * HomeViewModelのインスタンスを生成するファクトリクラス。
 * 必要な依存リポジトリを注入してViewModelを初期化するために用いる。
 */
class HomeViewModelFactory(
    private val repository: AlarmRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
