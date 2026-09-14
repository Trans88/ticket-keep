package com.ticketkeep.app.ui.screens.list

import com.ticketkeep.app.data.model.Ticket
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列表筛选纯逻辑：到期桶、时间范围、与无日期票证。
 */
class TicketListFilterTest {

    private val today = LocalDate.of(2026, 9, 14)
    private val todayEp = today.toEpochDay()

    private fun t(
        id: Long,
        end: Long? = null,
        purchase: Long? = null,
    ) = Ticket(id = id, merchantName = "m$id", warrantyEndEpochDay = end, purchaseDateEpochDay = purchase)

    @Test
    fun notExpired_excludesNoEndAndPast() {
        val list = listOf(
            t(1, end = todayEp),
            t(2, end = todayEp + 10),
            t(3, end = todayEp - 1),
            t(4, end = null),
        )
        val out = TicketListFilter.apply(list, TicketListFilterState(expiry = ExpiryBucket.NOT_EXPIRED), today)
        assertEquals(listOf(1L, 2L), out.map { it.id })
    }

    @Test
    fun expired_onlyPast() {
        val list = listOf(t(1, end = todayEp - 1), t(2, end = todayEp), t(3, end = null))
        val out = TicketListFilter.apply(list, TicketListFilterState(expiry = ExpiryBucket.EXPIRED), today)
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun noEnd_onlyNullWarranty() {
        val list = listOf(t(1, end = null), t(2, end = todayEp))
        val out = TicketListFilter.apply(list, TicketListFilterState(expiry = ExpiryBucket.NO_END), today)
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun rangeByWarrantyEnd_inclusive() {
        val start = today.minusDays(5)
        val end = today.plusDays(5)
        val list = listOf(
            t(1, end = todayEp),
            t(2, end = todayEp - 10),
            t(3, end = todayEp + 10),
            t(4, end = null),
        )
        val filter = TicketListFilterState(
            rangeField = DateRangeField.WARRANTY_END,
            rangeStart = start,
            rangeEnd = end,
        )
        val out = TicketListFilter.apply(list, filter, today)
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun rangeByPurchase_skipsMissingPurchase() {
        val filter = TicketListFilterState(
            rangeField = DateRangeField.PURCHASE,
            rangeStart = today.minusDays(1),
            rangeEnd = today.plusDays(1),
        )
        val list = listOf(
            t(1, purchase = todayEp),
            t(2, purchase = null),
        )
        val out = TicketListFilter.apply(list, filter, today)
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun expiryAndRange_combine() {
        val filter = TicketListFilterState(
            expiry = ExpiryBucket.NOT_EXPIRED,
            rangeField = DateRangeField.WARRANTY_END,
            rangeStart = today,
            rangeEnd = today.plusDays(3),
        )
        val list = listOf(
            t(1, end = todayEp + 1),
            t(2, end = todayEp + 10),
            t(3, end = todayEp - 1),
        )
        val out = TicketListFilter.apply(list, filter, today)
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun hasActiveConstraints() {
        assertFalse(TicketListFilterState().hasActiveConstraints)
        assertTrue(TicketListFilterState(expiry = ExpiryBucket.EXPIRED).hasActiveConstraints)
        assertTrue(TicketListFilterState(rangeStart = today).hasActiveConstraints)
    }
}
