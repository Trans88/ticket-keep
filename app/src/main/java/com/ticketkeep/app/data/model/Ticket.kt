package com.ticketkeep.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 票证实体：商家、金额、购买/保修日期、备注、本地图片路径与 OCR 原文。
 */
@Entity(tableName = "tickets")
data class Ticket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantName: String = "",
    val amountCents: Long? = null,
    val purchaseDateEpochDay: Long? = null,
    val warrantyMonths: Int? = null,
    val warrantyEndEpochDay: Long? = null,
    val note: String = "",
    val imagePath: String? = null,
    val ocrRawText: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)
