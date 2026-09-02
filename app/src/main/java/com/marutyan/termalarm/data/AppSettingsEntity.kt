package com.marutyan.termalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marutyan.termalarm.domain.AlarmDismissMethod
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.VolumeButtonAction
import com.marutyan.termalarm.domain.WeekStart

/**
 * アプリ全体の設定を保存する単一行のテーブル。ClockSettingsEntityと同じ理由で、idは常に0固定にして
 * 1行しか存在しないことを保証し、Insert(REPLACE)でupsertする(AppSettingsDao参照)。
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = SINGLE_ROW_ID,
    val dismissMethod: String,
    val autoStopMinutes: Int,
    val defaultSnoozeMinutes: Int,
    val alarmFadeInSeconds: Int,
    val volumeButtonAction: String,
    val weekStart: String,
    val showClockSeconds: Boolean,
    val timerSoundUri: String?,
    val timerFadeInSeconds: Float,
    val timerVibration: Boolean,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}

// enum⇔文字列の変換で未知の値(異なるバージョン間の互換切れ等)に当たった場合は既定値へ倒す
internal fun AppSettingsEntity.toDomain(): AppSettings = AppSettings(
    dismissMethod = runCatching { AlarmDismissMethod.valueOf(dismissMethod) }
        .getOrDefault(AppSettings().dismissMethod),
    autoStopMinutes = autoStopMinutes,
    defaultSnoozeMinutes = defaultSnoozeMinutes,
    alarmFadeInSeconds = alarmFadeInSeconds,
    volumeButtonAction = runCatching { VolumeButtonAction.valueOf(volumeButtonAction) }
        .getOrDefault(AppSettings().volumeButtonAction),
    weekStart = runCatching { WeekStart.valueOf(weekStart) }.getOrDefault(AppSettings().weekStart),
    showClockSeconds = showClockSeconds,
    timerSoundUri = timerSoundUri,
    timerFadeInSeconds = timerFadeInSeconds,
    timerVibration = timerVibration,
)

internal fun AppSettings.toEntity(): AppSettingsEntity = AppSettingsEntity(
    dismissMethod = dismissMethod.name,
    autoStopMinutes = autoStopMinutes,
    defaultSnoozeMinutes = defaultSnoozeMinutes,
    alarmFadeInSeconds = alarmFadeInSeconds,
    volumeButtonAction = volumeButtonAction.name,
    weekStart = weekStart.name,
    showClockSeconds = showClockSeconds,
    timerSoundUri = timerSoundUri,
    timerFadeInSeconds = timerFadeInSeconds,
    timerVibration = timerVibration,
)
