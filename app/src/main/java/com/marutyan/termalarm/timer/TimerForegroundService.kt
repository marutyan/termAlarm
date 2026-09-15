package com.marutyan.termalarm.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.IBinder
import android.os.SystemClock
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.marutyan.termalarm.alarm.AlarmVibration
import com.marutyan.termalarm.alarm.SoundFadeIn
import com.marutyan.termalarm.data.Repositories
import com.marutyan.termalarm.data.TimerRepository
import com.marutyan.termalarm.domain.AppSettings
import com.marutyan.termalarm.domain.TimerRunState
import com.marutyan.termalarm.domain.TimerState
import com.marutyan.termalarm.domain.millisUntilNextTimerEvent
import com.marutyan.termalarm.domain.remainingMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * タイマーの数字が進んでいる間だけ生きるフォアグラウンドサービス。
 *
 * 役目は2つある。
 *
 * 1つ目は、秒が変わるたびに通知を出し直すこと。残り時間を端末に数えさせていたときは、
 * 丸め方も秒が切り替わる位置も端末任せになり、画面の数字と1秒ずれて見えていた。
 * いまは画面と同じ計算で文字を作って書き込むため、誰かが出し直さないと数字が止まる。
 * これで画面・通知・ステータスバーのチップが必ず同じ数字になる。
 *
 * 2つ目は、0になったタイマーを鳴らすこと。音はアラームと同じUSAGE_ALARMで鳴らし、
 * マナーモードでも聞こえるようにする（docs/SPEC.md「タイマータブ」）。
 * 予約(TimerScheduler)が遅れて届いた場合の取りこぼしも、ここの見回りで拾う。
 *
 * 一時停止中のタイマーしか無いときは数字が動かないので、このサービスは止まる。
 */
class TimerForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    // 見回りは「秒が変わったとき」と「状態が保存されたとき」の2つから呼ばれる。同時に走らせない
    private val tickMutex = Mutex()

    // 鳴動中のタイマーidごとの再生。複数同時に鳴る場合があるためidで持つ
    private val ringingPlayers = mutableMapOf<Long, MediaPlayer>()

    // 0になる少し前に開いておく音。開くのに時間がかかるため、鳴らす直前に開くと出遅れる
    private var preparedPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var settings: AppSettings = AppSettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tickJob = scope.launch {
            settings = Repositories.settings(this@TimerForegroundService).observe().first()
            // 保存された内容が変わったら、秒を待たずに見回る。
            // 「停止」を押したときに通知を消すのはこの経路。次の秒まで待つと、
            // 止めたはずの通知が1秒近く残って見える
            launch { repository().observeAll().collect { runTick() } }
            while (isActive) {
                val timers = runTick() ?: break
                delay(nextTickDelay(timers))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService()からは数秒以内にstartForeground()を呼ぶ必要がある。
        // 中身を作るにはDBを読むため、待っていると間に合わないことがある。
        // まず中身の無い通知で立ってから、読み終えた内容で出し直す
        ServiceCompat.startForeground(
            this,
            TIMER_FOREGROUND_NOTIFICATION_ID,
            TimerNotifications.buildStarting(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        scope.launch { runTick() }
        return START_NOT_STICKY
    }

    // 次の見回りまで待つ時間。数え方はdomainのmillisUntilNextTimerEventにある。
    // 少し過ぎてから起きないと、同じ瞬間のまま起きて空振りする
    private fun nextTickDelay(timers: List<TimerState>): Long =
        millisUntilNextTimerEvent(
            timers = timers,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            nowWallClockMillis = System.currentTimeMillis(),
            displayLeadMillis = TimerNotifications.DISPLAY_LEAD_MILLIS,
        ) + TICK_OVERSHOOT_MILLIS

    /** 見回りを1つずつ順番に行う。秒の更新と保存の通知が重なっても、二重に走らせない */
    private suspend fun runTick(): List<TimerState>? = tickMutex.withLock { tickOnce() }

    /**
     * 見回りの1周。通知を出し直し、鳴らすものを鳴らす。
     * 数字が進むタイマーが無くなったら自分を止め、nullを返す。
     */
    private suspend fun tickOnce(): List<TimerState>? {
        // 予約が遅れて届いても、ここで0を過ぎたものを鳴動中へ移す
        TimerActions.markDueTimersFinished(this)
        val timers = repository().observeAll().first()
        val isTicking = timers.any {
            it.runState == TimerRunState.RUNNING || it.runState == TimerRunState.FINISHED
        }
        if (!isTicking) {
            stopRingingAll()
            // フォアグラウンドの通知は、サービスが手放すまで消せない。
            // 切り離すだけでは消えなかったので、いったん確実に消してから、
            // 残すべきもの（途中で一時停止しているタイマー）があれば出し直す
            stopForeground(STOP_FOREGROUND_REMOVE)
            TimerNotifications.refresh(this, timers)
            stopSelf()
            return null
        }
        syncRinging(timers)
        prepareSoundIfSoon(timers)
        promote(TimerNotifications.activeTimers(timers).ifEmpty { timers })
        return timers
    }

    /**
     * いまの一覧で通知を出し、フォアグラウンドとして立ち続ける。
     * 音を鳴らしている間だけメディア再生として名乗り、それ以外はタイマー用途として名乗る。
     */
    private fun promote(timers: List<TimerState>) {
        val type = if (ringingPlayers.isEmpty()) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
        ServiceCompat.startForeground(
            this,
            TIMER_FOREGROUND_NOTIFICATION_ID,
            TimerNotifications.build(this, timers),
            type,
        )
    }

    // 鳴っているタイマーの集合と、いま再生中の集合を突き合わせる
    private fun syncRinging(timers: List<TimerState>) {
        val finishedIds = timers.filter { it.runState == TimerRunState.FINISHED }
            .map { it.id }
            .toSet()
        (finishedIds - ringingPlayers.keys).forEach { startRingingFor(it) }
        (ringingPlayers.keys - finishedIds).toList().forEach { stopRingingFor(it) }
    }

    /**
     * 0になる少し前に音源を開いておく。
     *
     * 音源を開く処理は読み込みを伴い、数百ミリ秒かかることがある。
     * 0になってから開くと、そのぶん鳴り始めが遅れて、表示とずれて聞こえる。
     */
    private fun prepareSoundIfSoon(timers: List<TimerState>) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val soonest = timers
            .filter { it.runState == TimerRunState.RUNNING }
            .minOfOrNull { remainingMillis(it, nowElapsed, nowWall) }
        if (soonest == null || soonest > PREPARE_SOUND_BEFORE_MILLIS) {
            // もうすぐ鳴るものが無くなったら手放す。一時停止や停止で予定が消えることがある
            releasePreparedPlayer()
            return
        }
        if (preparedPlayer != null) return
        preparedPlayer = SoundFadeIn.prepareRinging(this, settings.alarmSoundUri)
    }

    private fun releasePreparedPlayer() {
        preparedPlayer?.let { runCatching { it.release() } }
        preparedPlayer = null
    }

    private fun startRingingFor(id: Long) {
        // 開いてあるものがあればそれを使う。無ければここで開く
        val player = preparedPlayer?.also { preparedPlayer = null }
            ?: SoundFadeIn.prepareRinging(this, settings.alarmSoundUri)
            ?: return
        // タイマーは0になった瞬間に鳴らす。「徐々に音量を上げる」はアラームの設定で、
        // これをタイマーへ効かせると、鳴っているのに数秒間ほとんど聞こえず、
        // 「マイナス数秒で鳴り始めた」ように感じられる
        SoundFadeIn.beginRinging(scope, player, fadeInSeconds = 0)
        ringingPlayers[id] = player
        if (settings.vibration) startVibrationIfNeeded()
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

    private fun stopRingingAll() {
        ringingPlayers.keys.toList().forEach { stopRingingFor(it) }
    }

    private fun repository(): TimerRepository = Repositories.timer(this)

    override fun onDestroy() {
        super.onDestroy()
        tickJob?.cancel()
        releasePreparedPlayer()
        ringingPlayers.values.forEach { player -> runCatching { player.stop() }; player.release() }
        ringingPlayers.clear()
        vibrator?.cancel()
        vibrator = null
        scope.cancel()
    }

    companion object {
        // 次の秒へ変わる瞬間より、このぶんだけ後に起きる。ちょうどに起きると同じ秒のままになる
        private const val TICK_OVERSHOOT_MILLIS = 20L

        // 鳴る何ミリ秒前から音源を開いておくか。開くのに数百ミリ秒かかることがある
        private const val PREPARE_SOUND_BEFORE_MILLIS = 3_000L

        /** 数字が進むタイマーがあるときに呼ぶ。既に動いていれば何も起きない */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TimerForegroundService::class.java))
        }
    }
}
