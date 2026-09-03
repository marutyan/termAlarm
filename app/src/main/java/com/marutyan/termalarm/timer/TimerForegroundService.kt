package com.marutyan.termalarm.timer

import com.marutyan.termalarm.alarm.SoundFadeIn
import android.app.NotificationChannel
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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.finishTimer
import com.marutyan.termalarm.domain.isDue
import com.marutyan.termalarm.domain.remainingMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 動作中または鳴動中(FINISHED)のタイマーが1件でもある間だけ生存するフォアグラウンドサービス。
 * 1秒ごとにRepositoryを読み直し、(1)期限が来たRUNNINGをFINISHEDへ遷移して保存、
 * (2)FINISHED中の全idに鳴動用MediaPlayerを割り当て、消えたidの再生を止め、
 * (3)通知を更新し、(4)動作中・鳴動中が0件になったら自分を止める、という1本のループで完結させる。
 * 画面を閉じても動き続ける要件(docs/SPEC.md「タイマータブ」)を満たす部分はこのループが担う。
 *
 * 完了時の音はalarm/RingingServiceと同じくUSAGE_ALARMのMediaPlayerで鳴らす(docs/SPEC.md
 * 「アラームと同じUSAGE_ALARMを使い、マナーモードでも鳴らす」)。AudioAttributesの組み立ては
 * alarm/SoundFadeIn.alarmAudioAttributes()を共有する。フェードイン(鳴り始めに音量を徐々に上げる)の
 * 秒数はアラームより短い設定既定値を使う(起きている人へ知らせるだけのため)。
 */
class TimerForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    // 鳴動中(FINISHED)のタイマーidごとのMediaPlayer。複数のタイマーが同時に完了しても個別に鳴らし続けられる
    private val ringingPlayers = mutableMapOf<Long, MediaPlayer>()

    // 鳴動中の全タイマーで共有する単一のVibrator。端末のバイブは1つしか無く、タイマーごとに分けられないため
    private var vibrator: Vibrator? = null

    // サービス起動時に一度だけ読み込む設定値。このサービスは鳴動中/動作中のタイマーが無くなると自ら停止し、
    // 次のタイマー開始時に作り直される(ensureRunning参照)ため、都度DBを見に行かずonCreateの1回読みで足りる
    private var settings = AppSettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // startForegroundService()からは数秒以内にstartForeground()を呼ぶ必要があるため、
        // 内容が確定する最初のtick()を待たずここで一旦空の通知を出す
        updateNotification(emptyList(), SystemClock.elapsedRealtime(), System.currentTimeMillis())
        loopJob = scope.launch {
            settings = SettingsRepository(AlarmDatabase.getInstance(this@TimerForegroundService).appSettingsDao())
                .observe().first()
            while (isActive) {
                tick()
                delay(1000)
            }
        }
    }

    // startCommandそのものには意味を持たせず、常駐ループ(onCreateで開始済み)に処理を一本化する
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private suspend fun tick() {
        val repo = repository()
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        // 期限が来たRUNNINGをFINISHEDへ遷移して保存する。以降このタイマーはremainingMillisMillisAtAnchor=0で固定される
        val timers = repo.observeAll().first().map { state ->
            if (isDue(state, nowElapsed, nowWall)) {
                val finished = finishTimer(state)
                repo.update(finished)
                finished
            } else {
                state
            }
        }

        val finishedIds = timers.filter { it.runState == TimerRunState.FINISHED }.map { it.id }.toSet()
        (finishedIds - ringingPlayers.keys).forEach { id -> startRingingFor(id) }
        // 削除されたか、何らかの理由でFINISHEDでなくなったタイマーの再生を止める
        (ringingPlayers.keys - finishedIds).toList().forEach { id -> stopRingingFor(id) }

        updateNotification(timers, nowElapsed, nowWall)

        if (timers.none { it.runState == TimerRunState.RUNNING || it.runState == TimerRunState.FINISHED }) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startRingingFor(id: Long) {
        // 設定「タイマーの音」で選んだ音を使う。未設定(null)なら既定のアラーム音にする
        val uri = settings.timerSoundUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: return
        val player = MediaPlayer().apply {
            // マナーモードでも鳴らすため、通知/メディアではなくALARM用途を明示する(docs/SPEC.md「タイマータブ」)
            setAudioAttributes(SoundFadeIn.alarmAudioAttributes())
            isLooping = true
            setVolume(0f, 0f)
            runCatching {
                setDataSource(this@TimerForegroundService, uri)
                prepare()
                setVolume(SoundFadeIn.START_VOLUME, SoundFadeIn.START_VOLUME)
                start()
            }
        }
        ringingPlayers[id] = player
        fadeIn(player)
        if (settings.timerVibration) startVibrationIfNeeded()
    }

    // 設定「徐々に音量を上げる」の秒数(既定1.5秒、起きている人へ知らせるだけなのでアラームより短い)で上げきる。
    // 0秒(なし)なら最初から最大音量にする
    private fun fadeIn(player: MediaPlayer) {
        val duration = SoundFadeIn.durationMillisOrNull(settings.timerFadeInSeconds)
        if (duration == null) {
            player.setVolume(1f, 1f)
            return
        }
        SoundFadeIn.start(scope, player, duration)
    }

    // 鳴動中のタイマーが1つも無い状態からバイブを始める。既に鳴動中のタイマーがあれば重ねて始めない
    private fun startVibrationIfNeeded() {
        if (vibrator != null) return
        val pattern = longArrayOf(0, 1000, 1000)
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createWaveform(pattern, 1))
        vibrator = v
    }

    private fun stopRingingFor(id: Long) {
        ringingPlayers.remove(id)?.let { player ->
            runCatching { player.stop() }
            player.release()
        }
        // 鳴動中のタイマーが無くなったらバイブも止める(複数タイマーが同時に鳴っている間は止めない)
        if (ringingPlayers.isEmpty()) {
            vibrator?.cancel()
            vibrator = null
        }
    }

    private fun updateNotification(timers: List<TimerState>, nowElapsed: Long, nowWall: Long) {
        val style = NotificationCompat.InboxStyle()
        timers.forEach { style.addLine(statusLine(it, nowElapsed, nowWall)) }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.timer_notification_title))
            .setContentText(getString(R.string.timer_notification_summary, timers.size))
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)
            .build()
        ServiceCompat.startForeground(
            this,
            TIMER_FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun statusLine(state: TimerState, nowElapsed: Long, nowWall: Long): String = when (state.runState) {
        TimerRunState.FINISHED -> getString(R.string.timer_notification_line_finished, state.label)
        TimerRunState.PAUSED -> getString(
            R.string.timer_notification_line_paused,
            state.label,
            formatDuration(remainingMillis(state, nowElapsed, nowWall)),
        )
        TimerRunState.RUNNING -> getString(
            R.string.timer_notification_line_running,
            state.label,
            formatDuration(remainingMillis(state, nowElapsed, nowWall)),
        )
    }

    // 通知チャンネルは一度だけ作成すればよい。1秒ごとの更新で毎回鳴らさないようIMPORTANCE_LOWにする
    // (実際の完了音はMediaPlayerが鳴らすため、チャンネル自体の音は不要)
    private fun ensureChannel(): String {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(TIMER_NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                TIMER_NOTIFICATION_CHANNEL_ID,
                getString(R.string.timer_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return TIMER_NOTIFICATION_CHANNEL_ID
    }

    private fun repository(): TimerRepository = TimerRepository(AlarmDatabase.getInstance(this).timerDao())

    override fun onDestroy() {
        super.onDestroy()
        loopJob?.cancel()
        ringingPlayers.values.forEach { player -> runCatching { player.stop() }; player.release() }
        ringingPlayers.clear()
        vibrator?.cancel()
        vibrator = null
        scope.cancel()
    }

    companion object {
        /**
         * 動作中/完了のタイマーが1件でもあるかもしれないタイミングで呼ぶ。サービス自身が不要になったら
         * 自分で止まる設計なので、呼び出し側(ui/timer, TimerTriggerReceiver, TimerRescheduleReceiver)は
         * 「開始・再開・再起動直後・端末を起こした直後」など複数箇所から重複して呼んでも安全。
         */
        fun ensureRunning(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TimerForegroundService::class.java))
        }
    }
}
