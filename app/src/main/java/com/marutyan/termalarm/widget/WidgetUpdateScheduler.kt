package com.marutyan.termalarm.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 1分ごとのウィジェット更新タイマーのブロードキャストAction。
 * AlarmManagerから次の分の頭に発火されたことを識別するために用いる。
 */
const val ACTION_WIDGET_TICK = "com.marutyan.termalarm.widget.action.WIDGET_TICK"

/**
 * 1分ごとの更新予約に用いるPendingIntentの要求コード。
 * 他の予約と区別して一意に管理するために用いる。
 */
private const val REQUEST_CODE_WIDGET_TICK = 9100

/**
 * AlarmManagerを用いたウィジェットの1分ごと定期更新予約を管理するオブジェクト。
 * 常駐サービスを持たず、非スリープのRTCアラームで次の分の頭に更新を予約するために用いる。
 */
object WidgetUpdateScheduler {

    /**
     * 指定時刻の直後にある「次の分の頭（00秒）」のエポックミリ秒を計算する。
     * 1分ごとの描画更新時刻を決定するために用いる。
     */
    fun nextMinuteEpochMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return ((nowMillis / 60_000L) + 1L) * 60_000L
    }

    /**
     * 次の分の頭にウィジェットを更新するためのアラームをAlarmManagerへ登録する。
     * 端末スリープ中は起こさず、画面点灯時や起動時にまとめて処理させるためRTCタイプを用いる。
     */
    fun scheduleNextTick(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = nextMinuteEpochMillis()
        val pendingIntent = tickPendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExact(AlarmManager.RTC, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC, triggerAtMillis, pendingIntent)
        }
    }

    /**
     * 登録済みの1分ごと更新アラームを取り消す。
     * ホーム画面から全ウィジェットが削除された際に無駄な定期発火を止めるために用いる。
     */
    fun cancelTick(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(tickPendingIntent(context))
    }

    /**
     * 1分ごとの更新発火を受け取るWidgetUpdateReceiver向けのPendingIntentを生成する。
     * システムに安全に予約を預けるために用いる。
     */
    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = ACTION_WIDGET_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_WIDGET_TICK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
