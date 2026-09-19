package com.ticketkeep.app.backup

import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.export.TicketCsvImporter
import com.ticketkeep.app.util.DateFormats
import com.ticketkeep.app.util.MoneyFormats
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 可恢复备份包：ZIP（manifest.json + tickets.csv + images/）。
 * 纯逻辑可单测；图片按相对路径打入，恢复时再落盘。
 */
object BackupPackager {
    const val MANIFEST_NAME = "manifest.json"
    const val CSV_NAME = "tickets.csv"
    const val IMAGES_DIR = "images/"
    const val FORMAT_VERSION = 1

    data class Packed(
        val zipBytes: ByteArray,
        val ticketCount: Int,
        val imageCount: Int,
    )

    data class Unpacked(
        val tickets: List<Ticket>,
        val images: Map<String, ByteArray>,
        val skippedRows: Int,
        val warnings: List<String>,
        val manifestJson: String?,
    )

    sealed class PackOutcome {
        data class Ok(val packed: Packed) : PackOutcome()
        data class Error(val message: String) : PackOutcome()
    }

    sealed class UnpackOutcome {
        data class Ok(val unpacked: Unpacked) : UnpackOutcome()
        data class Error(val message: String) : UnpackOutcome()
    }

    /**
     * 打包票证与本地可读图片。CSV「图片路径」写为 images/文件名。
     */
    fun pack(tickets: List<Ticket>, appVersion: String = "1.0"): PackOutcome {
        return try {
            val baos = ByteArrayOutputStream()
            var imageCount = 0
            ZipOutputStream(baos).use { zos ->
                val manifest = """{"formatVersion":$FORMAT_VERSION,"ticketCount":${tickets.size},"createdAtMillis":${System.currentTimeMillis()},"appVersion":${jsonStr(appVersion)}}"""
                zos.putNextEntry(ZipEntry(MANIFEST_NAME))
                zos.write(manifest.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                val rewritten = mutableListOf<Ticket>()
                val imageEntries = linkedMapOf<String, ByteArray>()
                for ((idx, t) in tickets.withIndex()) {
                    val path = t.imagePath
                    if (!path.isNullOrBlank()) {
                        val src = File(path)
                        if (src.isFile) {
                            val name = "img_${idx}_${src.name.filter { it.isLetterOrDigit() || it == '.' || it == '_' }.ifBlank { "photo.jpg" }}"
                            val rel = IMAGES_DIR + name
                            imageEntries[rel] = src.readBytes()
                            rewritten += t.copy(imagePath = rel)
                            imageCount++
                            continue
                        }
                    }
                    rewritten += t.copy(imagePath = null)
                }

                zos.putNextEntry(ZipEntry(CSV_NAME))
                zos.write(buildCsv(rewritten).toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                for ((rel, bytes) in imageEntries) {
                    zos.putNextEntry(ZipEntry(rel))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            PackOutcome.Ok(Packed(baos.toByteArray(), tickets.size, imageCount))
        } catch (e: Exception) {
            PackOutcome.Error("打包失败：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 从 ZIP 字节解包；票证解析复用 [TicketCsvImporter]；图片键为 zip 内相对路径。
     */
    fun unpack(zipBytes: ByteArray): UnpackOutcome {
        return try {
            var csvText: String? = null
            var manifest: String? = null
            val images = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.trimStart('/')
                        val bytes = zis.readBytes()
                        when {
                            name == CSV_NAME || name.endsWith("/$CSV_NAME") ->
                                csvText = bytes.toString(StandardCharsets.UTF_8)
                            name == MANIFEST_NAME || name.endsWith("/$MANIFEST_NAME") ->
                                manifest = bytes.toString(StandardCharsets.UTF_8)
                            name.startsWith(IMAGES_DIR) || name.contains("/$IMAGES_DIR") -> {
                                val key = if (name.startsWith(IMAGES_DIR)) name else name.substringAfter(IMAGES_DIR).let { IMAGES_DIR + it }
                                images[key] = bytes
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (csvText.isNullOrBlank()) {
                return UnpackOutcome.Error("备份包中缺少 tickets.csv")
            }
            when (val outcome = TicketCsvImporter.parse(csvText!!)) {
                is TicketCsvImporter.Outcome.Error -> UnpackOutcome.Error(outcome.message)
                is TicketCsvImporter.Outcome.Ready -> UnpackOutcome.Ok(
                    Unpacked(
                        tickets = outcome.preview.tickets,
                        images = images,
                        skippedRows = outcome.preview.skippedRows,
                        warnings = outcome.preview.rowWarnings,
                        manifestJson = manifest,
                    ),
                )
            }
        } catch (e: Exception) {
            UnpackOutcome.Error("解包失败：${e.message ?: "未知错误"}")
        }
    }

    /** 与导出 CSV 表头对齐，便于复用导入器。 */
    fun buildCsv(tickets: List<Ticket>): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append(
            listOf(
                "id", "商家", "金额_分", "金额_可读", "购买日_epochDay", "购买日_可读",
                "保修月数", "保修到期_epochDay", "保修到期_可读", "备注", "图片路径",
                "识别原文", "创建时间_millis", "创建时间_可读", "更新时间_millis", "更新时间_可读",
            ).joinToString(",") { csvEscape(it) },
        )
        sb.append('\n')
        for (t in tickets) {
            val row = listOf(
                t.id.toString(),
                t.merchantName,
                t.amountCents?.toString().orEmpty(),
                MoneyFormats.formatYuan(t.amountCents),
                t.purchaseDateEpochDay?.toString().orEmpty(),
                DateFormats.formatEpochDay(t.purchaseDateEpochDay),
                t.warrantyMonths?.toString().orEmpty(),
                t.warrantyEndEpochDay?.toString().orEmpty(),
                DateFormats.formatEpochDay(t.warrantyEndEpochDay),
                t.note,
                t.imagePath.orEmpty(),
                t.ocrRawText,
                t.createdAtMillis.toString(),
                "",
                t.updatedAtMillis.toString(),
                "",
            )
            sb.append(row.joinToString(",") { csvEscape(it) })
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val needs = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needs) "\"$escaped\"" else escaped
    }

    private fun jsonStr(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
