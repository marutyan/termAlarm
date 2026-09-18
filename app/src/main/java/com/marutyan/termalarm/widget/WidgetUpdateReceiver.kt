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
 * ウィジェットの再描画やシステムイベントを受信するブロードキャストレシーバー。
 * 日付切り替わりタイマー（0:00）、鳴動停止、端末再起動、時刻変更、アプリ更新の契機で次回更新を予約しウィジェットを再描画するために用いる。
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
                    WidgetUpdateScheduler.cancelRefresh(appContext)
                    return@launch
                }

                when (action) {
                    ACTION_WIDGET_REFRESH,
                    // 以前の版の旧Action。取り消し処理の前に発火した1回分でも月日が古いまま残らないよう、新Actionと同様に描き直して次回を予約する
                    ACTION_WIDGET_TICK,
                    RingingService.ACTION_RINGING_FINISHED,
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        WidgetUpdateScheduler.scheduleNextRefresh(appContext)
                        runCatching { TermAlarmWidget().updateAll(appContext) }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
