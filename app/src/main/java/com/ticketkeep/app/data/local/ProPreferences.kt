package com.ticketkeep.app.data.local

import com.ticketkeep.app.entitlement.EntitlementRepository
import com.ticketkeep.app.entitlement.EntitlementStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 兼容层：旧代码仍可注入 [ProPreferences]。
 *
 * - [isPro] → 本地高级版 **或** 旧年订有效（[EntitlementRepository.observeHasLocalPremium]）
 * - [setPro] **已弃用**：不再表示「开通一切」；仅在非 Debug 路径忽略，Debug 请用 [setDebugPro]（仅本地）
 * - 云权益请走 [EntitlementRepository]
 *
 * 正式写入权威路径：BillingManager → EntitlementRepository。
 */
@Singleton
class ProPreferences @Inject constructor(
    private val entitlementRepository: EntitlementRepository,
    private val entitlementStore: EntitlementStore,
) {
    /** @deprecated 语义变为「本地高级 / 旧年订」，不含云备份。 */
    val isPro: Flow<Boolean> = entitlementRepository.observeHasLocalPremium()

    /** Debug 本地模拟是否锁定。 */
    val isDebugOverride: Flow<Boolean> = entitlementStore.isDebugLocalOverride

    /**
     * @deprecated 禁止再把单一 setPro(true) 当成开通全部权益。
     * Billing 正式路径应分别调用 [EntitlementRepository.applyPlayLocalUnlock] /
     * [EntitlementRepository.applyPlayCloudSub] / [EntitlementRepository.applyLegacyYearly]。
     * 此方法仅同步「本地高级」布尔缓存，**不**授予云。
     */
    @Deprecated(
        message = "Use EntitlementRepository.applyPlayLocalUnlock / applyLegacyYearly",
        replaceWith = ReplaceWith(
            "entitlementRepository.applyPlayLocalUnlock(enabled)",
            "com.ticketkeep.app.entitlement.EntitlementRepository",
        ),
    )
    suspend fun setPro(enabled: Boolean) {
        entitlementRepository.applyPlayLocalUnlock(enabled)
    }

    /**
     * Debug 专用：仅模拟本地高级版（不再隐含云备份）。
     */
    suspend fun setDebugPro(enabled: Boolean) {
        entitlementRepository.setDebugLocal(enabled)
    }
}
