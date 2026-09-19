package com.ticketkeep.app.widget

import android.content.Context
import android.content.Intent
import com.ticketkeep.app.MainActivity
import com.ticketkeep.app.notification.WarrantyReminderWorker

/**
 * 小组件点击 Intent：打开列表或详情（复用通知深链 EXTRA_TICKET_ID）。
 */
object WidgetIntents {
    /** 打开列表并预选「未过期」筛选（MainActivity / ListScreen 识别） */
    const val EXTRA_EXPIRY_FILTER = "widget_expiry_filter"
    const val FILTER_NOT_EXPIRED = "NOT_EXPIRED"

    fun openList(context: Context, applyNotExpiredFilter: Boolean = true): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (applyNotExpiredFilter) {
                putExtra(EXTRA_EXPIRY_FILTER, FILTER_NOT_EXPIRED)
            }
        }

    fun openDetail(context: Context, ticketId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(WarrantyReminderWorker.EXTRA_TICKET_ID, ticketId)
        }
}
