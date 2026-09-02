package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.ClockDisplayMode
import com.marutyan.termalarm.domain.WorldClockCity
import com.marutyan.termalarm.domain.movedCity
import com.marutyan.termalarm.domain.withoutCity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 世界時計の都市一覧と表示設定(アナログ/デジタル)の永続化を担うリポジトリ。
 * 公開APIはdomain層の型のみを扱い、Room固有の型(Entity)をui/clock層に漏らさない。
 */
class WorldClockRepository(
    private val cityDao: WorldClockCityDao,
    private val settingsDao: ClockSettingsDao,
) {
    fun observeCities(): Flow<List<WorldClockCity>> =
        cityDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    // 一覧の末尾に都市を追加する。sortOrderは既存の最大値+1(未登録なら0)
    suspend fun addCity(zoneId: String) {
        val nextOrder = cityDao.maxSortOrder() + 1
        cityDao.insert(WorldClockCityEntity(id = 0, zoneId = zoneId, sortOrder = nextOrder))
    }

    suspend fun removeCity(id: Long) {
        cityDao.deleteById(id)
        val remaining = cityDao.getAll().map { it.toDomain() }.withoutCity(id)
        cityDao.updateAll(remaining.map { it.toEntity() })
    }

    suspend fun moveCity(fromIndex: Int, toIndex: Int) {
        val moved = cityDao.getAll().map { it.toDomain() }.movedCity(fromIndex, toIndex)
        cityDao.updateAll(moved.map { it.toEntity() })
    }

    // 未設定(初回起動)ならデジタル表示を既定にする
    fun observeDisplayMode(): Flow<ClockDisplayMode> =
        settingsDao.observe().map { it?.toDomain() ?: ClockDisplayMode.DIGITAL }

    suspend fun setDisplayMode(mode: ClockDisplayMode) = settingsDao.upsert(mode.toEntity())
}
