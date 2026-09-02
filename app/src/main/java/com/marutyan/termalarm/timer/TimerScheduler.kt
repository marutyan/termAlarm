package com.marutyan.termalarm.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.remainingMillis

/**
 * タイマー完了時刻のAlarmManager予約・解除を担う。TimerForegroundServiceの1秒ごとのtickだけに頼ると
 * Dozeなどでtickが遅れた場合にサービスプロセスごと止まっていると気付けないため、完了予定時刻ちょうどに
 * 端末を起こす保険としてAlarmManagerを使う（docs/SPEC.md「タイマータブ」の完了通知）。
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
        TimerRepository(AlarmDatabase.getInstance(context).timerDao())

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
