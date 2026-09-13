package com.ticketkeep.app.ocr

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TicketOcrParser 单测：小票与保修单（含双列）解析。
 */
class TicketOcrParserTest {

    private val parser = TicketOcrParser()

    /** 模拟双列 OCR：先全部标签，再全部值（用户保修单真实布局） */
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
        assertTrue("note=" + r.note, listOf("故障", "异常", "卡顿", "黑屏").any { r.note?.contains(it) == true })
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
        assertTrue("note=" + r.note, listOf("故障", "异常", "卡顿", "黑屏").any { r.note?.contains(it) == true })
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
        // 无保修期限等关键词时 isWarrantyForm 可能为 false，但列对齐仍应按序取购买日
        assertEquals(LocalDate.of(2025, 3, 12).toEpochDay(), r.purchaseDateEpochDay)
    }
}
