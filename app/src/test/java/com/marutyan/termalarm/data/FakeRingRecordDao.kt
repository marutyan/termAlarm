package com.marutyan.termalarm.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * RingRecordDaoのテスト用インメモリ実装。
 * Roomの実DBを使わずにWakeRecordRepositoryのロジックをJVM単体テストで検証するために用いる。
 */
class FakeRingRecordDao : RingRecordDao {
    private val records = MutableStateFlow<List<RingRecordEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(record: RingRecordEntity): Long {
        val id = nextId++
        records.value = records.value + record.copy(id = id)
        return id
    }

    override fun observeFrom(fromEpochDay: Long): Flow<List<RingRecordEntity>> =
        records.map { list ->
            list.filter { it.sessionStart >= fromEpochDay }.sortedBy { it.scheduledAt }
        }

    override fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<RingRecordEntity>> =
        records.map { list ->
            list.filter { it.sessionStart in fromEpochDay..toEpochDay }.sortedBy { it.scheduledAt }
        }

    override fun observeAll(): Flow<List<RingRecordEntity>> =
        records.map { list -> list.sortedBy { it.scheduledAt } }

    override suspend fun getBetween(fromEpochDay: Long, toEpochDay: Long): List<RingRecordEntity> =
        records.value.filter { it.sessionStart in fromEpochDay..toEpochDay }.sortedBy { it.scheduledAt }

    override suspend fun getAll(): List<RingRecordEntity> =
        records.value.sortedBy { it.scheduledAt }
}
