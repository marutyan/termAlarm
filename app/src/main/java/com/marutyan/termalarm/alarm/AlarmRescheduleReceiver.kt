package com.marutyan.termalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 端末再起動・タイムゾーン変更・時刻変更・ロケール変更のたびに、全アラームの予約を再計算して登録し直す
 * （docs/SPEC.md「予約の方式」）。Room読み込みを伴う非同期処理のためgoAsync()でBroadcastReceiverの
 * 生存期間を延長し、完了を待ってfinish()する。
 */
class AlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                AlarmScheduler.rescheduleAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
