package com.marutyan.termalarm.stopwatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.marutyan.termalarm.MainActivity
import com.marutyan.termalarm.R
import com.marutyan.termalarm.data.AlarmDatabase
import com.marutyan.termalarm.data.StopwatchRepository
import com.marutyan.termalarm.domain.StopwatchRunState
import com.marutyan.termalarm.domain.elapsedMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 動作中(RUNNING)の間だけ生存するフォアグラウンドサービス。1秒ごとにRepositoryを読み直して
 * 通知の経過時間を更新し、RUNNINGでなくなったら(一時停止・リセット)自分を止める。
 * 画面を閉じても計測を続ける要件(docs/SPEC.md「ストップウォッチタブ」)のうち、
 * 「通知に経過時間を出す」部分をこのループが担う。計測自体はDBに保存されたanchor値から
 * いつでも再計算できるため、一時停止中はサービスを止めても経過時間の正しさに影響しない。
 *
 * 通知は秒単位の更新に留める(1/100秒までは出さない)。画面表示と違って常時見えるものではなく、
 * 1秒ごとの更新で実用上十分な一方、更新頻度を上げても電池を消費するだけでほぼ意味が無いため
 * (詳細はui/stopwatch/StopwatchScreen.ktのコメントを参照)。
 *
 * media再生や位置情報など既存のforegroundServiceTypeのどれにも当てはまらないため、
 * Android 14+のポリシーに従いspecialUseとして宣言する(AndroidManifest.xml)。
 */
class StopwatchForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // startForegroundService()からは数秒以内にstartForeground()を呼ぶ必要があるため、
        // 内容が確定する最初のtick()を待たずここで一旦通知を出す
        updateNotification(0L)
        loopJob = scope.launch {
            while (isActive) {
                if (!tick()) break
                delay(1000)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // startCommandそのものには意味を持たせず、常駐ループ(onCreateで開始済み)に処理を一本化する
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    // RUNNINGでなくなっていたらfalseを返し、ループ側でサービスを止めさせる
    private suspend fun tick(): Boolean {
        val state = repository().getStateOnce()
        if (state.runState != StopwatchRunState.RUNNING) return false
        val elapsed = elapsedMillis(state, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        updateNotification(elapsed)
        return true
    }

    private fun updateNotification(elapsedMillis: Long) {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.stopwatch_notification_title))
            .setContentText(getString(R.string.stopwatch_notification_text, formatElapsed(elapsedMillis, includeCentiseconds = false)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(contentIntent)
            .build()
        ServiceCompat.startForeground(
            this,
            STOPWATCH_FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    // 通知チャンネルは一度だけ作成すればよい。1秒ごとの更新で毎回鳴らさないようIMPORTANCE_LOWにする
    private fun ensureChannel(): String {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(STOPWATCH_NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                STOPWATCH_NOTIFICATION_CHANNEL_ID,
                getString(R.string.stopwatch_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return STOPWATCH_NOTIFICATION_CHANNEL_ID
    }

    private fun repository(): StopwatchRepository = StopwatchRepository(AlarmDatabase.getInstance(this).stopwatchDao())

    override fun onDestroy() {
        super.onDestroy()
        loopJob?.cancel()
        scope.cancel()
    }

    companion object {
        /**
         * RUNNINGになったかもしれないタイミングで呼ぶ。サービス自身が不要になったら自分で止まる設計なので、
         * 呼び出し側(ui/stopwatch, StopwatchRescheduleReceiver)は「開始・再開・再起動直後」など
         * 複数箇所から重複して呼んでも安全(timer機能のTimerForegroundService.ensureRunningと同じ考え方)。
         */
        fun ensureRunning(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, StopwatchForegroundService::class.java))
        }
    }
}
