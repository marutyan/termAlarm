package com.marutyan.termalarm.alarm

import android.app.NotificationManager
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
            ACTION_SKIP -> stopRinging { id -> AlarmScheduler.onSkippedFromRingingScreen(this, id, occurrenceAt()) }
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
            startForegroundNotification()
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

    private fun startForegroundNotification() {
        val fullScreenIntent = RingingActivity.fullScreenPendingIntent(this, currentAlarmId, currentTriggerAtMillis)
        val notification = NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.ringing_notification_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

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
