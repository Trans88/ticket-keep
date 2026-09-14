package com.ticketkeep.app.ocr

/**
 * OCR 解析结果。字段均可为空：保修单常常没有金额；小票常常没有保修期限。
 * UI 层始终允许用户手改，解析只做预填。
 */
data class OcrParseResult(
    val rawText: String = "",
    val merchantName: String? = null,
    val amountCents: Long? = null,
    val purchaseDateEpochDay: Long? = null,
    /** 保修月数，如「3年」→ 36、「36个月」→ 36 */
    val warrantyMonths: Int? = null,
    /** 保修截止日期（优先来自「保修截止日期 / 保修至」等标签） */
    val warrantyEndEpochDay: Long? = null,
    /** 故障描述 / 备注等长文本 */
    val note: String? = null,
    /** 文本更像保修/报修信息表，而非收银小票 */
    val isWarrantyForm: Boolean = false,
)
