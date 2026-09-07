package com.marutyan.termalarm.domain

/**
 * 鳴動中のアラームを解除する操作。設定画面「アラーム解除の操作」で選ぶ
 * (docs/OFFICIAL_SETTINGS.md「アラーム解除の操作」)。
 */
enum class AlarmDismissMethod {
    TAP,
    SWIPE,
}

/**
 * 鳴動中に音量ボタンを押したときの動作。設定画面「アラーム時の音量ボタン」で選ぶ。
 */
enum class VolumeButtonAction {
    ADJUST_VOLUME,
    SNOOZE,
    DISMISS,
}

/**
 * 週の始まりの曜日。設定画面「週の始まり」で選び、アラーム編集画面などの曜日チップの並び順に影響する。
 */
enum class WeekStart {
    SUNDAY,
    MONDAY,
}

/**
 * アプリ全体の設定値（docs/OFFICIAL_SETTINGS.md「作る設定の一覧」）。
 * 時計のアナログ/デジタル表示だけは既存のClockDisplayMode/ClockSettingsEntityで既に管理されているため、
 * ここには含めない（重複した保存先を作らない）。アラームの音量は端末のSTREAM_ALARMを直接操作するだけで
 * アプリ側に別の値を持たないため、これも含めない。日付と時刻の変更は端末設定を開くだけで値を持たない。
 *
 * 各既定値は、この設定が無かった今までのアプリの固定値と同じにする
 * （消音10分=alarm/AlarmConstants.kt、フェードイン5秒=alarm/RingingService.kt、
 * タイマーのフェードイン1.5秒=timer/TimerForegroundService.kt）。
 */
data class AppSettings(
    val dismissMethod: AlarmDismissMethod = AlarmDismissMethod.TAP,
    val autoStopMinutes: Int = 10,
    val defaultSnoozeMinutes: Int = 5,
    val alarmFadeInSeconds: Int = 5,
    val volumeButtonAction: VolumeButtonAction = VolumeButtonAction.ADJUST_VOLUME,
    val weekStart: WeekStart = WeekStart.SUNDAY,
    // 純正の時計は秒まで出す。それに合わせて既定を出す側にしている
    val showClockSeconds: Boolean = true,
    // システムの音選択(RingtoneManager)で選んだUriの文字列表現。nullは既定のアラーム音を意味する
    val timerSoundUri: String? = null,
    val timerFadeInSeconds: Float = 1.5f,
    val timerVibration: Boolean = true,
)
