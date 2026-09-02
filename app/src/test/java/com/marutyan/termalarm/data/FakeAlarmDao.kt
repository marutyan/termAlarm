package com.marutyan.termalarm.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * AlarmDaoのテスト用インメモリ実装。Roomの実DB(Robolectric等)を使わずにAlarmRepositoryの
 * ロジックだけをJVM単体テストで検証するためのもの。実装は最小限（自動採番とCRUD）に留める。
 */
class FakeAlarmDao : AlarmDao {
    private val state = MutableStateFlow<List<AlarmScheduleEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<AlarmScheduleEntity>> = state

    override suspend fun getById(id: Long): AlarmScheduleEntity? = state.value.find { it.id == id }

    override suspend fun insert(entity: AlarmScheduleEntity): Long {
        val id = nextId++
        state.value = state.value + entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: AlarmScheduleEntity) {
        state.value = state.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun delete(entity: AlarmScheduleEntity) {
        state.value = state.value.filterNot { it.id == entity.id }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }
}
