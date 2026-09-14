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
import com.marutyan.termalarm.domain.resetTimer
import com.marutyan.termalarm.domain.resumeTimer
import com.marutyan.termalarm.domain.startTimer
import kotlinx.coroutines.flow.first

/**
 * タイマーの状態を変える操作を1か所へまとめる。
 *
 * 同じ操作を画面（TimerViewModel）、通知のボタン、期限が来たときの処理から呼ぶ。
 * どこから変えても「保存する → 完了時刻を予約し直す → 通知を出し直す」の3つが必ず揃うようにするため、
 * 呼び出し側それぞれに手順を書き写さない。
 */
object TimerActions {

    /**
     * 新しいタイマーを始める。戻り値は作られたタイマーのid。
     *
     * 名前は空にしておく。以前は「0:05」のような設定時間の文字列を入れていたが、
     * 延長すると設定時間だけが変わって名前が古いまま残り、通知の見出しにも
     * その古い数字が出ていた。設定時間は保存された長さから作れば足りる。
     */
    suspend fun start(context: Context, durationMillis: Long): Long {
        val state = startTimer(
            id = 0L,
            label = "",
            durationMillis = durationMillis,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            nowWallClockMillis = System.currentTimeMillis(),
        )
        val id = repository(context).add(state)
        TimerScheduler.reschedule(context, id)
        afterChange(context)
        return id
    }

    /**
     * 通知や画面の「停止」。鳴るのをやめ、設定した長さへ戻して一覧に残す。
     * 消すのは「×」の役目とする（純正の時計アプリも停止では消えない）。
     */
    suspend fun stop(context: Context, id: Long) {
        mutate(context, id, ::resetTimer)
    }

    /** 「×」。タイマーを一覧から消す。 */
    suspend fun delete(context: Context, id: Long) {
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

    /** 通知や画面の「再開」 */
    suspend fun resume(context: Context, id: Long) {
        mutate(context, id, ::resumeTimer)
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

    /** 鳴っているタイマーが1つでもあるか。音を鳴らし続けるかの判断に使う */
    suspend fun hasRingingTimer(context: Context): Boolean =
        repository(context).observeAll().first().any { it.runState == TimerRunState.FINISHED }

    /** いまの一覧に合わせて通知を出し直す */
    suspend fun refreshNotification(context: Context) {
        TimerNotifications.refresh(context, repository(context).observeAll().first())
    }

    /**
     * 状態を変えた後に必ず行うこと。
     *
     * 数字が進むタイマー（動作中か、0を過ぎて数え上げているもの）が1件でもあれば、
     * 秒ごとに通知を出し直すサービスを起こす。1件も無ければ止める。
     * 一時停止中は数字が動かないので、サービスは要らない。
     */
    suspend fun afterChange(context: Context) {
        refreshNotification(context)
        val hasTicking = repository(context).observeAll().first().any {
            it.runState == TimerRunState.RUNNING || it.runState == TimerRunState.FINISHED
        }
        if (hasTicking) {
            TimerForegroundService.start(context)
        } else {
            TimerForegroundService.stop(context)
        }
    }

    private fun repository(context: Context): TimerRepository =
        Repositories.timer(context)
}
