package com.ticketkeep.app.ui.screens.batch

import java.time.LocalDate
import java.util.UUID

/**
 * 批量草稿 OCR / 表单状态。
 */
enum class BatchOcrStatus {
    /** 等待识别 */
    PENDING,
    /** 正在识别 */
    RUNNING,
    /** 识别成功（字段可能仍需手改） */
    SUCCESS,
    /** 识别失败，可重试或手填 */
    FAILED,
}

/**
 * 批量核对页中的单张草稿：一图一票，独立字段；保存成功后写入 [savedTicketId] 防重复插入。
 */
data class BatchDraftItem(
    val id: String = UUID.randomUUID().toString(),
    /** 相册原始 URI（字符串，便于 SavedState 持久化） */
    val sourceUri: String = "",
    /** [ImageStorage.persistImage] 后的本地路径；进程死后恢复 OCR/缩略图 */
    val localImagePath: String? = null,
    val merchantName: String = "",
    val amountText: String = "",
    val purchaseDate: LocalDate? = null,
    val warrantyMonthsText: String = "",
    val warrantyEndDate: LocalDate? = null,
    val note: String = "",
    val ocrRawText: String = "",
    val ocrStatus: BatchOcrStatus = BatchOcrStatus.PENDING,
    val errorMessage: String? = null,
    /** 是否纳入「保存所选票证」 */
    val selected: Boolean = true,
    /** 已成功入库的票证 id；非 null 时禁止再次 insert */
    val savedTicketId: Long? = null,
    /** 用户是否手改过字段（取消确认用） */
    val userEdited: Boolean = false,
    val useManualWarrantyEnd: Boolean = false,
    /** 行是否展开编辑区 */
    val expanded: Boolean = false,
)
