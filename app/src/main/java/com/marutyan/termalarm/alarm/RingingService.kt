package com.marutyan.termalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.AlarmRepository
import com.marutyan.termalarm.domain.AlarmSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var timeoutJob: Job? = null
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
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, -1)
                stopRinging { id -> if (minutes > 0) AlarmScheduler.onSnoozed(this, id, minutes) }
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
            startForegroundNotification()
            playSound(schedule)
            if (schedule.vibrate) startVibration()
            scheduleAutoStop()
        }
    }

    // 一定時間(既定10分)操作が無ければ、無視されたものとして「停止」と同じ扱いにする（docs/SPEC.md「無視（放置）」）
    private fun scheduleAutoStop() {
        timeoutJob = scope.launch {
            delay(RINGING_AUTO_STOP_TIMEOUT_MILLIS)
            stopRinging { id -> AlarmScheduler.onStopped(this@RingingService, id) }
        }
    }

    // 音・バイブ・タイムアウトを止め、rescheduleの完了後にサービスを終了する共通処理
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

    private fun playSound(schedule: AlarmSchedule) {
        val uri: Uri = schedule.soundUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: return
        mediaPlayer = MediaPlayer().apply {
            // マナーモードでも鳴る必要があるため、通知/メディアではなくALARM用途を明示する（docs/SPEC.md「鳴動」節）
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            isLooping = true
            runCatching {
                setDataSource(this@RingingService, uri)
                prepare()
                start()
            }
        }
    }

    private fun startVibration() {
        // 1秒鳴動→1秒休止を無操作タイムアウトまで繰り返すパターン
        val pattern = longArrayOf(0, 1000, 1000)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API31以降はVibratorManager経由での取得が推奨される
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 1))
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
    private fun ensureChannel(): String {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.ringing_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }

    private fun repository(): AlarmRepository = AlarmRepository(AlarmDatabase.getInstance(this).alarmDao())

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
