package com.marutyan.termalarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * ring_recordテーブルへのアクセスを定義するDAO。
 * 鳴動実績の保存と、指定期間（今週や直近30日など）の読み出しを行うために用いる。
 */
@Dao
interface RingRecordDao {

    /**
     * 鳴動実績を1件追加する。
     * 鳴動を止めたとき、または自動で鳴り止んだときの記録を保存するために用いる。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RingRecordEntity): Long

    /**
     * 指定された開始日（epochDay）以降の鳴動記録を予定時刻の昇順で監視するFlowを返す。
     * 今週や直近30日のセッション集計をリアクティブに画面へ提供するために用いる。
     */
    @Query("SELECT * FROM ring_record WHERE sessionStart >= :fromEpochDay ORDER BY scheduledAt ASC")
    fun observeFrom(fromEpochDay: Long): Flow<List<RingRecordEntity>>

    /**
     * 指定された期間（開始日〜終了日: epochDay）の鳴動記録を予定時刻の昇順で監視するFlowを返す。
     * 週単位など範囲を区切った起床実績の集計に用いる。
     */
    @Query("SELECT * FROM ring_record WHERE sessionStart BETWEEN :fromEpochDay AND :toEpochDay ORDER BY scheduledAt ASC")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<RingRecordEntity>>

    /**
     * 全ての鳴動記録を予定時刻の昇順で監視するFlowを返す。
     * アプリ全体の傾向分析やデバッグ表示に用いる。
     */
    @Query("SELECT * FROM ring_record ORDER BY scheduledAt ASC")
    fun observeAll(): Flow<List<RingRecordEntity>>

    /**
     * 指定された期間（開始日〜終了日: epochDay）の鳴動記録を予定時刻の昇順で一括取得する。
     * ユニットテストや同期的な期間集計処理に用いる。
     */
    @Query("SELECT * FROM ring_record WHERE sessionStart BETWEEN :fromEpochDay AND :toEpochDay ORDER BY scheduledAt ASC")
    suspend fun getBetween(fromEpochDay: Long, toEpochDay: Long): List<RingRecordEntity>

    /**
     * 全ての鳴動記録を予定時刻の昇順で一括取得する。
     * テスト時の件数検証や全データ確認に用いる。
     */
    @Query("SELECT * FROM ring_record ORDER BY scheduledAt ASC")
    suspend fun getAll(): List<RingRecordEntity>
}
