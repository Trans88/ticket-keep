package com.ticketkeep.app.ui.screens.list

import com.ticketkeep.app.data.model.Ticket
import java.time.LocalDate

/**
 * 保修到期桶：互斥；「无到期」单独成类，未过期不含无到期日票证。
 */
enum class ExpiryBucket {
    ALL,
    NOT_EXPIRED,
    EXPIRED,
    NO_END,
}

/**
 * 时间范围按哪一日字段过滤。
 */
enum class DateRangeField {
    WARRANTY_END,
    PURCHASE,
}

/**
 * 列表筛选条件。保存在 [ListViewModel] 直至用户清除；同进程内离开详情再返回仍保持。
 */
data class TicketListFilterState(
    val expiry: ExpiryBucket = ExpiryBucket.ALL,
    val rangeField: DateRangeField = DateRangeField.WARRANTY_END,
    val rangeStart: LocalDate? = null,
    val rangeEnd: LocalDate? = null,
) {
    val hasActiveConstraints: Boolean
        get() = expiry != ExpiryBucket.ALL || rangeStart != null || rangeEnd != null
}

/**
 * 列表纯过滤：先到期桶，再按购买日/到期日闭区间；与搜索结果叠加时由调用方先搜后滤。
 */
object TicketListFilter {
    fun apply(
        tickets: List<Ticket>,
        filter: TicketListFilterState,
        today: LocalDate = LocalDate.now(),
    ): List<Ticket> {
        val todayEpoch = today.toEpochDay()
        return tickets.filter { ticket ->
            matchesExpiry(ticket, filter.expiry, todayEpoch) &&
                matchesRange(ticket, filter, todayEpoch)
        }
    }

    fun matchesExpiry(ticket: Ticket, bucket: ExpiryBucket, todayEpoch: Long): Boolean {
        val end = ticket.warrantyEndEpochDay
        return when (bucket) {
            ExpiryBucket.ALL -> true
            ExpiryBucket.NO_END -> end == null
            ExpiryBucket.NOT_EXPIRED -> end != null && end >= todayEpoch
            ExpiryBucket.EXPIRED -> end != null && end < todayEpoch
        }
    }

    fun matchesRange(
        ticket: Ticket,
        filter: TicketListFilterState,
        todayEpoch: Long = LocalDate.now().toEpochDay(),
    ): Boolean {
        val start = filter.rangeStart?.toEpochDay()
        val end = filter.rangeEnd?.toEpochDay()
        if (start == null && end == null) return true
        val value = when (filter.rangeField) {
            DateRangeField.WARRANTY_END -> ticket.warrantyEndEpochDay
            DateRangeField.PURCHASE -> ticket.purchaseDateEpochDay
        } ?: return false // 无该日期的票证不进入时间范围结果
        if (start != null && value < start) return false
        if (end != null && value > end) return false
        return true
    }
}
