package com.marutyan.termalarm.data

import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.RingRecord
import com.marutyan.termalarm.domain.SessionRecord
import com.marutyan.termalarm.domain.StopMethod
import com.marutyan.termalarm.domain.WakeAverages
import com.marutyan.termalarm.domain.autoSilencedRatio
import com.marutyan.termalarm.domain.averageWakeMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 起床記録および鳴動実績の永続化と集計アクセスを担うリポジトリ。
 * RingRecordDaoを通じて鳴動実績の保存と期間指定読み出しを行い、domainのSessionRecordや集計指標へ変換して提供する。
 */
class WakeRecordRepository(
    private val ringRecordDao: RingRecordDao,
    private val alarmDao: AlarmDao? = null,
) {

    /**
     * 鳴動1回分の実績を保存する。
     * 鳴動停止時または自動消音時に呼び出し、起床傾向集計の元データを蓄積するために用いる。
     */
    suspend fun record(
        alarmId: Long,
        sessionStart: Long,
        scheduledAt: Long,
        stoppedAt: Long?,
        stopMethod: String,
        occurrenceIndex: Int,
    ): Long {
        val entity = RingRecordEntity(
            alarmId = alarmId,
            sessionStart = sessionStart,
            scheduledAt = scheduledAt,
            stoppedAt = stoppedAt,
            stopMethod = stopMethod,
            occurrenceIndex = occurrenceIndex,
        )
        return ringRecordDao.insert(entity)
    }

    /**
     * 指定された日付期間（from〜to）に含まれるセッション記録のリストをFlowで監視する。
     * 今週や直近30日の起床実績を画面へリアクティブに反映するために用いる。
     */
    fun observeSessionsBetween(
        from: LocalDate,
        to: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<SessionRecord>> {
        return ringRecordDao.observeBetween(from.toEpochDay(), to.toEpochDay()).map { entities ->
            toSessionRecords(entities, zoneId)
        }
    }

    /**
     * 指定された開始日以降のセッション記録のリストをFlowで監視する。
     * 一定日数前からの起床傾向を表示するために用いる。
     */
    fun observeSessionsFrom(
        from: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<SessionRecord>> {
        return ringRecordDao.observeFrom(from.toEpochDay()).map { entities ->
            toSessionRecords(entities, zoneId)
        }
    }

    /**
     * 全てのセッション記録のリストを一括取得する。
     * テスト検証や一括処理に用いる。
     */
    suspend fun getAllSessions(zoneId: ZoneId = ZoneId.systemDefault()): List<SessionRecord> {
        val entities = ringRecordDao.getAll()
        return toSessionRecords(entities, zoneId)
    }

    /**
     * 指定期間のセッション記録のリストを一括取得する。
     * テスト検証や期間集計に用いる。
     */
    suspend fun getSessionsBetween(
        from: LocalDate,
        to: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<SessionRecord> {
        val entities = ringRecordDao.getBetween(from.toEpochDay(), to.toEpochDay())
        return toSessionRecords(entities, zoneId)
    }

    /**
     * 指定されたセッション群から平均起床指標（平均起床回・平均所要分数）を求める。
     * domain.WakeRecord.ktのaverageWakeMetricsを呼び出して結果を返す。
     */
    fun calculateAverages(sessions: List<SessionRecord>): WakeAverages? =
        averageWakeMetrics(sessions)

    /**
     * 指定されたセッション群から自動消音（放置）の割合を求める。
     * domain.WakeRecord.ktのautoSilencedRatioを呼び出して結果を返す。
     */
    fun calculateAutoSilencedRatio(sessions: List<SessionRecord>): Double =
        autoSilencedRatio(sessions)

    /**
     * RingRecordEntityのリストを同一セッションごとにグループ化し、domainのSessionRecordのリストへ変換する。
     * 鳴動記録をセッション単位のまとまりに再構成するために用いる。
     */
    private suspend fun toSessionRecords(
        entities: List<RingRecordEntity>,
        zoneId: ZoneId,
    ): List<SessionRecord> {
        // (alarmId, sessionStart)ごとにグループ化
        val grouped = entities.groupBy { it.alarmId to it.sessionStart }

        return grouped.map { (key, records) ->
            val (alarmId, sessionStartEpochDay) = key
            val sessionDate = LocalDate.ofEpochDay(sessionStartEpochDay)

            val rings = records
                .sortedBy { it.occurrenceIndex }
                .map { entity ->
                    RingRecord(
                        scheduledAt = Instant.ofEpochMilli(entity.scheduledAt).atZone(zoneId),
                        stoppedAt = entity.stoppedAt?.let { Instant.ofEpochMilli(it).atZone(zoneId) },
                        stopMethod = runCatching { StopMethod.valueOf(entity.stopMethod) }
                            .getOrDefault(StopMethod.TAP),
                        occurrenceIndex = entity.occurrenceIndex,
                    )
                }

            val schedule = alarmDao?.getById(alarmId)
            val rangeStartAt = if (schedule != null) {
                LocalDateTime.of(sessionDate, LocalTime.MIDNIGHT)
                    .plusMinutes(schedule.startMinutes.toLong())
                    .atZone(zoneId)
            } else {
                rings.firstOrNull()?.scheduledAt ?: sessionDate.atStartOfDay(zoneId)
            }

            SessionRecord(
                sessionStart = sessionDate,
                rangeStartAt = rangeStartAt,
                rings = rings,
            )
        }.sortedBy { it.sessionStart }
    }
}
