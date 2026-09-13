package com.ticketkeep.app.export

import com.ticketkeep.app.data.model.Ticket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSV 导出纯逻辑单测：BOM、转义、空列表仅表头。
 */
class TicketCsvExporterTest {

    @Test
    fun emptyList_hasBomAndHeader() {
        val csv = TicketCsvExporter.buildCsvString(emptyList())
        assertTrue(csv.isNotEmpty() && csv[0] == '\uFEFF')
        assertTrue(csv.contains("商家"))
        assertEquals(2, csv.lines().size) // header + trailing empty from final \n? 
    }

    @Test
    fun csvEscape_quotesCommaAndQuotes() {
        assertEquals("hello", TicketCsvExporter.csvEscape("hello"))
        assertEquals("\"a,b\"", TicketCsvExporter.csvEscape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", TicketCsvExporter.csvEscape("say \"hi\""))
    }

    @Test
    fun oneTicket_includesMerchant() {
        val t = Ticket(
            id = 1L,
            merchantName = "测试店",
            amountCents = 12850L,
            purchaseDateEpochDay = null,
            warrantyMonths = null,
            warrantyEndEpochDay = null,
            note = "含,逗号",
            imagePath = null,
            ocrRawText = "",
            createdAtMillis = 0L,
            updatedAtMillis = 0L,
        )
        val csv = TicketCsvExporter.buildCsvString(listOf(t))
        assertTrue(csv.contains("测试店"))
        assertTrue(csv.contains("\"含,逗号\""))
    }
}