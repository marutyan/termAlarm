package com.marutyan.termalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.isOneShotSessionFinished
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import com.marutyan.termalarm.domain.shouldPerformWakeCheck
import com.marutyan.termalarm.domain.wakeCheckTime
import com.marutyan.termalarm.domain.nextTrigger
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime

/**
 * AlarmManagerへの予約登録・解除をすべて担う。1件のAlarmScheduleにつき常に「次の1回」だけを
 * setAlarmClock()で登録し、全回を一括登録しない（docs/SPEC.md「予約の方式」）。
 *
 * **鳴る時刻を変える保存は、必ずこの object を通すこと。**
 * 「保存する」と「予約を入れ直す」を呼び出し側それぞれに任せると、片方だけ呼ぶ経路が必ず生まれる。
 * 実際に、一覧の切り替えとタームの終了で予約の入れ直しが漏れ、切り替えても鳴らない・
 * 終了させても次の1回が鳴る、という不具合が起きた。
 * そのため保存と予約をここで対にし、[setEnabled] [updateSchedule] [endSession] を入口とする。
 * 呼び出し側から Repository の書き込みを直接行わないこと。
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
        // 二度寝チェックも片付ける。切ったのに後から確認が鳴ると驚かせる
        alarmManager(context).cancel(wakeCheckPendingIntent(context, id))
        AlarmNotifications.cancelUpcoming(context, id)
    }

    /**
     * 有効・無効を切り替え、予約もそれに合わせる。
     * 保存だけだと、オンにしても鳴らず、オフにしても鳴り続ける。
     */
    suspend fun setEnabled(context: Context, id: Long, enabled: Boolean) {
        repository(context).setEnabled(id, enabled)
        if (enabled) reschedule(context, id) else cancel(context, id)
    }

    /**
     * タームの内容を保存し、予約もそれに合わせる。
     * 時刻・間隔・曜日のどれが変わっても、次に鳴る回は変わりうる。
     */
    suspend fun updateSchedule(context: Context, schedule: AlarmSchedule) {
        repository(context).update(schedule)
        if (schedule.enabled) reschedule(context, schedule.id) else cancel(context, schedule.id)
    }

    /**
     * タームを新しく登録し、予約も入れる。登録したidを返す。
     */
    suspend fun addSchedule(context: Context, schedule: AlarmSchedule): Long {
        val id = repository(context).add(schedule)
        reschedule(context, id)
        return id
    }

    /**
     * タームを削除し、予約も取り消す。
     */
    suspend fun deleteSchedule(context: Context, schedule: AlarmSchedule) {
        repository(context).delete(schedule)
        cancel(context, schedule.id)
    }

    /**
     * 鳴動画面の「停止」、および無操作タイムアウト時に呼ぶ。
     * 曜日を指定していないタームはここで鳴り終わりを判定し、自分でオフにする。
     * そうでなければ次の1回を予約し直す。
     */
    suspend fun onStopped(
        context: Context,
        id: Long,
        occurrenceAt: ZonedDateTime = ZonedDateTime.now(),
        isWakeCheck: Boolean = false,
    ) {
        val schedule = repository(context).getById(id)
        if (!disableIfOneShotFinished(context, id)) {
            reschedule(context, id)
        }
        // 二度寝チェックを止めたときは、もう一度チェックを入れない。そのセッションはここで終わる
        if (schedule != null && !isWakeCheck) {
            scheduleWakeCheckIfSessionDone(context, schedule, occurrenceAt)
        }
    }

    /**
     * 範囲の最後の回を止めたときだけ、二度寝チェックを1回予約する。
     *
     * 範囲の中では間隔で鳴り続けるため、途中に確認を挟むと役目が重なる。
     * だから範囲の外に1回だけ置く（docs/SPEC.md「二度寝チェック」）。
     * 「タームを終了」で終わらせたセッションでは確認しない。その判定は
     * [shouldPerformWakeCheck] が skippedSessionStart を見て行う。
     */
    private suspend fun scheduleWakeCheckIfSessionDone(
        context: Context,
        schedule: AlarmSchedule,
        occurrenceAt: ZonedDateTime,
    ) {
        if (!shouldPerformWakeCheck(schedule, occurrenceAt)) return
        // まだ範囲の中に鳴る回が残っているなら、最後の回ではない
        if (remainingOccurrenceCount(schedule, occurrenceAt) > 0) return
        val minutes = Repositories.settings(context).observe().first().wakeCheckMinutes
        val at = wakeCheckTime(schedule, ZonedDateTime.now(), minutes) ?: return
        registerWakeCheck(context, schedule.id, at)
    }

    // 二度寝チェックの予約。鳴らすので、通常の鳴動と同じく確実な方法で登録する
    private fun registerWakeCheck(context: Context, id: Long, at: ZonedDateTime) {
        if (!ExactAlarmPermission.isGranted(context)) return
        val triggerAtMillis = at.toInstant().toEpochMilli()
        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent(context, id))
        alarmManager(context).setAlarmClock(info, wakeCheckPendingIntent(context, id, triggerAtMillis))
    }

    // 二度寝チェックの予約の宛先。鳴動用・事前通知用とはactionで区別される
    private fun wakeCheckPendingIntent(context: Context, id: Long, triggerAtMillis: Long = 0L): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java)
            .setAction(ACTION_WAKE_CHECK)
            .putExtra(EXTRA_ALARM_ID, id)
            .putExtra(EXTRA_TRIGGER_AT_MILLIS, triggerAtMillis)
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 曜日を指定していないタームのぶんが鳴り終わっていたら、オフにして予約を片付ける。
     * オフにしたときだけtrueを返す。
     */
    private suspend fun disableIfOneShotFinished(context: Context, id: Long): Boolean {
        val repo = repository(context)
        val schedule = repo.getById(id) ?: return false
        if (!isOneShotSessionFinished(schedule, ZonedDateTime.now())) return false
        repo.setEnabled(id, false)
        cancel(context, id)
        return true
    }

    /**
     * 当日のタームを終了する。
     * occurrenceAt からセッション開始日を求めて skippedSessionStart へ書き込み、次回を予約する。
     */
    suspend fun onSessionEnded(context: Context, id: Long, occurrenceAt: ZonedDateTime) {
        // 当日終了の永続化ルール自体はAlarmRepository.endTodaySession()に一本化する（担当Bの実装と重複させない）
        val repo = repository(context)
        repo.endTodaySession(id, occurrenceAt)
        // 曜日を指定していないタームは、今日のぶんを終えたらもう鳴るものが無いのでオフにする
        if (disableIfOneShotFinished(context, id)) return
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
