package com.marutyan.termalarm.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.R
import com.marutyan.termalarm.notification.NotificationChannels
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.overdueMillis
import com.marutyan.termalarm.domain.remainingMillis
import com.marutyan.termalarm.ui.alarmlist.TermAlarmTab
import com.marutyan.termalarm.ui.navigation.EXTRA_DEEPLINK_TAB
import java.time.Duration

/**
 * タイマーの通知を組み立てて出す。
 *
 * 純正の時計アプリを調べたところ、動作中はサービスを持たず、通知を1回出すだけだった。
 * 残り時間はMetricStyleへ「ゼロになる時刻」を渡し、数えるのは端末に任せている。
 * そのため1秒ごとに通知を作り直す必要がなく、状態が変わったときだけ出し直せばよい。
 *
 * この置き場所をサービスから切り離してあるのは、動作中は誰でも（画面でもReceiverでも）
 * 通知を出し直せるようにするため。
 */
object TimerNotifications {

    /**
     * いまのタイマー一覧に合わせて通知を出し直す。1件も無ければ消す。
     * 動作中・一時停止中・鳴動中のどれでも同じ通知にまとめる（純正も1つにまとめている）。
     */
    fun refresh(context: Context, timers: List<TimerState>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (timers.isEmpty()) {
            manager.cancel(TIMER_FOREGROUND_NOTIFICATION_ID)
            return
        }
        manager.notify(TIMER_FOREGROUND_NOTIFICATION_ID, build(context, timers))
    }

    /** 通知そのものを作る。鳴動中のサービスが startForeground へ渡すためにも使う。 */
    fun build(context: Context, timers: List<TimerState>): Notification {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        // 鳴っているものを最優先。それ以外は残り時間が短い順に見て、いちばん早く鳴るものを主役にする
        val main = timers.firstOrNull { it.runState == TimerRunState.FINISHED }
            ?: timers.filter { it.runState == TimerRunState.RUNNING }
                .minByOrNull { remainingMillis(it, nowElapsed, nowWall) }
            ?: timers.first()

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            // 通知を押したらタイマーのタブを開く。一覧が出ると、どのタイマーの話か分からない
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_DEEPLINK_TAB, TermAlarmTab.TIMER.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 鳴っている間は重要度の高いチャンネルへ移して、画面の上へ降りてくるようにする
        val isFiring = main.runState == TimerRunState.FINISHED
        val builder = Notification.Builder(context, ensureChannel(context, isFiring))
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setOngoing(true)
            // 種類はタイマーにする。新しいAndroidは、この種類の進行中の通知だけを
            // ステータスバーへ出す対象として扱う。アラームは「今まさに鳴っている」ための種類で対象外
            .setCategory(Notification.CATEGORY_STOPWATCH)
            // 純正と同じく、この端末の中だけで出す。ロック画面でも中身を隠さない
            .setLocalOnly(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // 通知の丸いアイコンに、アプリのアイコンではなく砂時計を使わせる
            .addExtras(Bundle().apply { putBoolean("android.app.preferSmallIcon", true) })
            // 何時に出したかは要らない。残り時間の方が知りたい情報
            .setShowWhen(false)
            .setContentIntent(contentIntent)

        // 見出しは「進行中の重要な通知」として扱われるための必須項目。
        // 本文は書かない。MetricStyleが各タイマーの見出しを持つため、同じ言葉が2行続いてしまう
        builder.setContentTitle(main.label.ifBlank { context.getString(R.string.timer_notification_title) })
        applyRemainingTime(context, builder, timers, main, nowElapsed, nowWall)
        builder.addAction(action(context, R.string.timer_stop, ACTION_STOP, main.id))
        builder.addAction(action(context, R.string.timer_extend_one_minute, ACTION_EXTEND, main.id))
        if (main.runState == TimerRunState.RUNNING) {
            builder.addAction(action(context, R.string.timer_pause, ACTION_PAUSE, main.id))
        }
        if (timers.size > 1) {
            builder.setSubText(context.getString(R.string.timer_notification_summary, timers.size))
        }
        return builder.build()
    }

    /**
     * 残り時間を通知へ載せる。
     *
     * 純正は動いているタイマーを横に並べて出す。2件なら数字が2つ、3件なら3つ並ぶ。
     * MetricStyleへタイマーの数だけMetricを足すと、その形になる。
     */
    private fun applyRemainingTime(
        context: Context,
        builder: Notification.Builder,
        timers: List<TimerState>,
        main: TimerState,
        nowElapsed: Long,
        nowWall: Long,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
            applyChronometerFallback(builder, main, nowElapsed, nowWall)
            builder.setContentText(context.getString(statusTextRes(main.runState)))
            return
        }
        applyMetricStyle(context, builder, timers, main, nowElapsed, nowWall)
    }

    /**
     * 残り時間の表示を端末へ任せ、ステータスバーへも出す。
     * MetricStyleはAndroid 17(CINNAMON_BUN)からの仕組みなので、呼び出し側で版を確かめること。
     */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun applyMetricStyle(
        context: Context,
        builder: Notification.Builder,
        timers: List<TimerState>,
        main: TimerState,
        nowElapsed: Long,
        nowWall: Long,
    ) {
        // 主役を先頭に置く。ステータスバーの狭い場所へ出すのは先頭の1つだけになる
        val ordered = listOf(main) + timers.filter { it.id != main.id }
        val style = Notification.MetricStyle()
        ordered.forEach { style.addMetric(metricOf(context, it, nowElapsed, nowWall)) }
        builder.setStyle(style.setCriticalMetric(0))
        builder.setRequestPromotedOngoing(true)
        // ステータスバーの狭い場所(96dpまで)へ出す文字。収まらないとアイコンだけになる
        builder.setShortCriticalText(shortCriticalText(context, main, nowElapsed, nowWall))
    }

