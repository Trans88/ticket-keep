package com.ticketkeep.app.ocr

data class OcrParseResult(
    val rawText: String = "",
    val merchantName: String? = null,
    val amountCents: Long? = null,
    val purchaseDateEpochDay: Long? = null,
)
