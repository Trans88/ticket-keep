package com.ticketkeep.app.export

import android.content.Context
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.util.DateFormats
import com.ticketkeep.app.util.MoneyFormats
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pro 全部票证 → CSV（UTF-8 with BOM，便于 Excel）。
 * 仅 Pro 入口调用；本类不负责鉴权。
 */
@Singleton
class TicketCsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun exportToCache(tickets: List<Ticket>): File {
        val dir = File(context.cacheDir, "exports").also { if (!it.exists()) it.mkdirs() }
        val stamp = LocalDateTime.now().format(STAMP)
        val file = File(dir, "票证记_导出_${stamp}.csv")
        val csv = buildCsvString(tickets)
        FileOutputStream(file).use { out ->
            out.write(csv.toByteArray(StandardCharsets.UTF_8))
        }
        return file
    }

    companion object {
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
        private val DISPLAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** 纯字符串构建（含 BOM），供导出门面与单测复用。 */
        fun buildCsvString(tickets: List<Ticket>): String {
            val sb = StringBuilder()
            sb.append('\uFEFF')
            sb.append(
                listOf(
                    "id",
                    "商家",
                    "金额_分",
                    "金额_可读",
                    "购买日_epochDay",
                    "购买日_可读",
                    "保修月数",
                    "保修到期_epochDay",
                    "保修到期_可读",
                    "备注",
                    "图片路径",
                    "识别原文",
                    "创建时间_millis",
                    "创建时间_可读",
                    "更新时间_millis",
                    "更新时间_可读",
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
                    formatMillis(t.createdAtMillis),
                    t.updatedAtMillis.toString(),
                    formatMillis(t.updatedAtMillis),
                )
                sb.append(row.joinToString(",") { csvEscape(it) })
                sb.append('\n')
            }
            return sb.toString()
        }

        private fun formatMillis(millis: Long): String {
            val zdt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            return DISPLAY.format(zdt)
        }

        fun csvEscape(value: String): String {
            val needsQuote = value.contains(',') ||
                value.contains('"') ||
                value.contains('\n') ||
                value.contains('\r')
            val escaped = value.replace("\"", "\"\"")
            return if (needsQuote) "\"$escaped\"" else escaped
        }
    }
}