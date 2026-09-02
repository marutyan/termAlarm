package com.marutyan.termalarm.ui.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.data.WorldClockRepository
import com.marutyan.termalarm.domain.ClockDisplayMode
import com.marutyan.termalarm.domain.WorldClockCity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 時計タブの状態を持つViewModel。RepositoryのFlow(都市一覧・表示設定)をそのままUI状態として公開し、
 * 都市の追加・削除・並べ替えと表示モードの切り替えを仲介する。
 */
class ClockViewModel(private val repository: WorldClockRepository) : ViewModel() {

    val cities: StateFlow<List<WorldClockCity>> = repository.observeCities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val displayMode: StateFlow<ClockDisplayMode> = repository.observeDisplayMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClockDisplayMode.DIGITAL)

    fun addCity(zoneId: String) {
        viewModelScope.launch { repository.addCity(zoneId) }
    }

    fun removeCity(id: Long) {
        viewModelScope.launch { repository.removeCity(id) }
    }

    fun moveCity(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { repository.moveCity(fromIndex, toIndex) }
    }

    fun setDisplayMode(mode: ClockDisplayMode) {
        viewModelScope.launch { repository.setDisplayMode(mode) }
    }
}

// 依存注入フレームワークを使わないための手作りファクトリ
class ClockViewModelFactory(private val repository: WorldClockRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ClockViewModel(repository) as T
}