    /**
     * ステータスバーの丸いチップへ出す短い文字。
     * 幅が96dpしかないため、7文字ほどで収まるようにする。
     */
    private fun shortCriticalText(
        context: Context,
        main: TimerState,
        nowElapsed: Long,
        nowWall: Long,
    ): String = when (main.runState) {
        TimerRunState.FINISHED -> context.getString(R.string.timer_notification_short_finished)
        else -> formatDuration(remainingMillis(main, nowElapsed, nowWall))
    }

    /** タイマー1件を、通知が数を数えられる形へ変える。 */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun metricOf(
        context: Context,
        timer: TimerState,
        nowElapsed: Long,
        nowWall: Long,
    ): Notification.Metric {
        val remaining = remainingMillis(timer, nowElapsed, nowWall)
        val value = if (timer.runState == TimerRunState.PAUSED) {
            Notification.Metric.TimeDifference.forPausedTimer(
                Duration.ofMillis(remaining),
                Notification.Metric.TimeDifference.FORMAT_CHRONOMETER,
            )
        } else {
            // 残り時間が尽きる時刻。鳴動中はすでに過ぎているので、過去の時刻になる
            val zero = when (timer.runState) {
                TimerRunState.FINISHED -> nowElapsed - overdueMillis(timer, nowElapsed, nowWall)
                else -> nowElapsed + remaining
            }
            Notification.Metric.TimeDifference.forTimer(
                zero,
                Notification.Metric.TimeDifference.FORMAT_CHRONOMETER,
            )
        }
        // 名前を付けていないタイマーは、代わりに今の状態を書く(純正も状態を出している)
        val label = timer.label.ifBlank { context.getString(statusTextRes(timer.runState)) }
        return Notification.Metric(value, label)
    }

    /**
     * MetricStyleを使えない端末向けに、通知の時計機能へ終わる時刻を渡して数えさせる。
     * 数字を書き込むのではなく基準の時刻を渡す点はMetricStyleと同じ考え方。
     */
    private fun applyChronometerFallback(
        builder: Notification.Builder,
        main: TimerState,
        nowElapsed: Long,
        nowWall: Long,
    ) {
        if (main.runState == TimerRunState.PAUSED) return
        val remaining = when (main.runState) {
            TimerRunState.FINISHED -> -overdueMillis(main, nowElapsed, nowWall)
            else -> remainingMillis(main, nowElapsed, nowWall)
        }
        builder.setWhen(nowWall + remaining)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
    }

    // タイマーの状態を表す文言。通知の本文と、名前の無いタイマーの見出しの両方で使う
    private fun statusTextRes(runState: TimerRunState): Int = when (runState) {
        TimerRunState.FINISHED -> R.string.timer_notification_finished
        TimerRunState.PAUSED -> R.string.timer_notification_paused
        TimerRunState.RUNNING -> R.string.timer_notification_running
    }

    // 通知のボタン1つ分。アイコンは出さないのでnullを渡す
    private fun action(context: Context, labelRes: Int, action: String, id: Long): Notification.Action =
        Notification.Action.Builder(
            null as Icon?,
            context.getString(labelRes),
            actionIntent(context, action, id),
        ).build()

    // 通知のボタンから操作を送り返すための入れ物。受け取るのはTimerActionReceiver
    private fun actionIntent(context: Context, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (action + id).hashCode(),
            Intent(context, TimerActionReceiver::class.java).setAction(action).putExtra(EXTRA_TIMER_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 通知チャンネルを用意する。動作中と鳴動中で分ける。
     *
     * 動作中は重要度DEFAULT。LOWだと「サイレント」欄へ入ってしまう。
     * 鳴動中は重要度HIGHにして、画面の上へ降りてくる形（ヘッドアップ通知）にする。
     * 純正の時計アプリも同じように2つへ分けている。
     *
     * どちらも音は鳴らさない。音はMediaPlayerがアラーム用途で鳴らすため、
     * チャンネル側でも鳴らすと二重になる。
     *
     * 一度作ったチャンネルは重要度を上げられないため、古いものは消して新しいIDで作り直す。
     */
    private fun ensureChannel(context: Context, isFiring: Boolean): String {
        NotificationChannels.delete(context, LEGACY_TIMER_NOTIFICATION_CHANNEL_ID)
        return if (isFiring) {
            NotificationChannels.ensure(
                context,
                TIMER_FIRING_NOTIFICATION_CHANNEL_ID,
                R.string.timer_firing_notification_channel_name,
                NotificationManager.IMPORTANCE_HIGH,
            )
        } else {
            NotificationChannels.ensure(
                context,
                TIMER_NOTIFICATION_CHANNEL_ID,
                R.string.timer_notification_channel_name,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        }
    }

    const val ACTION_STOP = "com.marutyan.termalarm.timer.STOP"
    const val ACTION_EXTEND = "com.marutyan.termalarm.timer.EXTEND"
    const val ACTION_PAUSE = "com.marutyan.termalarm.timer.PAUSE"
}
