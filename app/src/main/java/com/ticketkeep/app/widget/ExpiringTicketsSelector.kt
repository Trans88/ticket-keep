package com.ticketkeep.app.widget

import com.ticketkeep.app.data.model.Ticket

/**
 * 临期小组件选条规则（纯函数，便于单测）：
 * - 仅含有保修截止日期的票证
 * - 最多 [maxExpired] 条「已过期」中到期日最近的（按到期日降序）
 * - 剩余名额填「未过期 / 今天到期」，按到期日升序
 * - 合计不超过 [maxItems]；展示顺序：已过期在前，其后未过期
 */
object ExpiringTicketsSelector {
    const val DEFAULT_MAX_ITEMS = 5
    const val DEFAULT_MAX_EXPIRED = 2

    fun select(
        tickets: List<Ticket>,
        todayEpochDay: Long,
        maxItems: Int = DEFAULT_MAX_ITEMS,
        maxExpired: Int = DEFAULT_MAX_EXPIRED,
    ): List<Ticket> {
        if (maxItems <= 0) return emptyList()
        val withEnd = tickets.filter { it.warrantyEndEpochDay != null }
        val expiredCap = maxExpired.coerceIn(0, maxItems)
        val expired = withEnd
            .filter { it.warrantyEndEpochDay!! < todayEpochDay }
            .sortedByDescending { it.warrantyEndEpochDay }
            .take(expiredCap)
        val remaining = maxItems - expired.size
        val upcoming = withEnd
            .filter { it.warrantyEndEpochDay!! >= todayEpochDay }
            .sortedBy { it.warrantyEndEpochDay }
            .take(remaining)
        return expired + upcoming
    }

    /** 副文案：已过期 / 今天到期 / 还剩 X 天 */
    fun statusLabel(warrantyEndEpochDay: Long, todayEpochDay: Long): String {
        val daysLeft = (warrantyEndEpochDay - todayEpochDay).toInt()
        return when {
            daysLeft < 0 -> "已过期"
            daysLeft == 0 -> "今天到期"
            else -> "还剩 ${daysLeft} 天"
        }
    }

    fun isExpired(warrantyEndEpochDay: Long, todayEpochDay: Long): Boolean =
        warrantyEndEpochDay < todayEpochDay
}
