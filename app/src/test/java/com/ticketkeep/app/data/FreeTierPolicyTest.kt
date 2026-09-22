package com.ticketkeep.app.data

import com.ticketkeep.app.data.repository.TicketRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 免费额度门禁单测：非本地高级 10 条边界；本地高级不限条数。
 * 云订阅不在此门禁内。
 */
class FreeTierPolicyTest {

    @Test
    fun freeLimit_isTen() {
        assertEquals(10, TicketRepository.FREE_TICKET_LIMIT)
    }

    @Test
    fun nonLocal_nineExist_canAddTenth() {
        assertTrue(TicketRepository.canAdd(hasLocalPremium = false, count = 9))
    }

    @Test
    fun nonLocal_tenExist_cannotAddEleventh() {
        assertFalse(TicketRepository.canAdd(hasLocalPremium = false, count = 10))
    }

    @Test
    fun nonLocal_zero_canAdd() {
        assertTrue(TicketRepository.canAdd(hasLocalPremium = false, count = 0))
    }

    @Test
    fun localPremium_alwaysCanAdd() {
        assertTrue(TicketRepository.canAdd(hasLocalPremium = true, count = 100))
        assertTrue(TicketRepository.canAdd(hasLocalPremium = true, count = 10))
    }

    @Test
    fun localPremium_hundredExist_canStillAdd() {
        assertTrue(TicketRepository.canAdd(hasLocalPremium = true, count = 100))
    }

    @Test
    fun nonLocal_atLimit_blocksLikeExportImportGate() {
        assertFalse(
            TicketRepository.canAdd(
                hasLocalPremium = false,
                count = TicketRepository.FREE_TICKET_LIMIT,
            ),
        )
        assertTrue(
            TicketRepository.canAdd(
                hasLocalPremium = true,
                count = TicketRepository.FREE_TICKET_LIMIT,
            ),
        )
    }

    /** 兼容旧位置参数调用。 */
    @Test
    fun positionalArgs_stillWork() {
        assertTrue(TicketRepository.canAdd(true, 100))
        assertFalse(TicketRepository.canAdd(false, 10))
    }
}
