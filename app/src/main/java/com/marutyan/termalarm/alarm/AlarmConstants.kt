package com.marutyan.termalarm.alarm

/**
 * 鳴動まわりのコンポーネント間（AlarmTriggerReceiver / RingingService / RingingActivity）で
 * 受け渡すIntent extraキーと、無操作タイムアウトの既定値。同じ意味の値を複数箇所に書かないための置き場所。
 */

// AlarmManagerの予約からRingingService/RingingActivityへ渡す、対象AlarmScheduleのid
const val EXTRA_ALARM_ID = "com.marutyan.termalarm.alarm.EXTRA_ALARM_ID"

// 予約時に意図していた鳴動時刻（epoch millis）。鳴動画面の残り回数・次回時刻の計算の基準にする
const val EXTRA_TRIGGER_AT_MILLIS = "com.marutyan.termalarm.alarm.EXTRA_TRIGGER_AT_MILLIS"

// 無操作のまま自動で鳴り止むまでの時間。既定10分（docs/SPEC.md「鳴動」節）
const val RINGING_AUTO_STOP_TIMEOUT_MILLIS = 10 * 60 * 1000L
