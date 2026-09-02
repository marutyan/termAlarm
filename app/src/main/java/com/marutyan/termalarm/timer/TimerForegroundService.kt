package com.marutyan.termalarm.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.TimerRepository
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
 * 「アラームと同じUSAGE_ALARMを使い、マナーモードでも鳴らす」)。ただしRingingServiceはalarm/配下で
 * 書き込み範囲外のため、共通化のための抽象化層を新設せず、この程度の量(AudioAttributes設定のみ)は
 * timer側に独立して持つ。フェードイン(鳴り始めに音量を徐々に上げる)は既存のRingingServiceには無い
 * 演出だが、鳴った瞬間の驚きを抑えるため独自に追加した。
 */
class TimerForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    // 鳴動中(FINISHED)のタイマーidごとのMediaPlayer。複数のタイマーが同時に完了しても個別に鳴らし続けられる
    private val ringingPlayers = mutableMapOf<Long, MediaPlayer>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // startForegroundService()からは数秒以内にstartForeground()を呼ぶ必要があるため、
        // 内容が確定する最初のtick()を待たずここで一旦空の通知を出す
        updateNotification(emptyList(), SystemClock.elapsedRealtime(), System.currentTimeMillis())
        loopJob = scope.launch {
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
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM) ?: return
        val player = MediaPlayer().apply {
            // マナーモードでも鳴らすため、通知/メディアではなくALARM用途を明示する(docs/SPEC.md「タイマータブ」)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            isLooping = true
            setVolume(0f, 0f)
            runCatching {
                setDataSource(this@TimerForegroundService, uri)
                prepare()
                start()
            }
        }
        ringingPlayers[id] = player
        fadeIn(player)
    }

    // 鳴り始めの0.1秒刻み×15回(計1.5秒)で音量を0→1へ上げる
    private fun fadeIn(player: MediaPlayer) {
        scope.launch {
            val steps = 15
            repeat(steps) { i ->
                val volume = (i + 1) / steps.toFloat()
                runCatching { player.setVolume(volume, volume) }
                delay(100)
            }
        }
    }

    private fun stopRingingFor(id: Long) {
        ringingPlayers.remove(id)?.let { player ->
            runCatching { player.stop() }
            player.release()
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
