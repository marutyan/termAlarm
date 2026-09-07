package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.ClockDisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 時計タブの表示設定(アナログ/デジタル)の永続化を担うリポジトリ。
 * 公開APIはdomain層の型のみを扱い、Room固有の型(Entity)をui/clock層に漏らさない。
 */
class ClockSettingsRepository(private val settingsDao: ClockSettingsDao) {

    // 未設定(初回起動)ならデジタル表示を既定にする
    fun observeDisplayMode(): Flow<ClockDisplayMode> =
        settingsDao.observe().map { it?.toDomain() ?: ClockDisplayMode.DIGITAL }

    suspend fun setDisplayMode(mode: ClockDisplayMode) = settingsDao.upsert(mode.toEntity())
}
