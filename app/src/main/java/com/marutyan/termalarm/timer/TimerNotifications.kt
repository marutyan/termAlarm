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
import com.marutyan.termalarm.domain.isTimerActive
import com.marutyan.termalarm.domain.isTimerOverdue
import com.marutyan.termalarm.domain.remainingMillis
import com.marutyan.termalarm.domain.timerDisplayText
import com.marutyan.termalarm.domain.timerRemainingFraction
import com.marutyan.termalarm.domain.userLabelOrNull
import com.marutyan.termalarm.ui.alarmlist.TermAlarmTab
import com.marutyan.termalarm.ui.navigation.EXTRA_DEEPLINK_TAB

/**
 * タイマーの通知を組み立てて出す。
 *
 * 残り時間は端末に数えさせず、画面と同じ[timerDisplayText]で文字を作って書き込む。
 * 端末へ「ゼロになる時刻」を渡して数えさせていたときは、丸め方も秒が切り替わる位置も
 * 端末任せになり、画面の数字と1秒ずれて見えていた。そのぶん、秒が変わるたびに
 * 出し直す必要がある。出し直すのはTimerForegroundServiceの役目。
 *
 * この置き場所をサービスから切り離してあるのは、状態が変わったときに
 * 誰でも（画面でもReceiverでも）通知を出し直せるようにするため。
 */
object TimerNotifications {

    /**
     * 通知の文字を、この分だけ先の時刻で作る(ミリ秒)。
     *
     * 通知を出してからステータスバーや通知欄へ届くまでに少し時間がかかる。
     * 秒が変わるちょうどに出すと、画面の数字が先に変わって一瞬ずれて見える。
     * 変わる少し前に、変わった後の値で出しておくと、同じ瞬間に切り替わって見える。
     */
    const val DISPLAY_LEAD_MILLIS = 60L

    /**
     * いまのタイマー一覧に合わせて通知を出し直す。動いているものが1件も無ければ消す。
     * 動作中・一時停止中・鳴動中のどれでも同じ通知にまとめる（純正も1つにまとめている）。
     */
    fun refresh(context: Context, timers: List<TimerState>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val active = activeTimers(timers)
        if (active.isEmpty()) {
            manager.cancel(TIMER_FOREGROUND_NOTIFICATION_ID)
            return
        }
        manager.notify(TIMER_FOREGROUND_NOTIFICATION_ID, build(context, active))
    }

    /**
     * 通知へ出すタイマーだけを取り出す。
     * 停止して設定した長さへ戻ったものは一覧には残るが、動いていないので通知へは出さない。
     */
    fun activeTimers(timers: List<TimerState>): List<TimerState> {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        return timers.filter { isTimerActive(it, nowElapsed, nowWall) }
    }

    /**
     * 通知そのものを作る。サービスがstartForegroundへ渡すためにも使う。
     * 渡す一覧は[activeTimers]で絞ったものにすること。
     */
    fun build(context: Context, timers: List<TimerState>): Notification {
        // 届くまでの遅れを見越して、少し先の時刻で文字を作る（[DISPLAY_LEAD_MILLIS]）
        val nowElapsed = SystemClock.elapsedRealtime() + DISPLAY_LEAD_MILLIS
        val nowWall = System.currentTimeMillis() + DISPLAY_LEAD_MILLIS
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
        builder.setContentTitle(
            main.userLabelOrNull() ?: context.getString(R.string.timer_notification_title),
        )
        applyRemainingTime(context, builder, timers, main, nowElapsed, nowWall)
        // 操作は2つまでにする。3つ並べるとステータスバーのチップへ昇格せず、
        // 動作中だけチップが出ない状態になっていた。純正も状態ごとに2つだけ出す
        when (main.runState) {
            TimerRunState.RUNNING -> {
                builder.addAction(action(context, R.string.timer_pause, ACTION_PAUSE, main.id))
                builder.addAction(action(context, R.string.timer_extend_one_minute, ACTION_EXTEND, main.id))
            }
            TimerRunState.PAUSED -> {
                builder.addAction(action(context, R.string.timer_resume, ACTION_RESUME, main.id))
                builder.addAction(action(context, R.string.timer_stop, ACTION_STOP, main.id))
            }
            TimerRunState.FINISHED -> {
                builder.addAction(action(context, R.string.timer_stop, ACTION_STOP, main.id))
                builder.addAction(action(context, R.string.timer_extend_one_minute, ACTION_EXTEND, main.id))
            }
        }
        if (timers.size > 1) {
            builder.setSubText(context.getString(R.string.timer_notification_summary, timers.size))
        }
        return builder.build()
    }

    /**
     * 残り時間と進み具合を通知へ載せる。
     *
     * 純正は動いているタイマーを横に並べて出す。2件なら数字が2つ、3件なら3つ並ぶ。
     * MetricStyleへタイマーの数だけMetricを足すと、その形になる。
     * MetricStyleを使えない端末では、主役の残り時間を本文へ書く。
     */
    private fun applyRemainingTime(
        context: Context,
        builder: Notification.Builder,
        timers: List<TimerState>,
        main: TimerState,
        nowElapsed: Long,
        nowWall: Long,
    ) {
        // 残りの割合をバーで出す。数字だけだと、あとどれくらいかが一目で掴めない
        builder.setProgress(
            PROGRESS_RESOLUTION,
            (timerRemainingFraction(main, nowElapsed, nowWall) * PROGRESS_RESOLUTION).toInt(),
            false,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
            builder.setContentText(timerDisplayText(main, nowElapsed, nowWall))
            return
        }
        applyMetricStyle(context, builder, timers, main, nowElapsed, nowWall)
    }

    /**
     * 残り時間をMetricStyleへ載せ、ステータスバーのチップへも出す。
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
        // ステータスバーの狭い場所(96dpまで)へ出す文字。画面と同じ文字をそのまま出す。
        // 0を過ぎた後も「タイムアップ」とは書かず、マイナスで数え上げる
        builder.setShortCriticalText(timerDisplayText(main, nowElapsed, nowWall))
    }

    /** タイマー1件を、通知へ載せる形へ変える。数字は画面と同じ文字をそのまま書く。 */
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun metricOf(
        context: Context,
        timer: TimerState,
        nowElapsed: Long,
        nowWall: Long,
    ): Notification.Metric {
        val value = Notification.Metric.FixedText(timerDisplayText(timer, nowElapsed, nowWall))
        // 名前を付けていないタイマーは、代わりに今の状態を書く(純正も状態を出している)
        val label = timer.userLabelOrNull()
            ?: context.getString(statusTextRes(timer, nowElapsed, nowWall))
        return Notification.Metric(value, label)
    }

    // タイマーの状態を表す文言。名前の無いタイマーの見出しに使う。
    // 0を過ぎていれば、鳴動中へ移る前でも「終了」と出す(画面のマイナス表示と合わせる)
    private fun statusTextRes(timer: TimerState, nowElapsed: Long, nowWall: Long): Int = when {
        isTimerOverdue(timer, nowElapsed, nowWall) -> R.string.timer_notification_finished
        timer.runState == TimerRunState.PAUSED -> R.string.timer_notification_paused
        else -> R.string.timer_notification_running
    }

    // 進み具合のバーの目盛りの細かさ。割合をそのまま渡せないため、この数で割った整数にする
    private const val PROGRESS_RESOLUTION = 1000

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
    const val ACTION_RESUME = "com.marutyan.termalarm.timer.RESUME"
}
