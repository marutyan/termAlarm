package com.marutyan.termalarm.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.marutyan.termalarm.notification.runAsync
import android.os.SystemClock
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.rebaseTimerAfterReboot

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
        // 外部アプリから偽のIntentが送られた場合に意図しない復元処理が走るのを防ぐため、
        // AndroidManifest.xmlのintent-filterで定義された想定通りのaction (BOOT_COMPLETED) であるか検証する。
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        // Receiverが受け取るContextは短命なので、アプリ全体のものへ持ち替える
        val appContext = context.applicationContext
        runAsync {
            val repository = TimerRepository(AlarmDatabase.getInstance(appContext).timerDao())
            val nowElapsed = SystemClock.elapsedRealtime()
            val nowWall = System.currentTimeMillis()
            repository.getAllRunningOnce().forEach { state ->
                val rebased = rebaseTimerAfterReboot(state, nowElapsed, nowWall)
                repository.update(rebased)
                TimerScheduler.reschedule(appContext, rebased.id)
            }
            // 再起動をまたいで期限が過ぎていたタイマーは、ここで鳴動中へ移す
            if (TimerActions.markDueTimersFinished(appContext)) {
                TimerRingingService.start(appContext)
            } else {
                TimerActions.refreshNotification(appContext)
            }
        }
    }
}
