package com.marutyan.termalarm.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.marutyan.termalarm.alarm.RingingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ウィジェットの定期更新やシステムイベントを受信するブロードキャストレシーバー。
 * 1分ごとのタイマー、鳴動停止、端末再起動、時刻変更、アプリ更新の契機で次回更新を予約しウィジェットを再描画するために用いる。
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val glanceManager = GlanceAppWidgetManager(appContext)
                val glanceIds = glanceManager.getGlanceIds(TermAlarmWidget::class.java)

                if (glanceIds.isEmpty()) {
                    WidgetUpdateScheduler.cancelTick(appContext)
                    return@launch
                }

                when (action) {
                    ACTION_WIDGET_TICK,
                    RingingService.ACTION_RINGING_FINISHED,
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        WidgetUpdateScheduler.scheduleNextTick(appContext)
                        runCatching { TermAlarmWidget().updateAll(appContext) }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
