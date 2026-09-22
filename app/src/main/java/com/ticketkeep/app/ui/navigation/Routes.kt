package com.ticketkeep.app.ui.navigation

/**
 * Compose Navigation 路由常量与参数拼装。
 */
object Routes {
    const val LIST = "list"
    const val DETAIL = "detail/{ticketId}"
    const val EDIT = "edit?ticketId={ticketId}&imageUri={imageUri}"
    const val PAYWALL = "paywall"
    const val SETTINGS = "settings"
    const val CLOUD_BACKUP = "cloud_backup"
    const val PRIVACY = "privacy"
    /** 批量相册核对：URI 经 BatchImportSession 传递，不走 nav args */
    const val BATCH_REVIEW = "batch_review"

    fun detail(ticketId: Long) = "detail/$ticketId"

    fun edit(ticketId: Long? = null, imageUri: String? = null): String {
        val id = ticketId?.toString() ?: "-1"
        val uri = imageUri?.let { android.net.Uri.encode(it) } ?: ""
        return "edit?ticketId=$id&imageUri=$uri"
    }
}
