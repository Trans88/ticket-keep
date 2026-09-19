package com.ticketkeep.app.widget

import com.ticketkeep.app.data.model.Ticket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 临期小组件选条规则单测。
 */
class ExpiringTicketsSelectorTest {

    private val today = 20_000L

    private fun t(id: Long, end: Long?) = Ticket(
        id = id,
        merchantName = "m$id",
        warrantyEndEpochDay = end,
    )

    @Test
    fun skipsTicketsWithoutWarrantyEnd() {
        val out = ExpiringTicketsSelector.select(
            listOf(t(1, null), t(2, today + 3)),
            todayEpochDay = today,
        )
        assertEquals(listOf(2L), out.map { it.id })
    }

    @Test
    fun expiredFirstThenUpcomingByAsc() {
        val out = ExpiringTicketsSelector.select(
            listOf(
                t(1, today + 10),
                t(2, today - 1),
                t(3, today + 1),
                t(4, today - 5),
            ),
            todayEpochDay = today,
            maxItems = 5,
            maxExpired = 2,
        )
        // 最近过期优先：-1 再 -5；其后 +1、+10
        assertEquals(listOf(2L, 4L, 3L, 1L), out.map { it.id })
    }

    @Test
    fun capsExpiredAndTotal() {
        val out = ExpiringTicketsSelector.select(
            listOf(
                t(1, today - 1),
                t(2, today - 2),
                t(3, today - 3),
                t(4, today + 1),
                t(5, today + 2),
                t(6, today + 3),
            ),
            todayEpochDay = today,
            maxItems = 4,
            maxExpired = 2,
        )
        assertEquals(4, out.size)
        assertEquals(listOf(1L, 2L), out.take(2).map { it.id })
        assertEquals(listOf(4L, 5L), out.drop(2).map { it.id })
    }

    @Test
    fun statusLabels() {
        assertEquals("已过期", ExpiringTicketsSelector.statusLabel(today - 1, today))
        assertEquals("今天到期", ExpiringTicketsSelector.statusLabel(today, today))
        assertEquals("还剩 3 天", ExpiringTicketsSelector.statusLabel(today + 3, today))
        assertTrue(ExpiringTicketsSelector.isExpired(today - 1, today))
    }
}
