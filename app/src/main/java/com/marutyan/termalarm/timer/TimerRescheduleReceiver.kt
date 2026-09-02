package com.marutyan.termalarm.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.rebaseTimerAfterReboot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 端末再起動後にタイマーを復元する（docs/SPEC.md「端末を再起動した場合は、経過時間を復元して続ける」）。
 * alarm/AlarmRescheduleReceiver.ktは書き込み範囲外のため既存のBOOT_COMPLETED受信口には相乗りせず、
 * timer専用の別Receiverとして新設した。RUNNING中だったタイマーだけSystemClock.elapsedRealtime()を
 * 現在値へ張り直し（起動直後はelapsedRealtimeが0から数え直されるため）、AlarmManager予約も引き直す。
 * FINISHED(鳴動中)だったタイマーは状態そのものは変えず、フォアグラウンドサービス起動後に
 * サービス側の通常のロジックがそのまま鳴動を再開する。
 */
class TimerRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repository = TimerRepository(AlarmDatabase.getInstance(context).timerDao())
                val nowElapsed = SystemClock.elapsedRealtime()
                val nowWall = System.currentTimeMillis()
                repository.getAllRunningOnce().forEach { state ->
                    val rebased = rebaseTimerAfterReboot(state, nowElapsed, nowWall)
                    repository.update(rebased)
                    TimerScheduler.reschedule(context, rebased.id)
                }
                if (repository.hasActiveTimer()) {
                    TimerForegroundService.ensureRunning(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
