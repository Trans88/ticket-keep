package com.ticketkeep.app.ocr

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启发式解析收据 OCR 文本：商家名 / 金额 / 日期。
 * 解析失败时字段为 null，表单始终可手改。
 */
@Singleton
class TicketOcrParser @Inject constructor() {

    private val amountPatterns = listOf(
        Pattern.compile("""(?:合计|总计|实付|应付|金额|总金额|收款)[^\d]{0,8}([¥￥]?\s*\d{1,7}(?:\.\d{1,2})?)"""),
        Pattern.compile("""([¥￥]\s*\d{1,7}(?:\.\d{1,2})?)"""),
        Pattern.compile("""(\d{1,7}\.\d{2})\s*元?"""),
    )

    private val dateRegex = Pattern.compile(
        """(20\d{2}[-/.年]\d{1,2}[-/.月]\d{1,2}日?)"""
    )

    fun parse(rawText: String): OcrParseResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return OcrParseResult(
            rawText = rawText,
            merchantName = guessMerchant(lines),
            amountCents = guessAmount(rawText),
            purchaseDateEpochDay = guessDate(rawText)?.toEpochDay(),
        )
    }

    private fun guessMerchant(lines: List<String>): String? {
        val skip = listOf("小票", "收据", "发票", "收银", "谢谢", "欢迎", "合计", "总计", "实付")
        return lines.firstOrNull { line ->
            line.length in 2..30 &&
                skip.none { line.contains(it) } &&
                !line.matches(Regex("""^[\d¥￥.,\s:-]+$"""))
        }
    }

    private fun guessAmount(text: String): Long? {
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(text)
            var best: Long? = null
            while (matcher.find()) {
                val raw = matcher.group(1)?.replace("¥", "")?.replace("￥", "")?.replace(" ", "")
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

    private fun guessDate(text: String): LocalDate? {
        val matcher = dateRegex.matcher(text)
        while (matcher.find()) {
            val raw = matcher.group(1) ?: continue
            val candidate = parseDateCandidate(raw) ?: continue
            if (candidate.year in 2000..2100) return candidate
        }
        return null
    }

    private fun parseDateCandidate(raw: String): LocalDate? {
        return try {
            if (raw.contains("年")) {
                LocalDate.parse(raw, DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
            } else {
                val normalized = raw.replace("年", "-").replace("月", "-").replace("日", "")
                val parts = normalized.split("-", "/", ".")
                if (parts.size != 3) return null
                LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            }
        } catch (_: Exception) {
            null
        }
    }
}
