package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.StopwatchLap
import com.marutyan.termalarm.domain.StopwatchState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * ストップウォッチの永続化を担うリポジトリ。公開APIはdomain.StopwatchState/StopwatchLapのみを扱い、
 * Room固有の型(Entity)をstopwatch/ui層に漏らさない(data.TimerRepositoryと同じ方針)。
 * 状態の遷移計算はdomain層(StopwatchCalculator)の責務とし、ここでは読み書きだけを行う。
 */
class StopwatchRepository(private val dao: StopwatchDao) {

    // 行がまだ無い(初回起動時)場合はStopwatchState.INITIALを返す
    fun observeState(): Flow<StopwatchState> =
        dao.observeState().map { it?.toDomain() ?: StopwatchState.INITIAL }

    suspend fun getStateOnce(): StopwatchState = dao.getStateOnce()?.toDomain() ?: StopwatchState.INITIAL

    suspend fun updateState(state: StopwatchState) = dao.upsertState(state.toEntity())

    fun observeLaps(): Flow<List<StopwatchLap>> =
        dao.observeLaps().map { entities -> entities.map { it.toDomain() } }

    // ラップ記録時、直前ラップとの差分計算(domain.recordLap)に必要な既存ラップ一覧を取得する
    suspend fun getLapsOnce(): List<StopwatchLap> = observeLaps().first()

    suspend fun addLap(lap: StopwatchLap) = dao.insertLap(lap.toEntity())

    suspend fun clearLaps() = dao.clearLaps()
}
