package com.marutyan.termalarm.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知のボタン（停止／+1分／一時停止）の受け口。
 *
 * 動作中のタイマーはサービスを持たないため、ボタンの宛先をサービスにできない。
 * BroadcastReceiverで受け、goAsync()で保存が終わるまで生かしてから終わる。
 */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
        if (id < 0) return
        val action = intent.action ?: return
        // onReceiveを抜けるとプロセスを止められてしまうため、保存が終わるまで待たせる
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    TimerNotifications.ACTION_STOP -> TimerActions.stop(appContext, id)
                    TimerNotifications.ACTION_EXTEND -> TimerActions.extendOneMinute(appContext, id)
                    TimerNotifications.ACTION_PAUSE -> TimerActions.pause(appContext, id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
