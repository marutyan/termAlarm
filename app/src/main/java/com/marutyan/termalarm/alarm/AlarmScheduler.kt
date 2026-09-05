package com.marutyan.termalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.nextTrigger
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime

/**
 * AlarmManagerへの予約登録・解除をすべて担う。1件のAlarmScheduleにつき常に「次の1回」だけを
 * setAlarmClock()で登録し、全回を一括登録しない（docs/SPEC.md「予約の方式」）。
 * ui層はRepositoryを追加・変更・削除・有効切替した直後、必ず reschedule/rescheduleAll を呼び直す契約とする。
 */
object AlarmScheduler {

    // 事前通知を出し始める、鳴動時刻からの前倒し時間。純正の時計アプリに合わせて2時間とする
    private val UPCOMING_LEAD: Duration = Duration.ofHours(2)

    // 指定idのアラームをRepositoryから読み直し、次の1回を再計算して登録し直す。
    // 削除済み・見つからない場合は予約を取り消すだけにする
    suspend fun reschedule(context: Context, id: Long) {
        val schedule = repository(context).getById(id)
        if (schedule == null) {
            cancel(context, id)
        } else {
            scheduleNextOccurrence(context, schedule)
        }
    }

    // 全アラームの予約を再計算して登録し直す。BOOT_COMPLETED等のブロードキャスト契機で使う
    suspend fun rescheduleAll(context: Context) {
        repository(context).observeAll().first().forEach { scheduleNextOccurrence(context, it) }
    }

    // 指定idの予約を取り消す。鳴動の予約だけでなく、事前通知の予約と掲示中の通知も一緒に片付ける
    fun cancel(context: Context, id: Long) {
        alarmManager(context).cancel(operationPendingIntent(context, id))
        alarmManager(context).cancel(upcomingPendingIntent(context, id))
        AlarmNotifications.cancelUpcoming(context, id)
    }

    // 鳴動画面の「停止」、および無操作タイムアウト時に呼ぶ。次の1回を予約する（結果は同じなのでrescheduleに委譲）
    suspend fun onStopped(context: Context, id: Long) = reschedule(context, id)

    /**
     * 鳴動画面の「スヌーズ」。snoozeMinutes分後に再度鳴らすよう登録する。
     * ただしそのスヌーズ時刻が次回の鳴動予定時刻以降になる場合は、スヌーズを行わず次回予定を優先する
     * （docs/SPEC.md「スヌーズ」）。戻り値はスヌーズを実際に登録できたかどうか
     */
    suspend fun onSnoozed(context: Context, id: Long, snoozeMinutes: Int): Boolean {
        val schedule = repository(context).getById(id) ?: return false
        val now = ZonedDateTime.now()
        val snoozeAt = now.plusMinutes(snoozeMinutes.toLong())
        val next = nextTrigger(schedule, now)
        return if (next != null && !snoozeAt.isBefore(next)) {
            // スヌーズ時刻が次回予定以降になるため、スヌーズはせず次回予定を優先する
            scheduleNextOccurrence(context, schedule)
            false
        } else {
            registerExact(context, id, snoozeAt)
            true
        }
    }

    /**
     * 当日のタームを終了する（skipRequiresApp==falseのときだけ現れる、鳴動画面と事前通知の導線から呼ばれる）。
     * occurrenceAt からセッション開始日を求めて skippedSessionStart へ書き込み、次回を予約する。
     */
    suspend fun onSessionEnded(context: Context, id: Long, occurrenceAt: ZonedDateTime) {
        // 当日終了の永続化ルール自体はAlarmRepository.endTodaySession()に一本化する（担当Bの実装と重複させない）
        val repo = repository(context)
        repo.endTodaySession(id, occurrenceAt)
        val schedule = repo.getById(id) ?: return
        scheduleNextOccurrence(context, schedule)
    }

    private fun scheduleNextOccurrence(context: Context, schedule: AlarmSchedule) {
        val now = ZonedDateTime.now()
        val next = nextTrigger(schedule, now)
        if (next == null) {
            cancel(context, schedule.id)
        } else {
            registerExact(context, schedule.id, next)
            refreshUpcomingNotification(context, schedule, next, now)
        }
    }

    /**
     * 事前通知（「次のアラーム」）の面倒を見る。純正の時計アプリと同じく鳴動の2時間前から出すため、
     * まだ2時間より前なら通知を消したうえで、出す時刻に起きるための予約だけを入れておく。
     */
    private fun refreshUpcomingNotification(
        context: Context,
        schedule: AlarmSchedule,
        next: ZonedDateTime,
        now: ZonedDateTime,
    ) {
        val showFrom = next.minus(UPCOMING_LEAD)
        if (now.isBefore(showFrom)) {
            AlarmNotifications.cancelUpcoming(context, schedule.id)
            registerUpcomingWake(context, schedule.id, showFrom)
        } else {
            AlarmNotifications.postUpcoming(context, schedule, next, now)
        }
    }

    // 事前通知を出す時刻に一度だけ起きるための予約。鳴らすわけではないので、
    // 正確さより電池を優先するsetAndAllowWhileIdle()で足りる（多少ずれても通知が出る時刻が前後するだけ）
    private fun registerUpcomingWake(context: Context, id: Long, at: ZonedDateTime) {
        alarmManager(context).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.toInstant().toEpochMilli(),
            upcomingPendingIntent(context, id),
        )
    }

    // setAlarmClock()で厳密な時刻に1回だけ予約する。Doze中も確実に発火し、ステータスバーに次のアラームが表示される
    private fun registerExact(context: Context, id: Long, at: ZonedDateTime) {
        // 権限が無い状態でsetAlarmClock()を呼ぶとSecurityExceptionになるため、無ければ何もしない。
        // 権限取得後の再予約はui層がcanScheduleExactAlarms()を確認してrescheduleAll()を呼ぶ契約
        if (!ExactAlarmPermission.isGranted(context)) return
        val triggerAtMillis = at.toInstant().toEpochMilli()
        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent(context, id))
        alarmManager(context).setAlarmClock(info, operationPendingIntent(context, id, triggerAtMillis))
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun repository(context: Context): AlarmRepository =
        Repositories.alarm(context)

    // AlarmManagerが発火時に送るPendingIntent。requestCodeをidにすることでアラームごとに別々の予約として扱う
    private fun operationPendingIntent(context: Context, id: Long, triggerAtMillis: Long = 0L): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, id)
            putExtra(EXTRA_TRIGGER_AT_MILLIS, triggerAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // 事前通知を出すための予約。actionが違えば別のPendingIntentとして扱われるため、
    // 鳴動用(operationPendingIntent)と同じrequestCodeでも取り違えは起きない
    private fun upcomingPendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java)
            .setAction(ACTION_UPCOMING)
            .putExtra(EXTRA_ALARM_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ステータスバーの「次のアラーム」表示をタップしたときに開く画面。アプリの入口(MainActivity)を指す
    private fun showPendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
