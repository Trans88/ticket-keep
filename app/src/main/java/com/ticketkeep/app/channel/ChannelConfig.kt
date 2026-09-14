package com.ticketkeep.app.channel

import com.ticketkeep.app.BuildConfig

/**
 * 发行渠道开关：由 productFlavor 写入 BuildConfig。
 * play = Google Play Billing；china = 国内包（无购买/无导出入口）。
 */
object ChannelConfig {
    /** 当前 flavor 名，如 play / china。 */
    val flavor: String get() = BuildConfig.FLAVOR

    /** 是否启用 Play Billing 连接与购买。 */
    val playBillingEnabled: Boolean get() = BuildConfig.PLAY_BILLING_ENABLED

    /** 是否展示开通/恢复购买（china 为 false）。 */
    val showProPurchase: Boolean get() = BuildConfig.SHOW_PRO_PURCHASE

    /** 是否展示导出入口（china 为 false，避免无效转化）。 */
    val showProExport: Boolean get() = BuildConfig.SHOW_PRO_EXPORT

    val isChinaChannel: Boolean get() = !playBillingEnabled
}
