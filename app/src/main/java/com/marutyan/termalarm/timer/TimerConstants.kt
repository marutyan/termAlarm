package com.marutyan.termalarm.timer

/**
 * タイマーまわりのコンポーネント間（TimerTriggerReceiver / TimerForegroundService / ui.timer）で
 * 受け渡すIntent extraキーと通知チャンネルIDの置き場所。同じ意味の値を複数箇所に書かないための共通契約
 * （alarm/AlarmConstants.ktと同じ方針。timerとalarmは別ドメインのため定数自体は分けて持つ）。
 */

// AlarmManagerの予約／通知アクションからTimerForegroundServiceへ渡す、対象TimerStateのid
const val EXTRA_TIMER_ID = "com.marutyan.termalarm.timer.EXTRA_TIMER_ID"

// 旧通知チャンネルID。一度作られた通知チャンネルはアプリから重要度を上げられないため、
// LOWからDEFAULTへ上げる際に新IDへ移行した。既存端末に残った古いチャンネルを削除するためだけに保持する。
const val LEGACY_TIMER_NOTIFICATION_CHANNEL_ID = "timer"

// 現在の通知チャンネルID。通知が「サイレント」欄に入らないよう重要度DEFAULTで作成する。
// 鳴動中(finished)も動作中(running)も同じチャンネルにまとめる
const val TIMER_NOTIFICATION_CHANNEL_ID = "timer_v2"

// 鳴っている間だけ使う通知チャンネルID。重要度を高くして、画面の上へ降りてくる形にする。
// 純正の時計アプリも動作中(Timers v2, 重要度3)と鳴動中(Firing, 重要度4)でチャンネルを分けている
const val TIMER_FIRING_NOTIFICATION_CHANNEL_ID = "timer_firing"

// 動作中/完了のタイマーが1件でもある間、常に表示し続けるフォアグラウンド通知のID
const val TIMER_FOREGROUND_NOTIFICATION_ID = 2001
