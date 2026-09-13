package com.ticketkeep.app.data

import com.ticketkeep.app.data.repository.TicketRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 免费额度门禁单测：非 Pro 10 条边界；Pro 不限条数。
 */
class FreeTierPolicyTest {

    @Test
    fun freeLimit_isTen() {
        assertEquals(10, TicketRepository.FREE_TICKET_LIMIT)
    }

    @Test
    fun nonPro_nineExist_canAddTenth() {
        assertTrue(TicketRepository.canAdd(isPro = false, count = 9))
    }

    @Test
    fun nonPro_tenExist_cannotAddEleventh() {
        assertFalse(TicketRepository.canAdd(isPro = false, count = 10))
    }

    @Test
    fun nonPro_zero_canAdd() {
        assertTrue(TicketRepository.canAdd(isPro = false, count = 0))
    }

    @Test
    fun pro_alwaysCanAdd() {
        assertTrue(TicketRepository.canAdd(isPro = true, count = 100))
        assertTrue(TicketRepository.canAdd(isPro = true, count = 10))
    }
}