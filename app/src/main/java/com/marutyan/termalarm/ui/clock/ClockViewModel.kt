package com.marutyan.termalarm.ui.clock

import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.domain.AppSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.data.ClockSettingsRepository
import com.marutyan.termalarm.domain.ClockDisplayMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 時計タブの状態を持つViewModel。Repositoryの表示設定(アナログ/デジタル)のFlowをそのままUI状態として
 * 公開し、切り替え操作を仲介する。
 */
class ClockViewModel(
    private val repository: ClockSettingsRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // 「時刻に秒を表示」の設定。画面が自分でDBを開くと、テストが渡した差し替え先と食い違うため
    // ここで受け取ったRepositoryだけを使う
    val appSettings: StateFlow<AppSettings> = settingsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val displayMode: StateFlow<ClockDisplayMode> = repository.observeDisplayMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClockDisplayMode.DIGITAL)

    fun setDisplayMode(mode: ClockDisplayMode) {
        viewModelScope.launch { repository.setDisplayMode(mode) }
    }
}

// 依存注入フレームワークを使わないための手作りファクトリ
class ClockViewModelFactory(
    private val repository: ClockSettingsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ClockViewModel(repository, settingsRepository) as T
}
