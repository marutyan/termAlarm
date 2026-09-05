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
import com.marutyan.termalarm.ui.alarmlist.TermAlarmTab
import com.marutyan.termalarm.ui.navigation.EXTRA_DEEPLINK_TAB
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.extendTimer
import com.marutyan.termalarm.domain.finishTimer
import com.marutyan.termalarm.domain.isDue
import com.marutyan.termalarm.domain.pauseTimer
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

    // 鳴り始めた壁時計の時刻。通知に「タイムアップから何秒経ったか」を出すために覚えておく。
    // 通知は1秒ごとに作り直すので、その都度「今」を入れると経過時間がいつまでも0のままになる
    private val ringingSince = mutableMapOf<Long, Long>()

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
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_TIMER_ID, -1L) ?: -1L
        if (id >= 0) {
            when (intent?.action) {
                ACTION_STOP -> scope.launch { stopTimer(id) }
                ACTION_EXTEND -> scope.launch { mutateTimer(id) { s, now, wall -> extendTimer(s, 60_000L, now, wall) } }
                ACTION_PAUSE -> scope.launch { mutateTimer(id, ::pauseTimer) }
            }
        }
        return START_NOT_STICKY
    }

    // 通知の「停止」。鳴っているタイマーは止めると消える(domain/TimerState.ktの契約)
    private suspend fun stopTimer(id: Long) {
        repository().delete(id)
        TimerScheduler.cancel(this, id)
    }

    // 通知の「+1分」「一時停止」。画面側(TimerViewModel)と同じ手順で、状態を変えて予約を取り直す
    private suspend fun mutateTimer(id: Long, transform: (TimerState, Long, Long) -> TimerState) {
        val state = repository().getById(id) ?: return
        repository().update(transform(state, SystemClock.elapsedRealtime(), System.currentTimeMillis()))
        TimerScheduler.reschedule(this, id)
    }

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
        ringingSince[id] = System.currentTimeMillis()
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
        ringingSince.remove(id)
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

    /**
     * 通知を純正の時計アプリと同じ形で出す。
     * 主役のタイマー1件（鳴っていればそれ、なければ次に鳴るもの）の残り時間を右上へ大きく出し、
     * その場で操作できるボタンを付ける。他のタイマーは下に一覧として並べる。
     *
     * 残り時間は数字を書き込まず、通知の時計機能（Chronometer）へ終わる時刻を渡して数えさせる。
     * こうすると鳴ったあとは純正と同じくマイナス表示になり、経過時間がそのまま続く。
     */
    private fun updateNotification(timers: List<TimerState>, nowElapsed: Long, nowWall: Long) {
        // 鳴っているものを最優先。それ以外は残り時間が短い順に見て、いちばん早く鳴るものを主役にする
        val main = timers.firstOrNull { it.runState == TimerRunState.FINISHED }
            ?: timers.filter { it.runState == TimerRunState.RUNNING }
                .minByOrNull { remainingMillis(it, nowElapsed, nowWall) }
            ?: timers.firstOrNull()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            // 通知を押したらタイマーのタブを開く。一覧が出ると、どのタイマーの話か分からない
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_DEEPLINK_TAB, TermAlarmTab.TIMER.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)

        if (main == null) {
            builder.setContentTitle(getString(R.string.timer_notification_title))
        } else {
            builder.setContentTitle(main.label.ifBlank { getString(R.string.timer_notification_title) })
            builder.setContentText(
                when (main.runState) {
                    TimerRunState.FINISHED -> getString(R.string.timer_notification_finished)
                    TimerRunState.PAUSED -> getString(R.string.timer_notification_paused)
                    TimerRunState.RUNNING -> getString(R.string.timer_notification_running)
                },
            )
            // 鳴っているタイマーは鳴り始めた時刻を基準にする。そうすると経過した分だけマイナスへ伸びていく
            val target = when (main.runState) {
                TimerRunState.FINISHED -> ringingSince[main.id] ?: nowWall
                else -> nowWall + remainingMillis(main, nowElapsed, nowWall)
            }
            if (main.runState != TimerRunState.PAUSED) {
                builder.setWhen(target).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
            }
            builder.addAction(0, getString(R.string.timer_stop), actionIntent(ACTION_STOP, main.id))
            builder.addAction(
                0,
                getString(R.string.timer_extend_one_minute),
                actionIntent(ACTION_EXTEND, main.id),
            )
            if (main.runState == TimerRunState.RUNNING) {
                builder.addAction(0, getString(R.string.timer_pause), actionIntent(ACTION_PAUSE, main.id))
            }
        }

        // 2件以上あるときだけ、主役以外を一覧で見せる
        val others = timers.filter { it.id != main?.id }
        if (others.isNotEmpty()) {
            val style = NotificationCompat.InboxStyle()
            others.forEach { style.addLine(statusLine(it, nowElapsed, nowWall)) }
            builder.setStyle(style)
            builder.setSubText(getString(R.string.timer_notification_summary, timers.size))
        }

        ServiceCompat.startForeground(
            this,
            TIMER_FOREGROUND_NOTIFICATION_ID,
            builder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    // 通知のボタンから自分自身へ操作を送り返すための入れ物
    private fun actionIntent(action: String, id: Long): PendingIntent = PendingIntent.getService(
        this,
        (action + id).hashCode(),
        Intent(this, TimerForegroundService::class.java).setAction(action).putExtra(EXTRA_TIMER_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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
        const val ACTION_STOP = "com.marutyan.termalarm.timer.STOP"
        const val ACTION_EXTEND = "com.marutyan.termalarm.timer.EXTEND"
        const val ACTION_PAUSE = "com.marutyan.termalarm.timer.PAUSE"
        const val EXTRA_TIMER_ID = "timer_id"

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
