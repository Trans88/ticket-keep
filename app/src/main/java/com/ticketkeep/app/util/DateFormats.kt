package com.ticketkeep.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    val display: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    fun epochDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun localDateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    fun formatEpochDay(epochDay: Long?): String {
        if (epochDay == null) return "未设置"
        return display.format(epochDayToLocalDate(epochDay))
    }

    fun todayEpochDay(): Long = LocalDate.now(zone).toEpochDay()

    fun millisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
