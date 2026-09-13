package com.marutyan.termalarm.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 時計ウィジェットをシステムへ登録するための受け口。
 * ウィジェットの追加・更新・削除はシステムがこの受け口へ知らせるため、マニフェストへ宣言する。
 */
class TermAlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TermAlarmWidget()
}
