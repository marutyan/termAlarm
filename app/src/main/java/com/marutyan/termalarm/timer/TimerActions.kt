package com.marutyan.termalarm.timer

import android.content.Context
import android.os.SystemClock
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.extendTimer
import com.marutyan.termalarm.domain.finishTimer
import com.marutyan.termalarm.domain.isDue
import com.marutyan.termalarm.domain.pauseTimer
import kotlinx.coroutines.flow.first

/**
 * タイマーの状態を変える操作を1か所へまとめる。
 *
 * 同じ操作を画面（TimerViewModel）、通知のボタン、期限が来たときの処理から呼ぶ。
 * どこから変えても「保存する → 完了時刻を予約し直す → 通知を出し直す」の3つが必ず揃うようにするため、
 * 呼び出し側それぞれに手順を書き写さない。
 */
object TimerActions {

    /** 通知の「停止」。鳴っているタイマーは止めると消える(domain/TimerState.ktの契約) */
    suspend fun stop(context: Context, id: Long) {
        repository(context).delete(id)
        TimerScheduler.cancel(context, id)
        afterChange(context)
    }

    /** 通知や画面の「+1分」 */
    suspend fun extendOneMinute(context: Context, id: Long) {
        mutate(context, id) { state, now, wall -> extendTimer(state, 60_000L, now, wall) }
    }

    /** 通知や画面の「一時停止」 */
    suspend fun pause(context: Context, id: Long) {
        mutate(context, id, ::pauseTimer)
    }

    /**
     * 期限が来たタイマーを鳴動中へ移す。予約で起こされたときに呼ぶ。
     * 1つ鳴らすつもりで起こされても、同時に期限が来ているものはまとめて移す。
     * 戻り値は、この呼び出しで新しく鳴り始めたタイマーがあるかどうか。
     */
    suspend fun markDueTimersFinished(context: Context): Boolean {
        val repo = repository(context)
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        var changed = false
        repo.observeAll().first().forEach { state ->
            if (isDue(state, nowElapsed, nowWall)) {
                repo.update(finishTimer(state, nowElapsed, nowWall))
                TimerScheduler.cancel(context, state.id)
                changed = true
            }
        }
        if (changed) refreshNotification(context)
        return changed
    }

    /** 状態を1件変えて、予約と通知を合わせ直す */
    suspend fun mutate(context: Context, id: Long, transform: (TimerState, Long, Long) -> TimerState) {
        val repo = repository(context)
        val state = repo.getById(id) ?: return
        repo.update(transform(state, SystemClock.elapsedRealtime(), System.currentTimeMillis()))
        TimerScheduler.reschedule(context, id)
        afterChange(context)
    }

    /** 鳴っているタイマーが1つでもあるか。音を鳴らすサービスを続けるかの判断に使う */
    suspend fun hasRingingTimer(context: Context): Boolean =
        repository(context).observeAll().first().any { it.runState == TimerRunState.FINISHED }

    /** いまの一覧に合わせて通知を出し直す */
    suspend fun refreshNotification(context: Context) {
        TimerNotifications.refresh(context, repository(context).observeAll().first())
    }

    // 状態を変えた後に必ず行うこと。鳴っているものが無くなったら音も止める
    private suspend fun afterChange(context: Context) {
        refreshNotification(context)
        if (!hasRingingTimer(context)) {
            TimerRingingService.stop(context)
        }
    }

    private fun repository(context: Context): TimerRepository =
        Repositories.timer(context)
}
