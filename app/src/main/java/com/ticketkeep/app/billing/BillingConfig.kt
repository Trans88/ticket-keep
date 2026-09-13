package com.ticketkeep.app.billing

/**
 * Google Play Billing 商品配置（单一配置点）。
 * Play Console 中创建的订阅 productId 必须与此一致。
 */
object BillingConfig {
    /** 年订阅 Pro：Play Console 订阅 ID */
    const val PRODUCT_ID: String = "ticketkeep_pro_yearly"

    /** 预留：当前仅订阅；日后可扩展一次性购买等 */
    enum class ProductKind {
        SUBS,
        // INAPP, // 预留：一次性购买
    }
}
