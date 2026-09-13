package com.ticketkeep.app.ocr

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启发式解析 OCR 全文：同时覆盖「收银小票」与「保修/报修信息表」。
 *
 * 策略：
 * 1. 先按「标签 + 值」抽保修单字段（购买日期、保修期限、截止日期、故障描述、商家）。
 * 2. 再回退到小票启发式（合计/¥、首行店名、文中第一个像日期的片段）。
 * 解析失败时对应字段为 null，表单始终可手改。
 */
@Singleton
class TicketOcrParser @Inject constructor() {

    // —— 小票金额：优先「合计/实付」等标签旁的数字，再退到带 ¥ 的金额 ——
    private val amountPatterns = listOf(
        Pattern.compile("""(?:合计|总计|实付|应付|金额|总金额|收款)[^\d]{0,8}([¥￥]?\s*\d{1,7}(?:\.\d{1,2})?)"""),
        Pattern.compile("""([¥￥]\s*\d{1,7}(?:\.\d{1,2})?)"""),
        Pattern.compile("""(\d{1,7}\.\d{2})\s*元?"""),
    )

    // 通用日期片段：2025-03-12 / 2025.03.12 / 2025年3月12日
    private val dateTokenRegex = Pattern.compile(
        """(20\d{2}\s*[-/.年]\s*\d{1,2}\s*[-/.月]\s*\d{1,2}\s*日?)"""
    )

    // 保修期限：「3年」「36个月」「1 年整」等
    private val warrantyPeriodRegex = Pattern.compile(
        """(?:保修期限|保修期|质保期|质保期限)\s*[:：]?\s*(\d+)\s*(年|个月|月)"""
    )
    private val warrantyPeriodLooseRegex = Pattern.compile(
        """(\d+)\s*(年|个月|月)\s*(?:保修|质保)?"""
    )

    fun parse(rawText: String): OcrParseResult {
        val normalized = rawText.replace("\u00A0", " ")
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val isWarranty = looksLikeWarrantyForm(normalized)

        val purchase = extractLabeledDate(
            normalized,
            listOf("购买日期", "购机日期", "购买日", "购置日期", "购入日期"),
        ) ?: if (!isWarranty) guessFirstDate(normalized) else null

        val warrantyEnd = extractLabeledDate(
            normalized,
            listOf("保修截止日期", "保修到期日", "保修到期", "截止日期", "质保到期", "质保截止日期"),
        )

        val months = extractWarrantyMonths(normalized)
        val merchant = extractLabeledValue(
            lines,
            listOf("商家", "门店", "服务商", "销售商", "经销商", "品牌", "厂商"),
        ) ?: guessMerchant(lines, isWarranty)

        val note = extractLabeledMultiline(
            lines,
            listOf("故障描述", "故障现象", "问题描述", "报修内容", "备注", "说明"),
        )

        // 小票才强求金额；保修单没有金额是正常的
        val amount = if (isWarranty) {
            guessAmount(normalized)
        } else {
            guessAmount(normalized)
        }

        return OcrParseResult(
            rawText = rawText,
            merchantName = merchant,
            amountCents = amount,
            purchaseDateEpochDay = purchase?.toEpochDay(),
            warrantyMonths = months,
            warrantyEndEpochDay = warrantyEnd?.toEpochDay(),
            note = note,
            isWarrantyForm = isWarranty,
        )
    }

    /** 含保修表典型栏目即视为保修/报修单 */
    private fun looksLikeWarrantyForm(text: String): Boolean {
        val keys = listOf(
            "保修期限", "保修截止", "保修到期", "报修日期", "故障描述",
            "故障现象", "质保期", "报修单", "保修卡", "保修信息",
        )
        return keys.any { text.contains(it) }
    }

