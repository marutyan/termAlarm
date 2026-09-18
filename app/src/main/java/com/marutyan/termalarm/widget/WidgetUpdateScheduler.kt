package com.marutyan.termalarm.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.marutyan.termalarm.alarm.ExactAlarmPermission
import java.time.ZonedDateTime

/**
 * ウィジェット再描画タイマーのブロードキャストAction。
 * 日付の切り替わり（0:00）または次の鳴動保険でAlarmManagerから発火されたことを識別するために用いる。
 */
const val ACTION_WIDGET_REFRESH = "com.marutyan.termalarm.widget.action.WIDGET_REFRESH"

/**
 * 以前の版が使っていた Action 名。更新後に残った旧予約を取り消すためと、
 * 取り消し前に発火した旧予約を WidgetUpdateReceiver で新 Action と同じ経路へ乗せるためだけに保持する。
 */
internal const val ACTION_WIDGET_TICK = "com.marutyan.termalarm.widget.action.WIDGET_TICK"

/**
 * ウィジェット再描画予約に用いるPendingIntentの要求コード。
 * 要求コードは他の予約と衝突させないためのもの。旧予約は Action が違うため別に取り消す。
 */
private const val REQUEST_CODE_WIDGET_REFRESH = 9100

/**
 * AlarmManagerを用いたウィジェットの再描画予約を管理するオブジェクト。
 * 日付の切り替わり（次の0:00）および鳴動未通知保険のタイミングで端末を起こして再描画を予約するために用いる。
 */
object WidgetUpdateScheduler {

    /**
     * 指定した現在日時とタイムゾーンにおける「次の 0:00（深夜0時）」のエポックミリ秒を計算する純粋関数。
     * 日付が変わる瞬間にウィジェットの月日表示を描き直すための予約時刻を決定する役割を持つ。
     *
     * 夏時間の切り替え日（1日が23時間または25時間になる日）でも正しく次の 0:00 を返すため、
     * 単純な24時間の加算ではなく、LocalDate.plusDays(1).atStartOfDay(zone) により
     * タイムゾーン規則を適用して求める。
     */
    fun nextMidnightEpochMillis(now: ZonedDateTime): Long {
        return now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
    }

    /**
     * 次にウィジェットの描き直しが必要となるエポックミリ秒を算出する純粋関数。
     * 次回更新予約の確定時刻を決定するために用いる。
     *
     * 次の鳴動が変わる契機は既に別経路（RINGING_FINISHED と設定変更の監視）で受けているため
     * 本来は予約の対象にする必要はないが、鳴動終了通知が何らかの理由で届かなかった場合の保険として、
     * 次の鳴動時刻が 0:00 より前にあるなら「鳴動時刻 + 1秒」と「次の 0:00」の早い方を返す。
     */
    fun calculateNextRefreshEpochMillis(
        now: ZonedDateTime,
        nextAlarmTrigger: ZonedDateTime?,
    ): Long {
        val nextMidnight = nextMidnightEpochMillis(now)
        val insuranceTrigger = nextAlarmTrigger?.toInstant()?.toEpochMilli()?.plus(1_000L)
        return if (insuranceTrigger != null && insuranceTrigger < nextMidnight) {
            insuranceTrigger
        } else {
            nextMidnight
        }
    }

    /**
     * 以前の版が登録した旧Action（ACTION_WIDGET_TICK）の予約を取り消す。
     * Action名が異なるPendingIntentは新Actionで上書きされないため、更新後に残った予約を明示的に消すために用いる。
     */
    private fun cancelLegacyTick(context: Context, alarmManager: AlarmManager) {
        val legacyIntent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = ACTION_WIDGET_TICK
        }
        val legacyPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_WIDGET_REFRESH,
            legacyIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (legacyPendingIntent != null) {
            alarmManager.cancel(legacyPendingIntent)
            legacyPendingIntent.cancel()
        }
    }

    /**
     * 次に描き直しが必要な時刻（次の 0:00、または手前の次の鳴動＋1秒）にウィジェット更新をAlarmManagerへ予約する。
     * 呼び忘れを防ぐため、処理の先頭で以前の版の旧予約（ACTION_WIDGET_TICK）を明示的に取り消す。
     *
     * 【予約型（RTC_WAKEUP）の選定理由】
     * 日付の切り替わりで確実にウィジェットを更新するため、端末を起こす RTC_WAKEUP を用いる。
     * 次の鳴動＋1秒の保険も端末を起こすが、鳴動の1秒後は端末がすでに起きているため電池への影響は無い。
     * 正確なアラームの権限があるときは setExactAndAllowWhileIdle、
     * 権限が無いときは setAndAllowWhileIdle を使用する。
     */
    suspend fun scheduleNextRefresh(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        cancelLegacyTick(context, alarmManager)

        val now = ZonedDateTime.now()
        val nextAlarm = nextWidgetAlarmTrigger(context, now)
        val triggerAtMillis = calculateNextRefreshEpochMillis(now, nextAlarm)
        val pendingIntent = refreshPendingIntent(context)

        if (ExactAlarmPermission.isGranted(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    /**
     * 登録済みのウィジェット更新アラームを取り消す。
     * ホーム画面から全ウィジェットが削除された際に無駄な発火を止めるために用いる。
     */
    fun cancelRefresh(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(refreshPendingIntent(context))
    }

    /**
     * 更新発火を受け取るWidgetUpdateReceiver向けのPendingIntentを生成する。
     * 予約と取り消しで同じ PendingIntent に一致させるため、生成をここへまとめる。
     */
    private fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = ACTION_WIDGET_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_WIDGET_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
