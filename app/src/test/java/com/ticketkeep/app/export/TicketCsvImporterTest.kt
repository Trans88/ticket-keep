package com.ticketkeep.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSV 导入解析单测：BOM、表头、样例行与坏表头。
 */
class TicketCsvImporterTest {

    @Test
    fun parse_withBomAndExporterHeaders_insertsTickets() {
        val csv = TicketCsvExporter.buildCsvString(
            listOf(
                com.ticketkeep.app.data.model.Ticket(
                    id = 9L,
                    merchantName = "导入店",
                    amountCents = 1990L,
                    purchaseDateEpochDay = 19000L,
                    warrantyMonths = 12,
                    warrantyEndEpochDay = 19365L,
                    note = "含,逗号",
                    imagePath = "/not/exists.jpg",
                    ocrRawText = "原文",
                    createdAtMillis = 1L,
                    updatedAtMillis = 2L,
                ),
            ),
        )
        val outcome = TicketCsvImporter.parse(csv)
        assertTrue(outcome is TicketCsvImporter.Outcome.Ready)
        val preview = (outcome as TicketCsvImporter.Outcome.Ready).preview
        assertEquals(1, preview.tickets.size)
        val t = preview.tickets.first()
        assertEquals(0L, t.id)
        assertEquals("导入店", t.merchantName)
        assertEquals(1990L, t.amountCents)
        assertEquals("含,逗号", t.note)
        val sanitized = TicketCsvImporter.sanitizeImagePaths(preview.tickets)
        assertEquals(null, sanitized.first().imagePath)
    }

    @Test
    fun badHeader_returnsChineseError() {
        val csv = "\uFEFFfoo,bar\n1,2\n"
        val outcome = TicketCsvImporter.parse(csv)
        assertTrue(outcome is TicketCsvImporter.Outcome.Error)
        val msg = (outcome as TicketCsvImporter.Outcome.Error).message
        assertTrue(msg.contains("商家"))
    }

    @Test
    fun emptyMerchantRow_skipped() {
        val header = listOf(
            "id", "商家", "金额_分", "金额_可读", "购买日_epochDay", "购买日_可读",
            "保修月数", "保修到期_epochDay", "保修到期_可读", "备注", "图片路径",
            "识别原文", "创建时间_millis", "创建时间_可读", "更新时间_millis", "更新时间_可读",
        ).joinToString(",")
        val csv = "\uFEFF$header\n1,,100,,,,,\n2,好店,200,,,,,,,,,,,,,\n"
        val outcome = TicketCsvImporter.parse(csv)
        assertTrue(outcome is TicketCsvImporter.Outcome.Ready)
        val preview = (outcome as TicketCsvImporter.Outcome.Ready).preview
        assertEquals(1, preview.tickets.size)
        assertEquals("好店", preview.tickets.first().merchantName)
        assertTrue(preview.skippedRows >= 1)
    }
}
