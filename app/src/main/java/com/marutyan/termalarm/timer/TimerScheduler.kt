package com.marutyan.termalarm.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.remainingMillis

/**
 * タイマーの完了時刻をAlarmManagerへ予約する。
 *
 * 動作中のタイマーはサービスを持たない（純正の時計アプリと同じ作り、docs/OFFICIAL_UI.md参照）。
 * 残り時間は通知の仕組みが数え、期限が来たことを知るのはこの予約だけが担う。
 * 予約が届くとTimerTriggerReceiverが鳴動へ移す。
 *
 * setAlarmClock()ではなくsetExactAndAllowWhileIdle()を使う。setAlarmClock()はステータスバーの
 * 「次のアラーム」表示や画面ロック解除の扱いなど“ユーザーが次に起こされる時刻”を表す特別な予約枠で、
 * キッチンタイマーのような一時的な完了通知に使うと、本来のアラーム機能の次回予定と紛らわしくなる。
 * setExactAndAllowWhileIdle()もDoze中に確実に起床でき、既存のUSE_EXACT_ALARM権限だけで動くため、
 * 複数同時に動くタイマーにはこちらが適切と判断した。
 */
object TimerScheduler {

    // 指定idのタイマーをRepositoryから読み直し、RUNNINGなら残り時間ちょうどに再予約、そうでなければ予約を取り消す
    suspend fun reschedule(context: Context, id: Long) {
        val state = repository(context).getById(id)
        if (state == null || state.runState != TimerRunState.RUNNING) {
            cancel(context, id)
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        val remaining = remainingMillis(state, nowElapsed, System.currentTimeMillis())
        registerExact(context, id, nowElapsed + remaining)
    }

    fun cancel(context: Context, id: Long) {
        alarmManager(context).cancel(pendingIntent(context, id))
    }

    // ELAPSED_REALTIME_WAKEUPで、完了予定のelapsedRealtime時刻ちょうどに1回だけ起こす
    private fun registerExact(context: Context, id: Long, triggerAtElapsedRealtime: Long) {
        alarmManager(context).setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtElapsedRealtime,
            pendingIntent(context, id),
        )
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun repository(context: Context): TimerRepository =
        Repositories.timer(context)

    private fun pendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, TimerTriggerReceiver::class.java).putExtra(EXTRA_TIMER_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
