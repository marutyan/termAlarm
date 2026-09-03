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
