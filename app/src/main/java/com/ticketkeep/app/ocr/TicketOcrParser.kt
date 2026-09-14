package com.ticketkeep.app.ocr

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * 启发式解析 OCR 全文：收银小票 + 保修/报修信息表。
 *
 * 同义词覆盖购买/购入/消费日期、保修期/质保期、保修至/截止日期、
 * 故障/问题描述、商家/门店/网点/销售单位、合计/实付/应付金额等。
 *
 * 保修单常见双列表格会被 ML Kit 读成「先全部左列标签，再全部右列值」。
 * 因此除了「标签同行/下一行」外，还用「日期类标签出现顺序 ↔ 全文日期出现顺序」做列对齐配对。
 */
@Singleton
class TicketOcrParser @Inject constructor() {

    private val amountPatterns = listOf(
        Pattern.compile("""(?:合计|总计|实付|应付|应付金额|实付金额|合计金额|金额|总金额|收款|维修费用)[^\d]{0,8}([¥￥]?\s*\d{1,7}(?:\.\d{1,2})?)"""),
        Pattern.compile("""([¥￥]\s*\d{1,7}(?:\.\d{1,2})?)"""),
        Pattern.compile("""(\d{1,7}\.\d{2})\s*元?"""),
    )

    private val dateTokenRegex = Pattern.compile(
        """(20\d{2}\s*[-/.年]\s*\d{1,2}\s*[-/.月]\s*\d{1,2}\s*日?)"""
    )

    private val warrantyPeriodRegex = Pattern.compile(
        """(?:保修期限|保修期|质保期|质保期限|保修时间)\s*[:：]?\s*(\d+)\s*(年|个月|月)"""
    )
    private val periodTokenRegex = Pattern.compile("""(\d+)\s*(年|个月|月)""")

    /** 日期类字段：顺序用于双列对齐 */
    private val purchaseLabels = listOf(
        "购买日期", "购入日期", "购机日期", "购置日期", "消费日期",
        "购买日", "购入日", "消费日",
    )
    private val repairLabels = listOf("报修日期", "送修日期", "受理日期")
    private val warrantyEndLabels = listOf(
        "保修截止日期", "质保截止日期", "保修到期日", "保修到期", "质保到期",
        "保修至", "质保至", "截止日期",
    )
    private val dateFieldLabels = purchaseLabels + repairLabels + warrantyEndLabels

    private val merchantLabels = listOf(
        "服务网点", "销售单位", "商家", "门店", "网点", "服务商",
        "销售商", "经销商", "品牌", "厂商",
    )
    private val noteLabels = listOf(
        "故障描述", "问题描述", "故障现象", "故障", "报修内容", "备注", "说明",
    )

    fun parse(rawText: String): OcrParseResult {
        val normalized = rawText.replace("\u00A0", " ").replace("\r\n", "\n")
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val isWarranty = looksLikeWarrantyForm(normalized)

        val purchase = extractLabeledDate(normalized, purchaseLabels)
            ?: extractDateByColumnAlign(normalized, purchaseLabels)
            ?: if (!isWarranty) findAllDates(normalized).firstOrNull() else null

        val warrantyEnd = extractLabeledDate(normalized, warrantyEndLabels)
            ?: extractDateByColumnAlign(normalized, warrantyEndLabels)

        val months = extractWarrantyMonths(normalized)
            ?: extractPeriodByColumnAlign(normalized)

        val merchant = extractLabeledValue(lines, merchantLabels)
            ?: guessMerchant(lines, isWarranty)

        val note = extractLabeledMultiline(lines, noteLabels)

        return OcrParseResult(
            rawText = rawText,
            merchantName = merchant,
            amountCents = guessAmount(normalized),
            purchaseDateEpochDay = purchase?.toEpochDay(),
            warrantyMonths = months,
            warrantyEndEpochDay = warrantyEnd?.toEpochDay(),
            note = note,
            isWarrantyForm = isWarranty,
        )
    }

    private fun looksLikeWarrantyForm(text: String): Boolean {
        val keys = listOf(
            "保修期限", "保修期", "保修截止", "保修到期", "保修至", "报修日期",
            "故障描述", "问题描述", "故障现象", "质保期", "报修单", "保修卡",
            "保修信息", "保修单号", "服务网点", "销售单位",
        )
        return keys.any { text.contains(it) }
    }

