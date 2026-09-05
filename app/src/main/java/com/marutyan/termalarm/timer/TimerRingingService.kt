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
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.marutyan.termalarm.alarm.AlarmVibration
import com.marutyan.termalarm.alarm.SoundFadeIn
import com.marutyan.termalarm.data.Repositories
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
            settings = Repositories.settings(this@TimerRingingService)
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
        // 起きている人へ知らせるだけなので、上げきる時間はアラームより短い(既定1.5秒)
        val player = SoundFadeIn.startRinging(this, scope, uri, settings.timerFadeInSeconds) ?: return
        ringingPlayers[id] = player
        if (settings.timerVibration) startVibrationIfNeeded()
    }

    // 鳴動中のタイマーが1つも無い状態からバイブを始める。既に鳴動中のタイマーがあれば重ねて始めない
    private fun startVibrationIfNeeded() {
        if (vibrator != null) return
        vibrator = AlarmVibration.start(this)
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

    private fun repository(): TimerRepository = Repositories.timer(this)

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
