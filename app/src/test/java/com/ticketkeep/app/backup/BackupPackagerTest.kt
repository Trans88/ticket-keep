package com.ticketkeep.app.backup

import com.ticketkeep.app.data.model.Ticket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 备份打包/解包：CSV 往返与图片条目。
 */
class BackupPackagerTest {

    @Test
    fun packUnpack_csvRoundTrip_withoutImages() {
        val tickets = listOf(
            Ticket(
                id = 1L,
                merchantName = "测试商家",
                amountCents = 1990L,
                note = "含,逗号",
                ocrRawText = "原文",
            ),
        )
        val packed = BackupPackager.pack(tickets) as BackupPackager.PackOutcome.Ok
        assertEquals(1, packed.packed.ticketCount)
        val unpacked = BackupPackager.unpack(packed.packed.zipBytes) as BackupPackager.UnpackOutcome.Ok
        assertEquals(1, unpacked.unpacked.tickets.size)
        assertEquals("测试商家", unpacked.unpacked.tickets.first().merchantName)
        assertEquals(1990L, unpacked.unpacked.tickets.first().amountCents)
    }

    @Test
    fun pack_includesImageWhenFileExists() {
        val tmp = File.createTempFile("tk_img", ".jpg")
        tmp.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02))
        try {
            val tickets = listOf(
                Ticket(id = 2L, merchantName = "有图", imagePath = tmp.absolutePath),
            )
            val packed = BackupPackager.pack(tickets) as BackupPackager.PackOutcome.Ok
            assertEquals(1, packed.packed.imageCount)
            val unpacked = BackupPackager.unpack(packed.packed.zipBytes) as BackupPackager.UnpackOutcome.Ok
            assertEquals(1, unpacked.unpacked.images.size)
            val rel = unpacked.unpacked.tickets.first().imagePath
            assertTrue(rel != null && rel!!.startsWith("images/"))
            assertTrue(unpacked.unpacked.images.containsKey(rel))
        } finally {
            tmp.delete()
        }
    }
}