    /**
     * 同行 / 下一行 / 标签后短窗口内的日期。
     * 对「购买日期\n报修日期\n...\n2025-03-12」这种双列拆分无效，需走列对齐。
     */
    private fun extractLabeledDate(text: String, labels: List<String>): LocalDate? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        // 同行日期始终可信
        for (i in lines.indices) {
            val line = lines[i]
            val label = labels.firstOrNull { line.contains(it) } ?: continue
            val after = line.substringAfter(label)
            findDateIn(after)?.let { return it }
        }
        // 双列：多个日期类标签连排后再出日期 → 勿用「下一行/短窗口」把第一个日期误绑到截止日期
        if (isStackedDateLabelColumnLayout(lines)) {
            return null
        }
        for (i in lines.indices) {
            val line = lines[i]
            val label = labels.firstOrNull { line.contains(it) } ?: continue
            if (i + 1 < lines.size) {
                findDateIn(lines[i + 1])?.let { return it }
            }
        }
        for (label in labels) {
            val idx = text.indexOf(label)
            if (idx < 0) continue
            val window = text.substring(idx + label.length, min(text.length, idx + label.length + 48))
            findDateIn(window)?.let { return it }
        }
        return null
    }

    /** 双列布局：日期类标签先连排，日期 token 后出现。此时应交由 [extractDateByColumnAlign] 按序配对。 */
    private fun isStackedDateLabelColumnLayout(lines: List<String>): Boolean {
        var labelCount = 0
        for (line in lines) {
            if (findDateIn(line) != null) break
            if (dateFieldLabels.any { line.contains(it) }) labelCount++
        }
        return labelCount >= 2
    }

    /**
     * 双列对齐：文档中「日期类标签」按出现顺序排，全文日期 token 按出现顺序排，下标对应。
     * 例：标签序 [购买日期, 报修日期, 保修截止日期] + 日期序 [2025-03-12, 2025-08-21, 2028-03-11]
     */
    private fun extractDateByColumnAlign(text: String, targetLabels: List<String>): LocalDate? {
        // 最长标签优先，避免「购买日」吃掉「购买日期」、「截止日期」吃掉「保修截止日期」
        val labelHits = findNonOverlappingLabelHits(text, dateFieldLabels)
        if (labelHits.isEmpty()) return null

        val targetHit = labelHits.firstOrNull { it.first in targetLabels } ?: return null
        val rankAmongDateLabels = labelHits.indexOf(targetHit)
        val dates = findAllDates(text)
        if (rankAmongDateLabels in dates.indices) {
            return dates[rankAmongDateLabels]
        }
        return null
    }

    /**
     * 在全文中找标签出现位置：长标签优先，占用区间不重叠，再按出现顺序排序。
     */
    private fun findNonOverlappingLabelHits(
        text: String,
        labels: List<String>,
    ): List<Pair<String, Int>> {
        val occupied = mutableListOf<IntRange>()
        val hits = mutableListOf<Pair<String, Int>>()
        for (label in labels.sortedByDescending { it.length }) {
            val idx = text.indexOf(label)
            if (idx < 0) continue
            val range = idx until (idx + label.length)
            val overlap = occupied.any { it.first < range.last && range.first < it.last }
            if (overlap) continue
            occupied += range
            hits += label to idx
        }
        return hits.sortedBy { it.second }
    }

    private fun extractWarrantyMonths(text: String): Int? {
        warrantyPeriodRegex.matcher(text).let { m ->
            if (m.find()) return toMonths(m.group(1), m.group(2))
        }
        // 标签同行/下一行
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            if (!lines[i].contains("保修期限") && !lines[i].contains("保修期") && !lines[i].contains("质保期")) continue
            val after = lines[i].substringAfter("保修期限").substringAfter("质保期")
            periodTokenRegex.matcher(after).let { m ->
                if (m.find()) return toMonths(m.group(1), m.group(2))
            }
            if (i + 1 < lines.size) {
                periodTokenRegex.matcher(lines[i + 1]).let { m ->
                    if (m.find()) return toMonths(m.group(1), m.group(2))
                }
            }
        }
        return null
    }

    /** 双列时「保修期限」常与「3年」不对齐到同行，在全文 period token 里取第一个合理年/月 */
    private fun extractPeriodByColumnAlign(text: String): Int? {
        if (!text.contains("保修期限") && !text.contains("保修期") && !text.contains("质保期")) return null
        val m = periodTokenRegex.matcher(text)
        while (m.find()) {
            val months = toMonths(m.group(1), m.group(2)) ?: continue
            // 过滤明显不是保修期的「2025年」——periodToken 不该吃到四位数年份，因模式是 (\d+)年
            // 但「2025年」会匹配 2025+年 → 24200 月，toMonths 有上限
            if (months in 1..120) return months
        }
        return null
    }

    private fun toMonths(num: String, unit: String): Int? {
        val n = num.toIntOrNull() ?: return null
        if (n !in 1..1200) return null
        val months = when {
            unit.startsWith("年") -> n * 12
            else -> n
        }
        // 「2025年」会被弄成 24300，直接丢弃
        if (months > 1200) return null
        return months
    }

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
            if (value.length in 2..40 && findDateIn(value) == null && !looksLikeFieldLabel(value)) {
                return value
            }
        }
        // 双列：标签块之后的值块，按 merchant 标签在全部标签中的序配对（简化：取含「中心/店/公司」的值行）
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
            if (same.isNotBlank() && !looksLikeFieldLabel(same)) buf += same
            var j = i + 1
            while (j < lines.size && buf.size < 6) {
                val next = lines[j]
                if (looksLikeFieldLabel(next)) break
                // 双列模式下下一行可能是下一个标签，已由 looksLikeFieldLabel 拦住
                // 值块里的故障描述往往在后半段；若下一行仍是短标签则停
                buf += next
                j++
            }
            val joined = buf.joinToString("\n").trim()
            if (joined.isNotBlank() && findDateIn(joined) == null) return joined
        }
        // 双列兜底：故障描述标签在标签区，描述正文在值区——用「故障描述」在 date/note 标签序之后的文本块
        return extractNoteByColumnAlign(lines)
    }

    private fun extractNoteByColumnAlign(lines: List<String>): String? {
        val labelIdx = lines.indexOfFirst { line -> noteLabels.any { line.contains(it) } }
        if (labelIdx < 0) return null
        // 若下一行就是正文（非标签），已在上面处理；此处找值区中较长的中文句子
        val candidates = lines.drop(labelIdx + 1).filter { line ->
            line.length >= 8 &&
                !looksLikeFieldLabel(line) &&
                findDateIn(line) == null &&
                !line.matches(Regex("""^[A-Z0-9\-*¥￥.\s]+$"""))
        }
        // 优先强语义故障词，避免产品名等长句抢 note；勿用 length>=10 做 firstOrNull
        return candidates.firstOrNull {
            it.contains("故障") || it.contains("异常") || it.contains("卡顿") ||
                it.contains("无法") || it.contains("黑屏") || it.contains("闪烁") ||
                it.contains("蓝牙")
        } ?: candidates.maxByOrNull { it.length }
    }

    private fun looksLikeFieldLabel(line: String): Boolean {
        val labels = listOf(
            "保修单号", "客户姓名", "联系电话", "产品名称", "产品型号", "产品序列号",
            "购买日期", "购入日期", "消费日期", "报修日期", "保修期限", "保修期",
            "保修截止", "保修至", "故障描述", "问题描述", "检测结果",
            "处理方式", "维修费用", "维修状态", "服务网点", "销售单位", "技术人员",
            "商家", "门店", "网点", "金额", "合计", "实付", "应付", "备注",
        )
        return labels.any { line == it || line.startsWith(it) }
    }

    private fun guessMerchant(lines: List<String>, isWarranty: Boolean): String? {
        val skip = listOf(
            "小票", "收据", "发票", "收银", "谢谢", "欢迎", "合计", "总计", "实付",
            "保修", "报修", "故障", "日期", "期限", "表格", "内容",
        )
        if (isWarranty) {
            lines.firstOrNull { it.contains("中心") || it.contains("服务") || it.contains("4S") }
                ?.takeIf { it.length in 2..30 && !looksLikeFieldLabel(it) }
                ?.let { return it }
            lines.firstOrNull { it.contains("保修") || it.contains("报修") }
                ?.let { title ->
                    val cleaned = title
                        .replace(Regex("""保修卡|保修单|报修单|保修信息|质保卡|信息表|登记表|内容"""), "")
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
                // 保修单常见 ¥0，允许 0；小票 0 也无妨
                if (cents in 0..99_999_999) {
                    best = cents
                }
            }
            if (best != null) return best
        }
        return null
    }

    private fun findAllDates(text: String): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        val seen = mutableSetOf<Long>()
        val m = dateTokenRegex.matcher(text)
        while (m.find()) {
            val d = parseDateCandidate(m.group(1)) ?: continue
            if (seen.add(d.toEpochDay())) out += d
        }
        return out
    }

    private fun findDateIn(chunk: String): LocalDate? = findAllDates(chunk).firstOrNull()

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
