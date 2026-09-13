package com.ticketkeep.app.backup

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份/迁移排除规则防回退：Room DB 与 ticket_images 必须保持 exclude。
 */
class BackupRulesTest {

    private fun findModuleRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(8) {
            val backup = File(dir, "app/src/main/res/xml/backup_rules.xml")
            if (backup.isFile) return dir
            dir = dir.parentFile ?: return File(".")
        }
        return File(".")
    }

    @Test
    fun backupRules_excludesDbAndImages() {
        val root = findModuleRoot()
        val xml = File(root, "app/src/main/res/xml/backup_rules.xml").readText(Charsets.UTF_8)
        assertTrue(xml.contains("ticket_keep.db"))
        assertTrue(xml.contains("ticket_keep.db-shm"))
        assertTrue(xml.contains("ticket_keep.db-wal"))
        assertTrue(xml.contains("ticket_images"))
        assertTrue(xml.contains("<exclude"))
    }

    @Test
    fun dataExtractionRules_excludesDbAndImages() {
        val root = findModuleRoot()
        val xml = File(root, "app/src/main/res/xml/data_extraction_rules.xml").readText(Charsets.UTF_8)
        assertTrue(xml.contains("cloud-backup"))
        assertTrue(xml.contains("device-transfer"))
        assertTrue(xml.contains("ticket_keep.db"))
        assertTrue(xml.contains("ticket_images"))
        assertTrue(xml.split("ticket_keep.db").size >= 3)
    }
}