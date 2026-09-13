package com.ticketkeep.app.billing

/** BillingClient 连接状态 */
enum class BillingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    /** Play 不可用 / 服务断开等，保留本地 Pro 缓存 */
    UNAVAILABLE,
}
