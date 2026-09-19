package com.ticketkeep.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 临期小组件 BroadcastReceiver：系统与 [WidgetRefresher] 触发更新。
 * 须在 AndroidManifest 中注册并关联 appwidget-provider。
 */
class ExpiringTicketsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpiringTicketsWidget()
}
