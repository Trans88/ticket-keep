package com.ticketkeep.app.ui.navigation

object Routes {
    const val LIST = "list"
    const val DETAIL = "detail/{ticketId}"
    const val EDIT = "edit?ticketId={ticketId}&imageUri={imageUri}"
    const val PAYWALL = "paywall"

    fun detail(ticketId: Long) = "detail/$ticketId"

    fun edit(ticketId: Long? = null, imageUri: String? = null): String {
        val id = ticketId?.toString() ?: "-1"
        val uri = imageUri?.let { android.net.Uri.encode(it) } ?: ""
        return "edit?ticketId=$id&imageUri=$uri"
    }
}
