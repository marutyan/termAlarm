package com.marutyan.termalarm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.data.ClockSettingsRepository
import com.marutyan.termalarm.domain.AlarmDismissMethod
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.ClockDisplayMode
import com.marutyan.termalarm.domain.VolumeButtonAction
import com.marutyan.termalarm.domain.WeekStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 設定画面の状態を持つViewModel。設定は読み書きの単位が細かい(13項目)ため、変更は
 * 「今のsettings.valueをcopyして丸ごと保存し直す」方式に統一し、項目ごとの保存処理を増やさない。
 * 時計のアナログ/デジタル表示だけは既存のClockSettingsRepository(clock_settingsテーブル)へ委譲する
 * (時計タブ側と同じ設定を二重に持たないため)。
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val clockRepository: ClockSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val clockDisplayMode: StateFlow<ClockDisplayMode> = clockRepository.observeDisplayMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClockDisplayMode.DIGITAL)

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform(settings.value)) }
    }

    fun setDismissMethod(method: AlarmDismissMethod) = update { it.copy(dismissMethod = method) }
    fun setAutoStopMinutes(minutes: Int) = update { it.copy(autoStopMinutes = minutes) }
    fun setDefaultSnoozeMinutes(minutes: Int) = update { it.copy(defaultSnoozeMinutes = minutes) }
    fun setAlarmFadeInSeconds(seconds: Int) = update { it.copy(alarmFadeInSeconds = seconds) }
    fun setVolumeButtonAction(action: VolumeButtonAction) = update { it.copy(volumeButtonAction = action) }
    fun setWeekStart(weekStart: WeekStart) = update { it.copy(weekStart = weekStart) }
    fun setShowClockSeconds(show: Boolean) = update { it.copy(showClockSeconds = show) }
    fun setTimerSoundUri(uri: String?) = update { it.copy(timerSoundUri = uri) }
    fun setTimerFadeInSeconds(seconds: Float) = update { it.copy(timerFadeInSeconds = seconds) }
    fun setTimerVibration(enabled: Boolean) = update { it.copy(timerVibration = enabled) }

    fun setClockDisplayMode(mode: ClockDisplayMode) {
        viewModelScope.launch { clockRepository.setDisplayMode(mode) }
    }
}

// 依存注入フレームワークを使わないための手作りファクトリ(ClockViewModelFactoryと同じ方針)
class SettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val clockRepository: ClockSettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(repository, clockRepository) as T
}
