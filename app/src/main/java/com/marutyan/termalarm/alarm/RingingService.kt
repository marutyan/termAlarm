package com.marutyan.termalarm.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
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
import com.marutyan.termalarm.domain.StopMethod
import com.marutyan.termalarm.domain.remainingOccurrenceCount
import com.marutyan.termalarm.ui.common.clockTimePattern
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

    // いま鳴っているのが二度寝チェックか。止めたときにもう一度チェックを入れないために持つ
    private var currentIsWakeCheck: Boolean = false

    // 鳴動中のoccurrenceの実時刻。domainの残り回数計算・当日終了のセッション判定に使う
    private fun occurrenceAt(): ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTriggerAtMillis), ZoneId.systemDefault())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val method = intent.getStringExtra(EXTRA_STOP_METHOD)
                val wasWakeCheck = currentIsWakeCheck
                val stoppedOccurrence = occurrenceAt()
                stopRinging(isTimeout = false, explicitStopMethod = method) { id ->
                    AlarmScheduler.onStopped(this, id, stoppedOccurrence, wasWakeCheck)
                }
            }
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
        // すでに別のタームが鳴っているなら、そちらを先に畳んでから切り替える。
        // 畳まずに上書きすると、前のMediaPlayerを掴む手段が無くなって鳴り続け、
        // そのタームの記録も次回の予約も行われないまま取り残される
        if (currentAlarmId != -1L && currentAlarmId != id) {
            handOverFromPreviousAlarm()
        }
        currentAlarmId = id
        currentIsWakeCheck = intent?.getBooleanExtra(EXTRA_IS_WAKE_CHECK, false) == true
        currentTriggerAtMillis = intent?.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, System.currentTimeMillis())
            ?: System.currentTimeMillis()

        // startForegroundService()からは数秒以内にstartForeground()を呼ぶ必要がある。
        // タームを読み終えるのを待つと間に合わないことがあるため、まず中身の無い通知で立つ
        startForegroundPlaceholder()
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
            // 鳴り始めた時点で「次のアラーム」の予告は役目を終える。次回分は停止後に出し直される
            AlarmNotifications.cancelUpcoming(this@RingingService, id)
            playSound(settings.alarmSoundUri, settings.fadeInSeconds)
            if (settings.vibration) startVibration()
            settings.silenceAfterMinutes?.let { scheduleAutoStop(it) }
        }
    }

    /**
     * 別のタームが鳴り始めるとき、いま鳴っているぶんを畳む。
     *
     * 利用者が止めたわけではないので、放置として記録する。
     * 鳴ったこと自体は事実であり、記録から落とすと実績が実態とずれる。
     */
    private fun handOverFromPreviousAlarm() {
        val previousId = currentAlarmId
        val previousTrigger = currentTriggerAtMillis
        val previousIsWakeCheck = currentIsWakeCheck
        val previousOccurrence = occurrenceAt()
        currentAlarmId = -1L
        releaseRingingResources()
        scope.launch {
            runCatching {
                recordRingSession(
                    previousId,
                    previousTrigger,
                    isTimeout = true,
                    explicitStopMethod = StopMethod.AUTO_SILENCED.name,
                )
            }
            runCatching {
                AlarmScheduler.onStopped(
                    this@RingingService,
                    previousId,
                    previousOccurrence,
                    previousIsWakeCheck,
                )
            }
        }
    }

    // 一定時間(設定「消音までの時間」)操作が無ければ、無視されたものとして
    // 「停止」と同じ扱いにする（docs/SPEC.md「無視（放置）」）
    private fun scheduleAutoStop(silenceMinutes: Int) {
        val wasWakeCheck = currentIsWakeCheck
        val stoppedOccurrence = occurrenceAt()
        timeoutJob = scope.launch {
            delay(silenceMinutes * 60_000L)
            stopRinging(isTimeout = true, explicitStopMethod = StopMethod.AUTO_SILENCED.name) { id ->
                AlarmScheduler.onStopped(this@RingingService, id, stoppedOccurrence, wasWakeCheck)
            }
        }
    }

    /**
     * 音・バイブ・自動消音の予約を片付ける。
     * 停止のときと、別のタームへ切り替えるときの両方から使う。
     */
    private fun releaseRingingResources() {
        timeoutJob?.cancel()
        timeoutJob = null
        mediaPlayer?.let { player -> runCatching { player.stop() }; player.release() }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopRinging(
        isTimeout: Boolean = false,
        explicitStopMethod: String? = null,
        reschedule: suspend (Long) -> Unit,
    ) {
        val id = currentAlarmId
        // 鳴動画面の「ストップ」と通知の「停止」がほぼ同時に届くと、
        // 記録が二重に入り予約も二重に走る。片方だけを通す
        if (id == -1L) return
        val triggerMillis = currentTriggerAtMillis
        currentAlarmId = -1L
        releaseRingingResources()

        // 通知の操作ボタンから止めた場合、ロック画面に出ている鳴動画面が取り残されるため閉じさせる
        sendBroadcast(Intent(ACTION_RINGING_FINISHED).setPackage(packageName))

        scope.launch {
            try {
                if (id != -1L) {
                    // 記録の失敗で予約の入れ直しまで巻き添えにしない。記録は残らなくても鳴り続ける方が大事
                    runCatching { recordRingSession(id, triggerMillis, isTimeout, explicitStopMethod) }
                    runCatching { reschedule(id) }
                }
            } finally {
                // 何が失敗しても前面サービスは必ず畳む。残すと通知が消えず、次の鳴動にも影響する
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * 鳴動停止時の実績を1件保存する。
     * 停止方法、予定時刻、実停止時刻、セッション開始日、回数をDBに記録するために用いる。
     */
    private suspend fun recordRingSession(
        alarmId: Long,
        triggerMillis: Long,
        isTimeout: Boolean,
        explicitStopMethod: String?,
    ) {
        val schedule = repository().getById(alarmId) ?: return
        val occurrenceAt = occurrenceAt()
        val sessionStart = com.marutyan.termalarm.domain.sessionStartDate(schedule, occurrenceAt)
        val totalCount = com.marutyan.termalarm.domain.occurrenceCount(schedule)
        val remainingCount = com.marutyan.termalarm.domain.remainingOccurrenceCount(schedule, occurrenceAt)
        val occurrenceIndex = (totalCount - remainingCount - 1).coerceAtLeast(0)

        val stopMethod = explicitStopMethod ?: if (isTimeout) {
            com.marutyan.termalarm.domain.StopMethod.AUTO_SILENCED.name
        } else {
            val hasChallenge = com.marutyan.termalarm.domain.challengeQuestionCount(schedule, occurrenceIndex) > 0
            if (hasChallenge) com.marutyan.termalarm.domain.StopMethod.CHALLENGE.name else com.marutyan.termalarm.domain.StopMethod.TAP.name
        }
        val stoppedAt = if (isTimeout) null else System.currentTimeMillis()

        Repositories.wakeRecord(this@RingingService).record(
            alarmId = alarmId,
            sessionStart = sessionStart.toEpochDay(),
            scheduledAt = triggerMillis,
            stoppedAt = stoppedAt,
            stopMethod = stopMethod,
            occurrenceIndex = occurrenceIndex,
        )
    }

    // アラームの音を鳴らす。寝ている人を起こすため、設定「徐々に音量を上げる」の秒数かけて音量を上げる。
    // 選ばれている音を開けないときの代わりの音はSoundFadeIn側が持つ
    private fun playSound(soundUri: String?, fadeInSeconds: Int) {
        mediaPlayer = SoundFadeIn.startRinging(this, scope, soundUri, fadeInSeconds)
    }

    private fun startVibration() {
        vibrator = AlarmVibration.start(this)
    }

    /**
     * 鳴動中に出す通知。純正の時計アプリと同じく、タイトルにアラームの名前、本文に鳴っている時刻を出し、
     * 展開すると停止を押せるようにする。
     */
    /**
     * 鳴っているタームを読み終える前に、フォアグラウンドとして立つためだけに出す通知。
     * 中身は[startForegroundNotification]がすぐ上書きする。
     */
    private fun startForegroundPlaceholder() {
        val notification = NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.ringing_notification_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun startForegroundNotification(schedule: AlarmSchedule) {
        val fullScreenIntent = RingingActivity.fullScreenPendingIntent(this, currentAlarmId, currentTriggerAtMillis, currentIsWakeCheck)
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

        builder.addAction(0, getString(R.string.ringing_stop), servicePendingIntent(REQUEST_STOP, stopIntent(this)))

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
        val time = at.format(ringingTimeFormatter())
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

        // 通知の操作ボタンごとに割り当てるrequestCode
        private const val REQUEST_STOP = 2

        // 鳴動画面(RingingActivity)へ「もう鳴っていないので閉じてよい」と伝えるための合図。
        // 自アプリ内でのみ送受信する（受け取り側はRECEIVER_NOT_EXPORTEDで登録する）
        const val ACTION_RINGING_FINISHED = "com.marutyan.termalarm.alarm.action.RINGING_FINISHED"

        // 通知の本文に出す鳴動時刻の書式。純正の「3:45 (日)」に合わせ、時刻と曜日を1行で示す。
        // 時刻の部分は端末の「24時間表示」設定に従う
        private fun ringingTimeFormatter(): DateTimeFormatter =
            DateTimeFormatter.ofPattern(clockTimePattern() + "（E）", Locale.getDefault())

        const val ACTION_STOP = "com.marutyan.termalarm.alarm.action.STOP"
        const val EXTRA_STOP_METHOD = "com.marutyan.termalarm.alarm.extra.STOP_METHOD"

        /**
         * 鳴動停止用のIntentを生成する。
         * 停止方法(CHALLENGE / TAP / AUTO_SILENCED)を指定してサービスへ渡すために用いる。
         */
        fun stopIntent(context: Context, stopMethod: com.marutyan.termalarm.domain.StopMethod? = null): Intent =
            Intent(context, RingingService::class.java).apply {
                action = ACTION_STOP
                if (stopMethod != null) {
                    putExtra(EXTRA_STOP_METHOD, stopMethod.name)
                }
            }
    }
}

