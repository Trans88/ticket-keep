package com.ticketkeep.app.ui.screens.batch

import com.ticketkeep.app.data.repository.TicketRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批量配额与防重复插入纯函数单测。
 */
class BatchQuotaLogicTest {

    @Test
    fun remainingSlots_premium_isUnlimited() {
        assertEquals(
            Int.MAX_VALUE,
            BatchQuotaLogic.remainingSlots(hasLocalPremium = true, currentCount = 100),
        )
    }

    @Test
    fun remainingSlots_free_clampsAtZero() {
        assertEquals(
            0,
            BatchQuotaLogic.remainingSlots(
                hasLocalPremium = false,
                currentCount = TicketRepository.FREE_TICKET_LIMIT,
            ),
        )
        assertEquals(
            3,
            BatchQuotaLogic.remainingSlots(hasLocalPremium = false, currentCount = 7),
        )
    }

    @Test
    fun decide_proceedWhenEnoughOrPremium() {
        assertEquals(
            BatchQuotaLogic.SaveGate.Proceed,
            BatchQuotaLogic.decideSaveGate(remaining = Int.MAX_VALUE, selectedUnsavedCount = 5),
        )
        assertEquals(
            BatchQuotaLogic.SaveGate.Proceed,
            BatchQuotaLogic.decideSaveGate(remaining = 5, selectedUnsavedCount = 5),
        )
    }

    @Test
    fun decide_needChooseNeverSilentTruncate() {
        val gate = BatchQuotaLogic.decideSaveGate(remaining = 2, selectedUnsavedCount = 5)
        assertTrue(gate is BatchQuotaLogic.SaveGate.NeedChoose)
        gate as BatchQuotaLogic.SaveGate.NeedChoose
        assertEquals(2, gate.remaining)
        assertEquals(5, gate.selectedCount)
    }

    @Test
    fun decide_needUpgradeWhenZero() {
        assertEquals(
            BatchQuotaLogic.SaveGate.NeedUpgrade,
            BatchQuotaLogic.decideSaveGate(remaining = 0, selectedUnsavedCount = 3),
        )
    }

    @Test
    fun decide_nothingToSave() {
        assertEquals(
            BatchQuotaLogic.SaveGate.NothingToSave,
            BatchQuotaLogic.decideSaveGate(remaining = 5, selectedUnsavedCount = 0),
        )
    }

    @Test
    fun markSaved_preventsDoubleInsert() {
        val draft = BatchDraftItem(selected = true)
        assertTrue(BatchQuotaLogic.canInsert(draft))
        val saved = BatchQuotaLogic.markSaved(draft, ticketId = 42L)
        assertFalse(BatchQuotaLogic.canInsert(saved))
        assertEquals(42L, saved.savedTicketId)
        assertFalse(saved.selected)
    }
}
