package com.marutyan.termalarm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.data.ClockSettingsRepository
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.ClockDisplayMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 設定画面の状態を持つViewModel。設定は読み書きの単位が細かいため、変更は
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

    fun setAlarmSoundUri(uri: String?) = update { it.copy(alarmSoundUri = uri) }
    fun setVibration(enabled: Boolean) = update { it.copy(vibration = enabled) }
    fun setFadeInSeconds(seconds: Int) = update { it.copy(fadeInSeconds = seconds) }
    fun setSilenceAfterMinutes(minutes: Int?) = update { it.copy(silenceAfterMinutes = minutes) }
    fun setWakeCheckMinutes(minutes: Int) = update { it.copy(wakeCheckMinutes = minutes) }

    /**
     * アプリ全体の配色テーマを更新する。
     * NAVY / LIGHT / BLACK / DYNAMICの指定値をDBに保存するために用いる。
     */
    fun setTheme(theme: com.marutyan.termalarm.domain.AppTheme) = update { it.copy(theme = theme) }

    /**
     * 出題を有効にするミニゲームの集合を更新する。
     * 1つ以上のゲームが選択されている場合のみ保存を反映する。
     */
    fun setEnabledGames(games: Set<com.marutyan.termalarm.domain.GameType>) {
        if (games.isNotEmpty()) {
            update { it.copy(enabledGames = games) }
        }
    }

    /**
     * 指定されたミニゲームの有効/無効を切り替える。
     * 選択数が1つの状態で最後の1つを解除しようとした場合は変更を拒否し、常に1つ以上が選ばれた状態を維持する。
     */
    fun toggleGame(game: com.marutyan.termalarm.domain.GameType) {
        val current = settings.value.enabledGames
        if (game in current) {
            if (current.size > 1) {
                update { it.copy(enabledGames = current - game) }
            }
        } else {
            update { it.copy(enabledGames = current + game) }
        }
    }

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
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository, clockRepository) as T
}
