package com.marutyan.termalarm.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.marutyan.termalarm.notification.runAsync

/**
 * 通知のボタン（停止／+1分／一時停止）の受け口。
 *
 * 動作中のタイマーはサービスを持たないため、ボタンの宛先をサービスにできない。
 * 保存が終わるまでプロセスを生かす必要があるので、runAsyncを通して走らせる。
 */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
        if (id < 0) return
        val action = intent.action ?: return
        // Receiverが受け取るContextは短命なので、アプリ全体のものへ持ち替える
        val appContext = context.applicationContext
        runAsync {
            when (action) {
                TimerNotifications.ACTION_STOP -> TimerActions.stop(appContext, id)
                TimerNotifications.ACTION_EXTEND -> TimerActions.extendOneMinute(appContext, id)
                TimerNotifications.ACTION_PAUSE -> TimerActions.pause(appContext, id)
            }
        }
    }
}
