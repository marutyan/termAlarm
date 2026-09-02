package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.sessionStartDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * アラームの永続化を担うリポジトリ。公開APIはdomain.AlarmScheduleのみを扱い、
 * Room固有の型(AlarmScheduleEntity)をui/alarm層に漏らさない。
 */
class AlarmRepository(private val dao: AlarmDao) {

    // 一覧をFlowで取得する。DBの変更が自動的に反映される
    fun observeAll(): Flow<List<AlarmSchedule>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getById(id: Long): AlarmSchedule? = dao.getById(id)?.toDomain()

    // 新規追加。渡されたscheduleのidは無視され、自動採番された新しいidが返る
    suspend fun add(schedule: AlarmSchedule): Long = dao.insert(schedule.toEntity())

    suspend fun update(schedule: AlarmSchedule) = dao.update(schedule.toEntity())

    suspend fun delete(schedule: AlarmSchedule) = dao.delete(schedule.toEntity())

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    // 「今日はもう止める」を実行する。nowが属するセッションの開始日をskippedSessionStartに書き込み、
    // そのセッションの残りの鳴動をすべて対象外にする（次のセッションは通常どおり予約される、docs/SPEC.md「当日終了の導線」）。
    // 対象idのアラームが存在しない場合は何もしない。
    suspend fun endTodaySession(id: Long, now: ZonedDateTime) {
        val schedule = getById(id) ?: return
        update(schedule.copy(skippedSessionStart = sessionStartDate(schedule, now)))
    }
}
