package com.marutyan.termalarm.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.notification.NotificationChannels
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import com.marutyan.termalarm.domain.canEndTodaySession
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * アラーム鳴動中だけ生存するフォアグラウンドサービス。音とバイブを鳴らし、全画面通知で
 * RingingActivityを起動する。停止・スヌーズ・当日終了の各操作はここで受け取り、AlarmSchedulerへ
 * 委譲して次回の予約まで行う（docs/SPEC.md「鳴動」節）。RingingActivityはボタン操作をIntentで
 * このサービスへ送るだけで、状態遷移の実体はすべてここに一本化する。
 */
class RingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 無操作のまま一定時間が過ぎたら自動で止めるための予約
    private var timeoutJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentAlarmId: Long = -1L
    private var currentTriggerAtMillis: Long = -1L

    // 鳴動中のoccurrenceの実時刻。domainの残り回数計算・当日終了のセッション判定に使う
    private fun occurrenceAt(): ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTriggerAtMillis), ZoneId.systemDefault())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRinging { id -> AlarmScheduler.onStopped(this, id) }
            ACTION_SNOOZE -> {
                val requestedMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, -1)
                stopRinging { id -> snoozeOrStop(id, requestedMinutes) }
            }
            ACTION_SKIP -> stopRinging { id -> AlarmScheduler.onSessionEnded(this, id, occurrenceAt()) }
            else -> startRinging(intent)
        }
        return START_NOT_STICKY
    }

    private fun startRinging(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        if (id == -1L) {
            stopSelf()
            return
        }
        currentAlarmId = id
        currentTriggerAtMillis = intent?.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, System.currentTimeMillis())
            ?: System.currentTimeMillis()

        scope.launch {
            val schedule = repository().getById(id)
            if (schedule == null) {
                stopSelf()
                return@launch
            }
            // 鳴動セッション開始時に一度だけ設定を読む。鳴動中に設定画面から値が変わることは想定しないため、
            // 都度DBへ問い合わせず、この時点の値をセッション終了まで使い続ける
            val settings = settingsRepository().observe().first()
            startForegroundNotification(schedule)
            // 鳴り始めた時点で「次のアラーム」の予告は役目を終える。次回分は停止・スヌーズ後に出し直される
            AlarmNotifications.cancelUpcoming(this@RingingService, id)
            playSound(schedule, settings.alarmFadeInSeconds)
            if (schedule.vibrate) startVibration()
            scheduleAutoStop(settings.autoStopMinutes)
        }
    }

    // 一定時間(設定「消音までの時間」、既定10分)操作が無ければ、無視されたものとして
    // 「停止」と同じ扱いにする（docs/SPEC.md「無視（放置）」）
    private fun scheduleAutoStop(autoStopMinutes: Int) {
        timeoutJob = scope.launch {
            delay(autoStopMinutes * 60_000L)
            stopRinging { id -> AlarmScheduler.onStopped(this@RingingService, id) }
        }
    }

    // 音・バイブ・タイムアウトを止め、rescheduleの完了後にサービスを終了する共通処理
    /**
     * スヌーズ操作の分岐。鳴動画面からは常に有効な分数が渡されるが、SNOOZE_ALARMインテント
     * (外部アプリやアシスタント経由)は分数を指定せず呼ばれることがあるため、その場合はDBの
     * snoozeMinutesを見て解決する。スヌーズ無効(null)のアラームに対しては、利用者が明示的に
     * 選んだ設定を外部インテントで上書きせず、停止(次回予約)と同じ扱いにする。
     */
    private suspend fun snoozeOrStop(id: Long, requestedMinutes: Int) {
        val minutes = requestedMinutes.takeIf { it > 0 } ?: repository().getById(id)?.snoozeMinutes
        if (minutes != null && minutes > 0) {
            AlarmScheduler.onSnoozed(this, id, minutes)
        } else {
            AlarmScheduler.onStopped(this, id)
        }
    }

    private fun stopRinging(reschedule: suspend (Long) -> Unit) {
        val id = currentAlarmId
        timeoutJob?.cancel()
        mediaPlayer?.let { player -> runCatching { player.stop() }; player.release() }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null

        // 通知の操作ボタンから止めた場合、ロック画面に出ている鳴動画面が取り残されるため閉じさせる
        sendBroadcast(Intent(ACTION_RINGING_FINISHED).setPackage(packageName))

        scope.launch {
            if (id != -1L) reschedule(id)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun playSound(schedule: AlarmSchedule, fadeInSeconds: Int) {
        val uri: Uri = schedule.soundUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: return
        // 寝ている人を起こすため、設定「徐々に音量を上げる」の秒数(既定5秒)かけて音量を上げる
        mediaPlayer = SoundFadeIn.startRinging(this, scope, uri, fadeInSeconds)
    }

    private fun startVibration() {
        vibrator = AlarmVibration.start(this)
    }

    /**
     * 鳴動中に出す通知。純正の時計アプリと同じく、タイトルにアラームの名前、本文に鳴っている時刻を出し、
     * 展開するとスヌーズと停止を押せるようにする。純正が1回分の操作しか持たないのに対し、
     * このアプリは時間帯（ターム）で鳴らすため、本文へ残り回数を、操作へ「このタームを終了」を足す。
     */
    private fun startForegroundNotification(schedule: AlarmSchedule) {
        val fullScreenIntent = RingingActivity.fullScreenPendingIntent(this, currentAlarmId, currentTriggerAtMillis)
        val builder = NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(schedule.label.ifBlank { getString(R.string.ringing_notification_title) })
            .setContentText(ringingText(schedule))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setLocalOnly(true)
            // 5分間隔のタームでは通知が何度も出し直されるため、音や振動の合図は最初の1回だけにする
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)

        // 操作の並びは純正に合わせてスヌーズ・停止の順にする
        val snoozeMinutes = schedule.snoozeMinutes
        if (snoozeMinutes != null) {
            builder.addAction(0, getString(R.string.ringing_snooze), servicePendingIntent(REQUEST_SNOOZE, snoozeIntent(this, snoozeMinutes)))
        }
        builder.addAction(0, getString(R.string.ringing_stop), servicePendingIntent(REQUEST_STOP, stopIntent(this)))
        // 寝ぼけたまま押せてしまう事故を防ぐ設定(skipRequiresApp)のときは、鳴動画面と同じく操作を出さない
        if (!schedule.skipRequiresApp && canEndTodaySession(schedule, occurrenceAt())) {
            builder.addAction(0, getString(R.string.ringing_skip_today), servicePendingIntent(REQUEST_SKIP, skipIntent(this)))
        }

        val notification = builder.build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    // 通知の本文。「7:05（日）・あと24回」のように、鳴っている時刻とタームの残り回数を並べる。
    // 単発に退化しているアラーム（残り0回）では回数を出さず、純正と同じく時刻だけにする
    private fun ringingText(schedule: AlarmSchedule): String {
        val at = occurrenceAt()
        val time = at.format(RINGING_TIME_FORMATTER)
        val remaining = remainingOccurrenceCount(schedule, at)
        return if (remaining > 0) getString(R.string.ringing_notification_text_with_remaining, time, remaining) else time
    }

    // 通知の操作ボタンから、このサービス自身へIntentを送り返すためのPendingIntent。
    // 押したボタンと違う操作が走らないよう、操作ごとに別のrequestCodeを与えて確実に別物として扱わせる
    private fun servicePendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    // 通知チャンネルは一度だけ作成すればよい。音はサービス側のMediaPlayerが鳴らすため、
    // チャンネル自体の音源はnullにする（docs/SPEC.md「チャンネル側では音を鳴らさない」）
    private fun ensureChannel(): String = NotificationChannels.ensure(
        this,
        CHANNEL_ID,
        R.string.ringing_channel_name,
        NotificationManager.IMPORTANCE_HIGH,
    )

    private fun repository(): AlarmRepository = Repositories.alarm(this)

    private fun settingsRepository(): SettingsRepository = Repositories.settings(this)

    override fun onDestroy() {
        super.onDestroy()
        timeoutJob?.cancel()
        mediaPlayer?.let { player -> runCatching { player.stop() }; player.release() }
        vibrator?.cancel()
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "ringing"
        private const val NOTIFICATION_ID = 1001

        // 通知の操作ボタンごとに割り当てるrequestCode。同じ値を使い回すと押したボタンと違う操作が走りうる
        private const val REQUEST_SNOOZE = 1
        private const val REQUEST_STOP = 2
        private const val REQUEST_SKIP = 3

        // 鳴動画面(RingingActivity)へ「もう鳴っていないので閉じてよい」と伝えるための合図。
        // 自アプリ内でのみ送受信する（受け取り側はRECEIVER_NOT_EXPORTEDで登録する）
        const val ACTION_RINGING_FINISHED = "com.marutyan.termalarm.alarm.action.RINGING_FINISHED"

        // 通知の本文に出す鳴動時刻の書式。純正の「3:45 (日)」に合わせ、時刻と曜日を1行で示す
        private val RINGING_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("H:mm（E）", Locale.JAPANESE)

        const val ACTION_STOP = "com.marutyan.termalarm.alarm.action.STOP"
        const val ACTION_SNOOZE = "com.marutyan.termalarm.alarm.action.SNOOZE"
        const val ACTION_SKIP = "com.marutyan.termalarm.alarm.action.SKIP"
        const val EXTRA_SNOOZE_MINUTES = "com.marutyan.termalarm.alarm.EXTRA_SNOOZE_MINUTES"

        fun stopIntent(context: Context): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_STOP)

        fun snoozeIntent(context: Context, minutes: Int): Intent =
            Intent(context, RingingService::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_SNOOZE_MINUTES, minutes)

        fun skipIntent(context: Context): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_SKIP)
    }
}
