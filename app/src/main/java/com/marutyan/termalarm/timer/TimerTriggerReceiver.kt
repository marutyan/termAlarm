package com.marutyan.termalarm.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * TimerScheduler.setExactAndAllowWhileIdle()の発火先。予約はタイマーごとの完了予定時刻ちょうどに
 * 1回だけ届く。ここで期限が来たタイマーを鳴動中へ移し、音を鳴らすサービスを起こす。
 *
 * 期限の判定をここで行うのは、動作中のタイマーがサービスを持たないため。
 * 純正の時計アプリも同じで、動作中はAlarmManagerの予約だけに任せている。
 * 複数のタイマーが同時に期限を迎える場合もまとめて処理する。
 */
class TimerTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "termalarm:timer-trigger")
        wakeLock.acquire(15_000L)

        // onReceiveを抜けるとプロセスを止められてしまうため、保存が終わるまで待たせる
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (TimerActions.markDueTimersFinished(appContext)) {
                    TimerRingingService.start(appContext)
                }
            } finally {
                runCatching { wakeLock.release() }
                pending.finish()
            }
        }
    }
}
