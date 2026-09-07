package com.marutyan.termalarm.alarm

/**
 * 鳴動まわりのコンポーネント間（AlarmTriggerReceiver / RingingService / RingingActivity）で
 * 受け渡すIntent extraキー。同じ意味の値を複数箇所に書かないための置き場所。
 *
 * 無操作タイムアウトの時間はAppSettings.autoStopMinutes(設定画面「消音までの時間」)で持つため、
 * ここには置かない。RingingService/RingingActivityはそれぞれ鳴動開始時に設定を読んで使う。
 */

// AlarmManagerの予約からRingingService/RingingActivityへ渡す、対象AlarmScheduleのid
const val EXTRA_ALARM_ID = "com.marutyan.termalarm.alarm.EXTRA_ALARM_ID"

// 予約時に意図していた鳴動時刻（epoch millis）。鳴動画面の残り回数・次回時刻の計算の基準にする
const val EXTRA_TRIGGER_AT_MILLIS = "com.marutyan.termalarm.alarm.EXTRA_TRIGGER_AT_MILLIS"

// 事前通知（「次のアラーム」）を出すために、鳴動の2時間前へ入れておく予約の目印。
// 鳴動そのものの予約と同じAlarmTriggerReceiverで受け、actionで区別する
const val ACTION_UPCOMING = "com.marutyan.termalarm.alarm.action.UPCOMING"

// 事前通知の「このタームを終了」を押したときに送られてくる目印
const val ACTION_END_SESSION = "com.marutyan.termalarm.alarm.action.END_SESSION"
