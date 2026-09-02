package com.marutyan.termalarm.stopwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.StopwatchRepository
import com.marutyan.termalarm.domain.StopwatchRunState
import com.marutyan.termalarm.domain.rebaseStopwatchAfterReboot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 端末再起動後にストップウォッチを復元する（docs/SPEC.md「端末を再起動しても計測を続ける」）。
 * alarm/AlarmRescheduleReceiver.ktは書き込み範囲外のため既存のBOOT_COMPLETED受信口には相乗りせず、
 * stopwatch専用の別Receiverとして新設した(timer機能のTimerRescheduleReceiverと同じ方針)。
 * RUNNING中だった場合だけSystemClock.elapsedRealtime()を現在値へ張り直し
 * (起動直後はelapsedRealtimeが0から数え直されるため)、通知を再開するためフォアグラウンドサービスも起こす。
 */
class StopwatchRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repository = StopwatchRepository(AlarmDatabase.getInstance(context).stopwatchDao())
                val state = repository.getStateOnce()
                if (state.runState == StopwatchRunState.RUNNING) {
                    val nowElapsed = SystemClock.elapsedRealtime()
                    val nowWall = System.currentTimeMillis()
                    repository.updateState(rebaseStopwatchAfterReboot(state, nowElapsed, nowWall))
                    StopwatchForegroundService.ensureRunning(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
