package com.marutyan.termalarm.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.marutyan.termalarm.notification.runAsync

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
        // Doze中でもCPUを維持したまま、保存と鳴動の開始まで終わらせる
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "termalarm:timer-trigger")
        wakeLock.acquire(15_000L)

        // Receiverが受け取るContextは短命なので、アプリ全体のものへ持ち替える
        val appContext = context.applicationContext
        runAsync {
            try {
                if (TimerActions.markDueTimersFinished(appContext)) {
                    TimerRingingService.start(appContext)
                }
            } finally {
                runCatching { wakeLock.release() }
            }
        }
    }
}
