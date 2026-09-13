package com.marutyan.termalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 鳴動1回ごとの記録。何回目にどう止めたかを残し、起床の傾向を集計するために使う。
 * 予定時刻や停止時刻、停止方法、セッション内の回数をRoomデータベースに永続化する役割を持つ。
 */
@Entity(tableName = "ring_record")
data class RingRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,          // どのタームか
    val sessionStart: Long,     // セッションの開始日（epoch day）
    val scheduledAt: Long,      // 鳴るはずだった時刻（epoch milli）
    val stoppedAt: Long?,       // 実際に止めた時刻（epoch milli）。放置ならnull
    val stopMethod: String,     // CHALLENGE / TAP / AUTO_SILENCED
    val occurrenceIndex: Int,   // そのセッションの何回目か。0始まり
)
