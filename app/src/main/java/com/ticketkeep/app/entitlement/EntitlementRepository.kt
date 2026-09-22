package com.ticketkeep.app.entitlement

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 统一权益仓库：对外暴露 [EntitlementSnapshot] 与派生能力流。
 *
 * Billing / Debug / 迁移均经由此处写入；UI 与门禁应观察本仓库而非直接读 DataStore。
 */
@Singleton
class EntitlementRepository @Inject constructor(
    private val store: EntitlementStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { store.ensureMigration() }
    }

    val snapshot: StateFlow<EntitlementSnapshot> = store.snapshot
        .stateIn(scope, SharingStarted.Eagerly, EntitlementSnapshot())

    fun observe(): Flow<EntitlementSnapshot> = store.snapshot

    /** 兼容旧「是否 Pro」观察：= 本地高级版或旧年订有效。 */
    fun observeHasLocalPremium(): Flow<Boolean> = store.snapshot.map { it.hasLocalPremium }

    fun observeCanUseCloud(): Flow<Boolean> = store.snapshot.map { it.canUploadCloud }

    suspend fun setDebugLocal(enabled: Boolean) = store.setDebugLocal(enabled)

    suspend fun setDebugCloud(enabled: Boolean) = store.setDebugCloud(enabled)

    suspend fun setDebugAllOff() = store.setDebugAllOff()

    suspend fun applyPlayLocalUnlock(owned: Boolean) = store.applyPlayLocalUnlock(owned)

    suspend fun applyPlayCloudSub(
        status: CloudSubStatus,
        expiryMillis: Long? = null,
    ) = store.applyPlayCloudSub(status, expiryMillis)

    suspend fun applyLegacyYearly(active: Boolean) = store.applyLegacyYearly(active)

    /** 仅清除云权益缓存；不得触碰本地买断。 */
    suspend fun clearCloudOnly() = store.clearCloudOnly()

    fun isDebugLocalOverride(): Flow<Boolean> = store.isDebugLocalOverride

    fun isDebugCloudOverride(): Flow<Boolean> = store.isDebugCloudOverride
}
