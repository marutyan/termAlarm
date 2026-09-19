package com.marutyan.termalarm.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 時計ウィジェットをシステムへ登録するための受け口。
 * ウィジェットの追加・更新・削除はシステムがこの受け口へ知らせるため、マニフェストへ宣言する。
 */
class TermAlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TermAlarmWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // onEnabled では親クラスは goAsync() を使わないが、onUpdate と同じく自前の寿命を持つ WidgetUpdateReceiver へ委譲し、
        // 予約が終わる前にプロセスが落ちて月日が更新されなくなる経路を作らない。
        context.sendBroadcast(
            Intent(context, WidgetUpdateReceiver::class.java).setAction(ACTION_WIDGET_REFRESH)
        )
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateScheduler.cancelRefresh(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // GlanceAppWidgetReceiver 内部で既に goAsync() が呼ばれて消費されており、1受信につき1度しか呼べないためここでは null になる。
        // そのため自前の寿命で非同期処理を行う WidgetUpdateReceiver へ明示 Intent を送り、再描画と次回予約を委譲する。
        context.sendBroadcast(
            Intent(context, WidgetUpdateReceiver::class.java).setAction(ACTION_WIDGET_REFRESH)
        )
    }
}
