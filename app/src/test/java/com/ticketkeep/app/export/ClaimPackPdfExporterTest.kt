package com.ticketkeep.app.export

import com.ticketkeep.app.data.model.Ticket
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 送修材料包纯逻辑单测：摘要字段与文件名拼装。
 */
class ClaimPackPdfExporterTest {

    @Test
    fun buildSummaryRows_includesCoreFields() {
        val t = Ticket(
            merchantName = "示例店",
            amountCents = 12800L,
            purchaseDateEpochDay = 19000L,
            warrantyEndEpochDay = 19365L,
            note = "黑屏",
        )
        val rows = ClaimPackPdfExporter.buildSummaryRows(t)
        assertEquals(5, rows.size)
        assertEquals("商家", rows[0].first)
        assertTrue(rows[0].second.contains("示例店"))
        assertTrue(rows.any { it.first == "备注" && it.second.contains("黑屏") })
    }

    @Test
    fun buildFileName_sanitizesMerchant() {
        val t = Ticket(merchantName = "A/B:店")
        val name = ClaimPackPdfExporter.buildFileName(t, LocalDate.of(2026, 9, 14))
        assertTrue(name.startsWith("票证记_送修材料包_"))
        assertTrue(name.endsWith("_20260914.pdf"))
        assertTrue(!name.contains("/"))
        assertTrue(!name.contains(":"))
    }

    @Test
    fun disclaimer_isChineseAndNonLegalPromise() {
        assertTrue(ClaimPackPdfExporter.DISCLAIMER.contains("不构成理赔承诺"))
    }

    @Test
    fun blankMerchant_fileNameUsesPlaceholder() {
        val name = ClaimPackPdfExporter.buildFileName(Ticket(merchantName = "  "), LocalDate.of(2026, 1, 2))
        assertTrue(name.contains("未命名"))
        assertTrue(name.endsWith("_20260102.pdf"))
    }

    @Test
    fun blankNote_summaryShowsDash() {
        val rows = ClaimPackPdfExporter.buildSummaryRows(Ticket(merchantName = "X", note = ""))
        val note = rows.first { it.first == "备注" }.second
        assertEquals("—", note)
    }
}
