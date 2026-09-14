package com.ticketkeep.app.util

import java.time.LocalDate

/**
 * 编辑页日期选择的合理范围：拒绝过分远古与过远未来，供 DatePicker 与 ViewModel 共用。
 */
object DateBounds {
    val MIN: LocalDate = LocalDate.of(1990, 1, 1)

    fun max(): LocalDate = LocalDate.now().plusYears(40)

    const val OUT_OF_RANGE_MESSAGE: String = "日期超出合理范围（1990 年至今天起 40 年内）"

    fun isAllowed(date: LocalDate): Boolean =
        !date.isBefore(MIN) && !date.isAfter(max())

    fun yearRange(): IntRange = MIN.year..max().year
}
