package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * タイマーの永続化を担うリポジトリ。公開APIはdomain.TimerStateのみを扱い、
 * Room固有の型(TimerEntity)をtimer/ui層に漏らさない(data.AlarmRepositoryと同じ方針)。
 */
class TimerRepository(private val dao: TimerDao) {

    fun observeAll(): Flow<List<TimerState>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getById(id: Long): TimerState? = dao.getById(id)?.toDomain()

    // 端末再起動直後に再アンカーする対象を取得する(TimerBootReceiver専用)
    suspend fun getAllRunningOnce(): List<TimerState> = dao.getAllRunningOnce().map { it.toDomain() }

    // 新規追加。渡されたstateのidは無視され、自動採番された新しいidが返る
    suspend fun add(state: TimerState): Long = dao.insert(state.toEntity())

    suspend fun update(state: TimerState) = dao.update(state.toEntity())

    suspend fun delete(id: Long) = dao.deleteById(id)

    /**
     * 動作中または鳴動中のタイマーが1件でもあるか。
     * フォアグラウンドサービスを止めてよいかの判定に使う。一覧はFlowで流すが、
     * この判定に必要なのは現在の件数だけなので都度問い合わせる。
     */
    suspend fun hasActiveTimer(): Boolean =
        dao.countByRunStates(listOf(TimerRunState.RUNNING.name, TimerRunState.FINISHED.name)) > 0
}
