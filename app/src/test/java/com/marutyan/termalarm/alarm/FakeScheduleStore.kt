package com.marutyan.termalarm.alarm

import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.isOneShotSessionFinished
import com.marutyan.termalarm.domain.sessionStartDate
import java.time.ZonedDateTime

/**
 * テスト用の[ScheduleStore]。保存は本物のRepositoryへ通し、予約の操作は記録だけ残す。
 *
 * 「保存はされるが予約が入っていない」という不具合が繰り返し起きたため、
 * テストから予約の呼び出しを見られるようにする。
 */
class FakeScheduleStore(private val repository: AlarmRepository) : ScheduleStore {

    /** 予約を入れ直した対象のid。呼ばれた順に並ぶ。 */
    val rescheduled = mutableListOf<Long>()

    /** 予約を取り消した対象のid。呼ばれた順に並ぶ。 */
    val cancelled = mutableListOf<Long>()

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        repository.setEnabled(id, enabled)
        if (enabled) rescheduled += id else cancelled += id
    }

    override suspend fun update(schedule: AlarmSchedule) {
        repository.update(schedule)
        if (schedule.enabled) rescheduled += schedule.id else cancelled += schedule.id
    }

    override suspend fun endSession(id: Long, at: ZonedDateTime) {
        val before = repository.getById(id) ?: return
        repository.update(before.copy(skippedSessionStart = sessionStartDate(before, at)))
        val after = repository.getById(id) ?: return
        // 本物と同じく、曜日を指定していないタームは鳴り終わったらオフにする
        if (isOneShotSessionFinished(after, at)) {
            repository.setEnabled(id, false)
            cancelled += id
        } else {
            rescheduled += id
        }
    }
}
