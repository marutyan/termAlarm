package com.marutyan.termalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marutyan.termalarm.domain.ClockDisplayMode

/**
 * 時計タブの表示設定(アナログ/デジタル)を保存する単一行のテーブル。idは常に0固定にして
 * 1行しか存在しないことを保証し、Insert(REPLACE)でupsertする(WorldClockDao.setDisplayMode参照)。
 */
@Entity(tableName = "clock_settings")
data class ClockSettingsEntity(
    @PrimaryKey
    val id: Int = SINGLE_ROW_ID,
    val displayMode: String,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}

internal fun ClockSettingsEntity.toDomain(): ClockDisplayMode =
    runCatching { ClockDisplayMode.valueOf(displayMode) }.getOrDefault(ClockDisplayMode.DIGITAL)

internal fun ClockDisplayMode.toEntity() = ClockSettingsEntity(displayMode = name)
