package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * アプリ全体の設定の永続化を担うリポジトリ。公開APIはdomain.AppSettingsのみを扱い、
 * Room固有の型(AppSettingsEntity)をui/settings層に漏らさない(AlarmRepository/WorldClockRepositoryと同じ方針)。
 */
class SettingsRepository(private val dao: AppSettingsDao) {

    // 未設定(初回起動)なら既定値(AppSettings())を返す
    fun observe(): Flow<AppSettings> = dao.observe().map { it?.toDomain() ?: AppSettings() }

    suspend fun update(settings: AppSettings) = dao.upsert(settings.toEntity())
}
