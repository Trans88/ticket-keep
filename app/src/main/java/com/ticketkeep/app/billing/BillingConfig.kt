package com.ticketkeep.app.billing

/**
 * Google Play Billing 商品配置（单一配置点）。
 *
 * Phase 1：保留旧年订查询；新本地买断 / 云年订 ID 为占位，[enableNewProductSales]=false
 * 直至用户确认真实 ID、价格与计费形式。不得在未确认前开放新商品销售。
 */
object BillingConfig {
    /** @deprecated 请用 [LEGACY_PRO_YEARLY]；保留别名以免旧引用断裂。 */
    const val PRODUCT_ID: String = "ticketkeep_pro_yearly"

    /** 旧 Pro 年订：继续查询与恢复，兼容既有订阅者。 */
    const val LEGACY_PRO_YEARLY: String = "ticketkeep_pro_yearly"

    /** 本地高级版一次性解锁（非消耗型）占位 ID；销售未开放。 */
    const val LOCAL_UNLOCK_INAPP: String = "ticketkeep_local_unlock"

    /** 加密云备份年订占位 ID；销售未开放。 */
    const val CLOUD_BACKUP_YEARLY: String = "ticketkeep_cloud_yearly"

    /**
     * 用户已确认的展示价（人民币，仅 UI 回退用）。
     * 正式扣款价以 Google Play 返回为准；未拉取到 Play 价格时显示此文案。
     * 2026-09-22 用户确认：本地高级 ¥39 一次；云备份 ¥29/年。
     */
    const val DISPLAY_PRICE_LOCAL_UNLOCK: String = "¥39"
    const val DISPLAY_PRICE_CLOUD_YEARLY: String = "¥29/年"
    const val DISPLAY_PRICE_LOCAL_NOTE: String = "一次购买"
    const val DISPLAY_PRICE_CLOUD_NOTE: String = "按年付费"


    /**
     * 是否允许拉起新商品购买流。
     * 用户确认商品 ID / 价格 / 续费形式前必须为 false；UI 展示「商品待配置」。
     */
    @Volatile
    var enableNewProductSales: Boolean = false

    /** 购买目标类型（launchPurchaseFlow 参数）。 */
    enum class ProductKind {
        /** 本地高级版一次性（INAPP） */
        LOCAL_UNLOCK,
        /** 云备份年订（SUBS） */
        CLOUD_YEARLY,
        /** 旧 Pro 年订（SUBS，兼容） */
        LEGACY_YEARLY,
    }

    fun productIdOf(kind: ProductKind): String = when (kind) {
        ProductKind.LOCAL_UNLOCK -> LOCAL_UNLOCK_INAPP
        ProductKind.CLOUD_YEARLY -> CLOUD_BACKUP_YEARLY
        ProductKind.LEGACY_YEARLY -> LEGACY_PRO_YEARLY
    }

    fun isSubs(kind: ProductKind): Boolean =
        kind == ProductKind.CLOUD_YEARLY || kind == ProductKind.LEGACY_YEARLY

    fun salesEnabledFor(kind: ProductKind): Boolean = when (kind) {
        ProductKind.LEGACY_YEARLY -> true // 旧商品仍可恢复；新购是否继续销售由商店侧决定
        ProductKind.LOCAL_UNLOCK, ProductKind.CLOUD_YEARLY -> enableNewProductSales
    }
}
