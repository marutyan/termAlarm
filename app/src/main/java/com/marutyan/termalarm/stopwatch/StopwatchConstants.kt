package com.marutyan.termalarm.stopwatch

/**
 * ストップウォッチまわりのコンポーネント間で共有する通知チャンネルID・通知IDの置き場所。
 * 同じ意味の値を複数箇所に書かないための共通契約(timer/TimerConstants.ktと同じ方針)。
 */

// 通知チャンネルID
const val STOPWATCH_NOTIFICATION_CHANNEL_ID = "stopwatch"

// 動作中(RUNNING)の間だけ表示し続けるフォアグラウンド通知のID。
// timer機能のTIMER_FOREGROUND_NOTIFICATION_ID(2001)と重複しない値にする
const val STOPWATCH_FOREGROUND_NOTIFICATION_ID = 2002
