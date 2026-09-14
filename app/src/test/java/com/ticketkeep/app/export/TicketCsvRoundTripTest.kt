package com.ticketkeep.app.export

import com.ticketkeep.app.data.model.Ticket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSV 导出→导入往返：BOM、字段保留、插入条数（id 重置为 0）。
 */
class TicketCsvRoundTripTest {

    @Test
    fun exportThenImport_preservesMerchantAmountNoteAndCount() {
        val original = listOf(
            Ticket(
                id = 7L,
                merchantName = "往返店",
                amountCents = 9900L,
                purchaseDateEpochDay = 20000L,
                warrantyMonths = 24,
                warrantyEndEpochDay = 20730L,
                note = "含,逗号与\"引号\"",
                imagePath = null,
                ocrRawText = "OCR一行",
                createdAtMillis = 100L,
                updatedAtMillis = 200L,
            ),
            Ticket(
                id = 8L,
                merchantName = "第二店",
                amountCents = 100L,
                note = "B",
            ),
        )
        val csv = TicketCsvExporter.buildCsvString(original)
        assertTrue(csv.isNotEmpty() && csv[0] == '\uFEFF')
        val outcome = TicketCsvImporter.parse(csv)
        assertTrue(outcome is TicketCsvImporter.Outcome.Ready)
        val preview = (outcome as TicketCsvImporter.Outcome.Ready).preview
        assertEquals(2, preview.tickets.size)
        assertEquals(0L, preview.tickets[0].id)
        assertEquals("往返店", preview.tickets[0].merchantName)
        assertEquals(9900L, preview.tickets[0].amountCents)
        assertEquals("含,逗号与\"引号\"", preview.tickets[0].note)
        assertEquals("第二店", preview.tickets[1].merchantName)
    }

    @Test
    fun missingMerchantHeader_errorsInChinese() {
        val outcome = TicketCsvImporter.parse("\uFEFFa,b\n1,2\n")
        assertTrue(outcome is TicketCsvImporter.Outcome.Error)
        assertTrue((outcome as TicketCsvImporter.Outcome.Error).message.contains("商家"))
    }
}
