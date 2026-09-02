package com.marutyan.termalarm.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * TimerScheduler.setExactAndAllowWhileIdle()の発火先。予約はタイマーごとの完了予定時刻ちょうどに1回だけ
 * 届くが、どのタイマーが完了したかの実処理はTimerForegroundService側がRepositoryを読み直して行う
 * （複数タイマーが同時に完了する場合でも1回のtickでまとめて処理できるようにするため）。
 * ここではDoze中でもCPUを維持しつつサービスを起こすだけに留める（alarm/AlarmTriggerReceiverと同じ考え方）。
 */
class TimerTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "termalarm:timer-trigger")
        wakeLock.acquire(15_000L)

        TimerForegroundService.ensureRunning(context)
    }
}
