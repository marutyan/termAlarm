package com.marutyan.termalarm.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * AppSettingsDaoのテスト用インメモリ実装。FakeAlarmDaoと同じ方針で、Roomの実DBを使わず
 * SettingsRepositoryのロジックだけをJVM単体テストで検証する。
 */
class FakeAppSettingsDao : AppSettingsDao {
    private val state = MutableStateFlow<AppSettingsEntity?>(null)

    override fun observe(): Flow<AppSettingsEntity?> = state

    override suspend fun upsert(entity: AppSettingsEntity) {
        state.value = entity
    }
}