    /**
     * 在「标签」后同一行或下一行找日期。
     * OCR 常把表格拆成「购买日期」与「2025-03-12」两行。
     */
    private fun extractLabeledDate(text: String, labels: List<String>): LocalDate? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            val line = lines[i]
            val label = labels.firstOrNull { line.contains(it) } ?: continue
            // 同行：标签后面的日期
            val after = line.substringAfter(label)
            findDateIn(after)?.let { return it }
            // 下一行常是单元格值
            if (i + 1 < lines.size) {
                findDateIn(lines[i + 1])?.let { return it }
            }
        }
        // 全文：标签与日期之间允许少量杂字（同一段落被拼在一起时）
        for (label in labels) {
            val p = Pattern.compile(
                Pattern.quote(label) + """[^\d]{0,12}""" + dateTokenRegex.pattern()
            )
            val m = p.matcher(text)
            if (m.find()) {
                parseDateCandidate(m.group(1))?.let { return it }
            }
        }
        return null
    }

    private fun findDateIn(chunk: String): LocalDate? {
        val m = dateTokenRegex.matcher(chunk)
        while (m.find()) {
            parseDateCandidate(m.group(1))?.let { return it }
        }
        return null
    }

    private fun extractWarrantyMonths(text: String): Int? {
        warrantyPeriodRegex.matcher(text).let { m ->
            if (m.find()) return toMonths(m.group(1), m.group(2))
        }
        // 宽松：仅当上下文像保修单时，避免把「3年店庆」误当成保修期
        if (looksLikeWarrantyForm(text)) {
            warrantyPeriodLooseRegex.matcher(text).let { m ->
                if (m.find()) return toMonths(m.group(1), m.group(2))
            }
        }
        return null
    }

    private fun toMonths(num: String, unit: String): Int? {
        val n = num.toIntOrNull() ?: return null
        if (n !in 1..1200) return null
        return when {
            unit.startsWith("年") -> n * 12
            else -> n
        }
    }

    /** 标签同行取值；若值太短/像日期则看下一行 */
    private fun extractLabeledValue(lines: List<String>, labels: List<String>): String? {
        for (i in lines.indices) {
            val line = lines[i]
            val label = labels.firstOrNull { line.contains(it) } ?: continue
            var value = line.substringAfter(label)
                .replace(Regex("""^[\s:：\-—_|]+"""), "")
                .trim()
            if (value.isBlank() && i + 1 < lines.size) {
                value = lines[i + 1].trim()
            }
            if (value.length in 2..40 && findDateIn(value) == null) {
                return value
            }
        }
        return null
    }

    private fun extractLabeledMultiline(lines: List<String>, labels: List<String>): String? {
        for (i in lines.indices) {
            val line = lines[i]
            val label = labels.firstOrNull { line.contains(it) } ?: continue
            val same = line.substringAfter(label)
                .replace(Regex("""^[\s:：\-—_|]+"""), "")
                .trim()
            val buf = mutableListOf<String>()
            if (same.isNotBlank()) buf += same
            // 随后若干行直到碰到下一个像标签的行
            var j = i + 1
            while (j < lines.size && buf.size < 6) {
                val next = lines[j]
                if (looksLikeFieldLabel(next)) break
                buf += next
                j++
            }
            val joined = buf.joinToString("\n").trim()
            if (joined.isNotBlank()) return joined
        }
        return null
    }

    private fun looksLikeFieldLabel(line: String): Boolean {
        val labels = listOf(
            "购买日期", "报修日期", "保修期限", "保修截止", "故障描述",
            "商家", "门店", "金额", "合计", "实付", "备注",
        )
        return labels.any { line.startsWith(it) || line == it }
    }

    private fun guessMerchant(lines: List<String>, isWarranty: Boolean): String? {
        val skip = listOf(
            "小票", "收据", "发票", "收银", "谢谢", "欢迎", "合计", "总计", "实付",
            "保修", "报修", "故障", "日期", "期限", "公司", "表格",
        )
        // 保修单：优先带「卡/单/信息」的标题行里的品牌片段，否则取较短的非标签首行
        if (isWarranty) {
            lines.firstOrNull { it.contains("保修") || it.contains("报修") || it.contains("质保") }
                ?.let { title ->
                    val cleaned = title
                        .replace(Regex("""保修卡|保修单|报修单|保修信息|质保卡|信息表|登记表"""), "")
                        .trim()
                    if (cleaned.length in 2..20) return cleaned
                }
        }
        return lines.firstOrNull { line ->
            line.length in 2..30 &&
                skip.none { line.contains(it) } &&
                !looksLikeFieldLabel(line) &&
                !line.matches(Regex("""^[\d¥￥.,\s:\-_/]+$""")) &&
                findDateIn(line) == null
        }
    }

    private fun guessAmount(text: String): Long? {
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(text)
            var best: Long? = null
            while (matcher.find()) {
                val raw = matcher.group(1)
                    ?.replace("¥", "")
                    ?.replace("￥", "")
                    ?.replace(" ", "")
                    ?: continue
                val cents = try {
                    Math.round(raw.toDouble() * 100)
                } catch (_: NumberFormatException) {
                    continue
                }
                if (cents in 1..99_999_999) {
                    best = cents
                }
            }
            if (best != null) return best
        }
        return null
    }

    private fun guessFirstDate(text: String): LocalDate? = findDateIn(text)

    private fun parseDateCandidate(raw: String): LocalDate? {
        val compact = raw.replace(Regex("""\s+"""), "")
        return try {
            if (compact.contains("年")) {
                val normalized = if (compact.endsWith("日")) compact else "${compact}日"
                LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
            } else {
                val normalized = compact
                    .replace("年", "-")
                    .replace("月", "-")
                    .replace("日", "")
                val parts = normalized.split("-", "/", ".")
                if (parts.size != 3) return null
                val date = LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                if (date.year in 2000..2100) date else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
