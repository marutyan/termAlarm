package com.marutyan.termalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.AppTheme

/**
 * アプリ全体の設定を保存する単一行のテーブル。ClockSettingsEntityと同じ理由で、idは常に0固定にして
 * 1行しか存在しないことを保証し、Insert(REPLACE)でupsertする(AppSettingsDao参照)。
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = SINGLE_ROW_ID,
    val alarmSoundUri: String?,
    val vibration: Boolean,
    val fadeInSeconds: Int,
    val silenceAfterMinutes: Int?,
    val wakeCheckMinutes: Int,
    val theme: String,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}

// enum⇔文字列の変換で未知の値(異なるバージョン間の互換切れ等)に当たった場合は既定値へ倒す
internal fun AppSettingsEntity.toDomain(): AppSettings = AppSettings(
    alarmSoundUri = alarmSoundUri,
    vibration = vibration,
    fadeInSeconds = fadeInSeconds,
    silenceAfterMinutes = silenceAfterMinutes,
    wakeCheckMinutes = wakeCheckMinutes,
    theme = runCatching { AppTheme.valueOf(theme) }.getOrDefault(AppTheme.NAVY),
)

internal fun AppSettings.toEntity(): AppSettingsEntity = AppSettingsEntity(
    alarmSoundUri = alarmSoundUri,
    vibration = vibration,
    fadeInSeconds = fadeInSeconds,
    silenceAfterMinutes = silenceAfterMinutes,
    wakeCheckMinutes = wakeCheckMinutes,
    theme = theme.name,
)

