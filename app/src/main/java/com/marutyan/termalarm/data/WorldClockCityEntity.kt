package com.marutyan.termalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marutyan.termalarm.domain.WorldClockCity

/**
 * WorldClockCityをRoomで永続化するためのテーブル定義。都市データそのものは持たず、
 * ユーザーが選んだzoneIdと並び順(sortOrder)だけを保存する(docs/SPEC.md「時計タブ」)。
 */
@Entity(tableName = "world_clock_city")
data class WorldClockCityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val zoneId: String,
    val sortOrder: Int,
)

internal fun WorldClockCityEntity.toDomain() = WorldClockCity(id = id, zoneId = zoneId, sortOrder = sortOrder)

internal fun WorldClockCity.toEntity() = WorldClockCityEntity(id = id, zoneId = zoneId, sortOrder = sortOrder)
