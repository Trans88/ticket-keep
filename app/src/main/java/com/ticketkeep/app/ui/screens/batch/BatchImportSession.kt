package com.ticketkeep.app.ui.screens.batch

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 批量导入会话：相册多选后的 URI 列表暂存于此，再导航到 [BatchReviewScreen]。
 * 不走巨型 nav args，避免 URI 串过长；由 ListScreen 写入、BatchReviewViewModel 消费后清空。
 */
@Singleton
class BatchImportSession @Inject constructor() {
    @Volatile
    var uris: List<Uri> = emptyList()

    fun set(uris: List<Uri>) {
        this.uris = uris
    }

    fun consume(): List<Uri> {
        val current = uris
        uris = emptyList()
        return current
    }

    fun clear() {
        uris = emptyList()
    }
}
