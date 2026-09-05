package com.marutyan.termalarm.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.R
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.canEndTodaySession
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import com.marutyan.termalarm.notification.NotificationChannels
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 鳴る前に通知欄へ出す「次のアラーム」。純正の時計アプリと同じく、次にいつ鳴るかを通知欄で確認でき、
 * 展開するとその場で操作できるようにする。純正が1回分の解除しか出せないのに対し、
 * このアプリは時間帯（ターム）で鳴らすため、残りの回数と「このタームを終了」を載せる。
 *
 * いつ出すか・いつ消すかはAlarmSchedulerが一手に決め、ここは組み立てと掲示だけを担う。
 */
object AlarmNotifications {

    // 通知に出す鳴動時刻の書式。純正の「3:45 (日)」に合わせ、時刻と曜日を1行で示す
    private val TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("H:mm（E）", Locale.JAPANESE)

    // 他の通知(鳴動中・タイマー・ストップウォッチ)と番号がぶつからないよう、事前通知だけのタグで分ける。
    // 同じタグの中ではアラームのidをそのまま通知番号に使い、1件のアラームにつき1件だけ出す
    private const val UPCOMING_TAG = "upcoming"

    private const val CHANNEL_ID = "upcoming"

    /** 次の鳴動時刻を通知欄へ出す。同じアラームへ出し直すと内容が置き換わる */
    fun postUpcoming(context: Context, schedule: AlarmSchedule, next: ZonedDateTime, now: ZonedDateTime) {
        if (!NotificationPermission.isGranted(context)) return

        val builder = NotificationCompat.Builder(context, ensureChannel(context))
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.upcoming_notification_title))
            .setContentText(upcomingText(context, schedule, next))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context, schedule.id))
            // 複数のアラームが通知欄に並ぶとき、鳴る順に上から並べる。
            // epoch millisは2286年まで13桁で揃うため、文字列のまま比較しても時刻順になる
            .setSortKey(next.toInstant().toEpochMilli().toString())

        // 寝ぼけたまま押せてしまう事故を防ぐ設定(skipRequiresApp)のときは、鳴動画面と同じく操作を出さない
        if (!schedule.skipRequiresApp && canEndTodaySession(schedule, now)) {
            builder.addAction(
                0,
                context.getString(R.string.ringing_skip_today),
                endSessionPendingIntent(context, schedule.id),
            )
        }

        manager(context).notify(UPCOMING_TAG, schedule.id.toInt(), builder.build())
    }

    /** 指定アラームの事前通知を取り消す。まだ2時間以上先の場合や、アラームを消した場合に呼ぶ */
    fun cancelUpcoming(context: Context, id: Long) {
        manager(context).cancel(UPCOMING_TAG, id.toInt())
    }

    /**
     * 通知の本文。「7:05（日）」に、そのタームで残っている回数を続ける。
     * 単発に退化しているアラーム（残り0回）では回数を出さず、純正と同じく時刻だけにする。
     */
    private fun upcomingText(context: Context, schedule: AlarmSchedule, next: ZonedDateTime): String {
        val time = next.format(TIME_FORMATTER)
        val remaining = remainingOccurrenceCount(schedule, next)
        return if (remaining > 0) {
            context.getString(R.string.upcoming_notification_text_with_remaining, time, remaining)
        } else {
            time
        }
    }

    // 通知本体をタップしたときに開く画面。アプリの入口(MainActivity)を指す
    private fun openAppIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // 「このタームを終了」を押したときの送り先。受け口はAlarmTriggerReceiverに揃えている
    private fun endSessionPendingIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, AlarmTriggerReceiver::class.java)
                .setAction(ACTION_END_SESSION)
                .putExtra(EXTRA_ALARM_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // 鳴る前の予告なので、画面の上へ降りてこないIMPORTANCE_DEFAULTにする（純正の「次のアラーム」と同じ）
    private fun ensureChannel(context: Context): String = NotificationChannels.ensure(
        context,
        CHANNEL_ID,
        R.string.upcoming_channel_name,
        NotificationManager.IMPORTANCE_DEFAULT,
    )

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)
}
