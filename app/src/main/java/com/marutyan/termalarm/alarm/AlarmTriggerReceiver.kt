package com.marutyan.termalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.domain.nextTrigger
import com.marutyan.termalarm.notification.runAsync
import java.time.ZonedDateTime

/**
 * AlarmSchedulerが登録したPendingIntentの受け口
 * （マニフェストでexported=falseにしているため他アプリからは起動できない）。
 * actionの無い予約が鳴動、ACTION_UPCOMINGが事前通知の掲示、ACTION_END_SESSIONが
 * 事前通知の「このタームを終了」に対応する。
 * 鳴動そのものはRingingServiceへ委ね、ここでは端末を起こしてサービスを起動するだけに留める。
 */
class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (id == -1L) return

        // Receiverが受け取るContextは短命なので、アプリ全体のものへ持ち替える
        val appContext = context.applicationContext
        when (intent.action) {
            // 鳴動の2時間前。予約し直せば事前通知の掲示までAlarmSchedulerが面倒を見る
            ACTION_UPCOMING -> {
                runAsync { AlarmScheduler.reschedule(appContext, id) }
                return
            }
            ACTION_END_SESSION -> {
                runAsync { endSession(appContext, id) }
                return
            }
        }

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

    // 事前通知から当日のタームを終了する。まだ鳴っていないため、次の鳴動予定をそのタームの代表時刻として渡す
    private suspend fun endSession(context: Context, id: Long) {
        val schedule = Repositories.alarm(context).getById(id) ?: return
        val next = nextTrigger(schedule, ZonedDateTime.now()) ?: return
        AlarmScheduler.onSessionEnded(context, id, next)
    }
}
