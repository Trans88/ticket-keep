package com.ticketkeep.app.util

import java.util.Locale

object MoneyFormats {
    fun formatYuan(amountCents: Long?): String {
        if (amountCents == null) return "—"
        val yuan = amountCents / 100.0
        return String.format(Locale.CHINA, "¥%.2f", yuan)
    }

    fun parseYuanToCents(text: String): Long? {
        val cleaned = text
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace("元", "")
            .trim()
        if (cleaned.isEmpty()) return null
        return try {
            val value = cleaned.toDouble()
            Math.round(value * 100)
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun centsToYuanString(amountCents: Long?): String {
        if (amountCents == null) return ""
        val yuan = amountCents / 100.0
        return if (amountCents % 100L == 0L) {
            (amountCents / 100).toString()
        } else {
            String.format(Locale.US, "%.2f", yuan)
        }
    }
}
