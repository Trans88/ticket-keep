package com.ticketkeep.app.ocr

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TicketOcrParser] 单元测试：小票金额/日期、保修单同义词与双列对齐。
 */
class TicketOcrParserTest {

    private val parser = TicketOcrParser()

    /** 模拟双列 OCR：先全部标签，再全部值 */
    private val warrantyFormColumnOcr = """
        领克中心
        保修单号
        客户姓名
        联系电话
        产品名称
        产品型号
        产品序列号
        购买日期
        报修日期
        保修期限
        保修截止日期
        故障描述
        检测结果
        处理方式
        维修费用
        维修状态
        服务网点
        技术人员
        WX20250821-473926
        林志杰
        0987-***-315
        智能车载中控主机
        IVI-X7 Plus
        SN25X7P08214631
        2025-03-12
        2025-08-21
        3年
        2028-03-11
        中控屏偶发卡顿，蓝牙连接后无声音
        系统服务异常，音频模块配置丢失
        升级系统版本并重新初始化音频配置
        ¥0（保修期内）
        已完成
        示例汽车科技售后服务中心
        李工
    """.trimIndent()

    @Test
    fun warrantyForm_columnLayout_parsesPurchaseAndWarranty() {
        val r = parser.parse(warrantyFormColumnOcr)
        assertTrue(r.isWarrantyForm)
        assertEquals(LocalDate.of(2025, 3, 12).toEpochDay(), r.purchaseDateEpochDay)
        assertEquals(LocalDate.of(2028, 3, 11).toEpochDay(), r.warrantyEndEpochDay)
        assertEquals(36, r.warrantyMonths)
        assertTrue(
            r.note?.contains("故障") == true ||
                r.note?.contains("异常") == true ||
                r.note?.contains("卡顿") == true ||
                r.note?.contains("蓝牙") == true,
        )
    }

    @Test
    fun warrantyForm_sameLineLabels_stillWork() {
        val text = """
            购买日期：2025-03-12
            报修日期：2025-08-21
            保修期限：3年
            保修截止日期：2028-03-11
            故障描述：中控屏偶发卡顿
            服务网点：领克中心
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(LocalDate.of(2025, 3, 12).toEpochDay(), r.purchaseDateEpochDay)
        assertEquals(LocalDate.of(2028, 3, 11).toEpochDay(), r.warrantyEndEpochDay)
        assertEquals(36, r.warrantyMonths)
        assertEquals("领克中心", r.merchantName)
        assertTrue(r.note?.contains("卡顿") == true)
    }

    @Test
    fun receipt_parsesAmountAndDoesNotForceWarrantyForm() {
        val text = """
            某某超市
            合计 ¥128.50
            2024-11-03
            谢谢惠顾
        """.trimIndent()
        val r = parser.parse(text)
        assertFalse(r.isWarrantyForm)
        assertEquals(12850L, r.amountCents)
        assertEquals(LocalDate.of(2024, 11, 3).toEpochDay(), r.purchaseDateEpochDay)
    }

    @Test
    fun emptyText_returnsEmptyFields() {
        val r = parser.parse("")
        assertEquals("", r.rawText)
        assertNull(r.purchaseDateEpochDay)
        assertNull(r.warrantyMonths)
        assertFalse(r.isWarrantyForm)
    }

    @Test
    fun chineseDateFormat_parses() {
        val text = """
            购买日期
            报修日期
            保修截止日期
            2025年3月12日
            2025年8月21日
            2028年3月11日
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(LocalDate.of(2025, 3, 12).toEpochDay(), r.purchaseDateEpochDay)
    }

    @Test
    fun synonym_consumeDate_parses() {
        val text = "消费日期：2024-06-18\n实付 ¥59.00"
        val r = parser.parse(text)
        assertEquals(LocalDate.of(2024, 6, 18).toEpochDay(), r.purchaseDateEpochDay)
        assertEquals(5900L, r.amountCents)
    }

    @Test
    fun synonym_purchaseInDate_parses() {
        val text = "购入日期 2023/01/05\n门店：测试店"
        val r = parser.parse(text)
        assertEquals(LocalDate.of(2023, 1, 5).toEpochDay(), r.purchaseDateEpochDay)
    }

    @Test
    fun synonym_warrantyUntil_parsesEnd() {
        val text = """
            购买日期：2024-01-01
            保修至：2026-01-01
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(LocalDate.of(2024, 1, 1).toEpochDay(), r.purchaseDateEpochDay)
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay(), r.warrantyEndEpochDay)
        assertTrue(r.isWarrantyForm)
    }

    @Test
    fun synonym_qualityPeriod_months() {
        val text = "质保期：18个月\n购买日期：2024-02-02"
        val r = parser.parse(text)
        assertEquals(18, r.warrantyMonths)
        assertEquals(LocalDate.of(2024, 2, 2).toEpochDay(), r.purchaseDateEpochDay)
    }

    @Test
    fun synonym_warrantyPeriodShort_months() {
        val text = "保修期：2年\n购入日期：2022-05-05"
        val r = parser.parse(text)
        assertEquals(24, r.warrantyMonths)
    }

    @Test
    fun synonym_salesUnit_merchant() {
        val text = """
            销售单位：华南旗舰店
            购买日期：2024-09-09
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals("华南旗舰店", r.merchantName)
    }

    @Test
    fun synonym_outlet_merchant() {
        val text = "网点：城东售后点\n故障描述：无法开机"
        val r = parser.parse(text)
        assertEquals("城东售后点", r.merchantName)
        assertTrue(r.note?.contains("无法开机") == true)
    }

    @Test
    fun synonym_payableAmount() {
        val text = "应付金额 ¥199.90\n某某数码"
        val r = parser.parse(text)
        assertEquals(19990L, r.amountCents)
    }

    @Test
    fun synonym_problemDescription_note() {
        val text = "问题描述：屏幕闪烁且触控失灵"
        val r = parser.parse(text)
        assertTrue(r.note?.contains("闪烁") == true)
    }

    @Test
    fun garbageText_doesNotInventPurchaseDate() {
        val text = "欢迎光临\n谢谢惠顾\n请保管好随身物品"
        val r = parser.parse(text)
        assertNull(r.purchaseDateEpochDay)
        assertNull(r.amountCents)
    }

    @Test
    fun columnAlign_deadlineLabel_mapsThirdDate() {
        val text = """
            购买日期
            报修日期
            截止日期
            2021-01-10
            2021-06-01
            2024-01-09
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(LocalDate.of(2021, 1, 10).toEpochDay(), r.purchaseDateEpochDay)
        assertEquals(LocalDate.of(2024, 1, 9).toEpochDay(), r.warrantyEndEpochDay)
    }

    @Test
    fun receipt_actualPaid_amount() {
        val text = "实付 88.00 元\n2024.12.25"
        val r = parser.parse(text)
        assertEquals(8800L, r.amountCents)
        assertNotNull(r.purchaseDateEpochDay)
    }
}
