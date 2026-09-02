package com.marutyan.termalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * AlarmManager.setAlarmClock()の発火先。AlarmSchedulerが登録したPendingIntentからのみ呼ばれる
 * （マニフェストでexported=falseにしているため他アプリからは起動できない）。
 * 鳴動そのものはRingingServiceへ委ね、ここでは端末を起こしてサービスを起動するだけに留める。
 */
class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (id == -1L) return
        val triggerAtMillis = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, System.currentTimeMillis())

        // Doze中でもRingingServiceがstartForeground()するまでCPUを維持するための短時間ウェイクロック。
        // タイムアウト付きacquireのため明示的なreleaseは不要
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "termalarm:trigger")
        wakeLock.acquire(15_000L)

        val serviceIntent = Intent(context, RingingService::class.java).apply {
            putExtra(EXTRA_ALARM_ID, id)
            putExtra(EXTRA_TRIGGER_AT_MILLIS, triggerAtMillis)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
