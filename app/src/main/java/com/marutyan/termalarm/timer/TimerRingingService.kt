package com.marutyan.termalarm.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.marutyan.termalarm.alarm.SoundFadeIn
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.SettingsRepository
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.TimerRunState
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
 * タイマーが鳴っている間だけ動くサービス。音とバイブを担当する。
 *
 * 純正の時計アプリも、動作中はサービスを持たず、鳴ったときだけサービスを起こしていた。
 * 動作中の残り時間は通知（MetricStyle）が数えるため、こちらが1秒ごとに起きる必要がない。
 * 常駐をやめたことで電池を使わなくなり、通知も「進行中の重要な通知」として扱われる。
 *
 * 音はアラームと同じUSAGE_ALARMで鳴らし、マナーモードでも聞こえるようにする
 * （docs/SPEC.md「タイマータブ」）。フェードインの秒数はアラームより短い設定既定値を使う。
 */
class TimerRingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null

    // 鳴動中のタイマーidごとの再生。複数同時に鳴る場合があるためidで持つ
    private val ringingPlayers = mutableMapOf<Long, MediaPlayer>()
    private var vibrator: Vibrator? = null
    private var settings: AppSettings = AppSettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // startForegroundService()からは数秒以内にstartForeground()を呼ぶ必要がある。
        // 中身が確定する前に、まず今の一覧で通知を出す
        startForegroundWithCurrentTimers()
        watchJob = scope.launch {
            settings = SettingsRepository(AlarmDatabase.getInstance(this@TimerRingingService).appSettingsDao())
                .observe().first()
            // 鳴っているタイマーが増減したら音を合わせる。鳴っているものが無くなったら自分を止める
            while (isActive) {
                syncRinging()
                delay(500)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithCurrentTimers()
        return START_NOT_STICKY
    }

    // いまの一覧で通知を出しつつ、フォアグラウンドとして立つ
    private fun startForegroundWithCurrentTimers() {
        scope.launch {
            val timers = repository().observeAll().first()
            if (timers.isEmpty()) {
                stopSelf()
                return@launch
            }
            ServiceCompat.startForeground(
                this@TimerRingingService,
                TIMER_FOREGROUND_NOTIFICATION_ID,
                TimerNotifications.build(this@TimerRingingService, timers),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        }
    }

    // 鳴っているタイマーの集合と、いま再生中の集合を突き合わせる
    private suspend fun syncRinging() {
        val finishedIds = repository().observeAll().first()
            .filter { it.runState == TimerRunState.FINISHED }
            .map { it.id }
            .toSet()
        (finishedIds - ringingPlayers.keys).forEach { startRingingFor(it) }
        (ringingPlayers.keys - finishedIds).toList().forEach { stopRingingFor(it) }
        if (finishedIds.isEmpty()) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun startRingingFor(id: Long) {
        // 設定「タイマーの音」で選んだ音を使う。未設定(null)なら既定のアラーム音にする
        val uri = settings.timerSoundUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: return
        val player = MediaPlayer().apply {
            // マナーモードでも鳴らすため、通知/メディアではなくALARM用途を明示する
            setAudioAttributes(SoundFadeIn.alarmAudioAttributes())
            isLooping = true
            setVolume(0f, 0f)
            runCatching {
                setDataSource(this@TimerRingingService, uri)
                prepare()
                setVolume(SoundFadeIn.START_VOLUME, SoundFadeIn.START_VOLUME)
                start()
            }
        }
        ringingPlayers[id] = player
        fadeIn(player)
        if (settings.timerVibration) startVibrationIfNeeded()
    }

    // 設定「徐々に音量を上げる」の秒数(既定1.5秒、起きている人へ知らせるだけなのでアラームより短い)で
    // 上げきる。0秒(なし)なら最初から最大音量にする
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

    private fun repository(): TimerRepository = TimerRepository(AlarmDatabase.getInstance(this).timerDao())

    override fun onDestroy() {
        super.onDestroy()
        watchJob?.cancel()
        ringingPlayers.values.forEach { player -> runCatching { player.stop() }; player.release() }
        ringingPlayers.clear()
        vibrator?.cancel()
        vibrator = null
        scope.cancel()
    }

    companion object {
        /** 鳴っているタイマーがあるときに呼ぶ。既に鳴っていれば何も起きない */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TimerRingingService::class.java))
        }

        /** 鳴っているタイマーが無くなったときに呼ぶ */
        fun stop(context: Context) {
            context.stopService(Intent(context, TimerRingingService::class.java))
        }
    }
}
