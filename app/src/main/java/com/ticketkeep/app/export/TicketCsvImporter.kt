package com.ticketkeep.app.export

import com.ticketkeep.app.data.model.Ticket
import java.io.File

/**
 * Pro CSV 导入解析：兼容 [TicketCsvExporter] 表头与 UTF-8 BOM；未知列忽略；缺「商家」则失败。
 * 策略：一律按新票证插入（忽略 id）；跨设备图片路径若本地不存在则清空。
 */
object TicketCsvImporter {

    /** 解析成功后的预览：可插入票证、跳过行数与行级告警。 */
    data class Preview(
        val tickets: List<Ticket>,
        val skippedRows: Int,
        val rowWarnings: List<String>,
    )

    sealed class Outcome {
        data class Error(val message: String) : Outcome()
        data class Ready(val preview: Preview) : Outcome()
    }

    private val requiredHeader = "商家"

    fun parse(csvText: String): Outcome {
        val text = csvText.removePrefix("\uFEFF").trimStart('\uFEFF')
        if (text.isBlank()) {
            return Outcome.Error("CSV 为空，请选择有效的导出文件")
        }
        val rows = parseCsvRows(text)
        if (rows.isEmpty()) {
            return Outcome.Error("CSV 无有效行")
        }
        val header = rows.first().map { it.trim() }
        val index = header.withIndex().associate { (i, name) -> name to i }
        if (!index.containsKey(requiredHeader)) {
            return Outcome.Error("CSV 缺少必要列：$requiredHeader（请使用本应用导出的 CSV）")
        }

        val tickets = mutableListOf<Ticket>()
        var skipped = 0
        val warnings = mutableListOf<String>()
        for ((lineNo, row) in rows.drop(1).withIndex()) {
            val humanLine = lineNo + 2 // 1-based + header
            if (row.all { it.isBlank() }) {
                skipped++
                continue
            }
            try {
                val merchant = cell(row, index, "商家").trim()
                if (merchant.isBlank()) {
                    skipped++
                    warnings.add("第 ${humanLine} 行：商家为空，已跳过")
                    continue
                }
                val amountCents = cell(row, index, "金额_分").toLongOrNull()
                val purchase = cell(row, index, "购买日_epochDay").toLongOrNull()
                val months = cell(row, index, "保修月数").toIntOrNull()
                val end = cell(row, index, "保修到期_epochDay").toLongOrNull()
                val note = cell(row, index, "备注")
                val imageRaw = cell(row, index, "图片路径").trim()
                val imagePath = imageRaw.takeIf { it.isNotEmpty() }
                val ocr = cell(row, index, "识别原文")
                val created = cell(row, index, "创建时间_millis").toLongOrNull()
                    ?: System.currentTimeMillis()
                val updated = cell(row, index, "更新时间_millis").toLongOrNull() ?: created
                tickets += Ticket(
                    id = 0L, // 一律新插入
                    merchantName = merchant,
                    amountCents = amountCents,
                    purchaseDateEpochDay = purchase,
                    warrantyMonths = months,
                    warrantyEndEpochDay = end,
                    note = note,
                    imagePath = imagePath,
                    ocrRawText = ocr,
                    createdAtMillis = created,
                    updatedAtMillis = updated,
                )
            } catch (e: Exception) {
                skipped++
                warnings.add("第 ${humanLine} 行：解析失败（${e.message ?: "未知错误"}）")
            }
        }
        if (tickets.isEmpty()) {
            return Outcome.Error(
                if (warnings.isNotEmpty()) {
                    "没有可导入的票证。" + warnings.take(3).joinToString("；")
                } else {
                    "没有可导入的票证"
                },
            )
        }
        return Outcome.Ready(
            Preview(
                tickets = tickets,
                skippedRows = skipped,
                rowWarnings = warnings,
            ),
        )
    }

    /** 导入落库前清理跨设备无效图片路径。 */
    fun sanitizeImagePaths(tickets: List<Ticket>): List<Ticket> =
        tickets.map { t ->
            val path = t.imagePath
            if (path.isNullOrBlank()) {
                t.copy(imagePath = null)
            } else if (File(path).isFile) {
                t
            } else {
                t.copy(imagePath = null)
            }
        }

    private fun cell(row: List<String>, index: Map<String, Int>, key: String): String {
        val i = index[key] ?: return ""
        return row.getOrNull(i).orEmpty()
    }

    /** 简易 RFC4180 风格 CSV 分行/分列（支持引号转义）。 */
    fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        val row = mutableListOf<String>()
        var i = 0
        var inQuotes = false
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    row += field.toString()
                    field.clear()
                }
                c == '\n' -> {
                    row += field.toString()
                    field.clear()
                    rows += row.toList()
                    row.clear()
                }
                c == '\r' -> {
                    // ignore; handle \r\n via \n
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row.toList()
        }
        return rows.filter { it.isNotEmpty() && !(it.size == 1 && it[0].isBlank()) }
    }
}
