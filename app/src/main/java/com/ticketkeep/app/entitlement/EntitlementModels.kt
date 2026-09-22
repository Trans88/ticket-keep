package com.ticketkeep.app.entitlement

/**
 * 本地高级版解锁状态（一次性买断）。
 *
 * [UNKNOWN] 表示尚未得到可信查询结果，**不得**当作 [NOT_OWNED] 去清缓存或撤销已有权益。
 */
enum class LocalUnlockStatus {
    UNKNOWN,
    NOT_OWNED,
    OWNED,
    REVOKED,
}

/**
 * 云备份订阅状态。
 *
 * [UNKNOWN] 同样不得当作已过期去清本地买断或删除本地数据。
 */
enum class CloudSubStatus {
    UNKNOWN,
    ACTIVE,
    CANCELED_BUT_ACTIVE,
    IN_GRACE,
    ON_HOLD,
    EXPIRED,
    REVOKED,
}

/**
 * 权益来源标记（用于迁移与 Debug 隔离）。
 */
enum class EntitlementSource {
    NONE,
    PLAY_INAPP,
    PLAY_SUBS_LEGACY,
    DEBUG,
    MIGRATION_CACHE,
}

/**
 * 统一权益快照：本地买断与云订阅独立建模。
 *
 * - [hasLocalPremium]：本地高级能力（条数 / PDF / CSV）；旧年订 [legacyYearlyActive] 兼容计入。
 * - [canUploadCloud] / [canRestoreCloud]：仅云订阅有效期内可用；**不**因本地买断自动开通。
 * - 云到期不影响本地买断；退出备份账号也不清本地买断。
 *
 * TODO(retention)：恢复窗口目前与上传权限相同；保留期天数待用户确认后再拆分。
 */
data class EntitlementSnapshot(
    val localStatus: LocalUnlockStatus = LocalUnlockStatus.UNKNOWN,
    val localSource: EntitlementSource = EntitlementSource.NONE,
    val localVerifiedAtMillis: Long? = null,
    val cloudStatus: CloudSubStatus = CloudSubStatus.UNKNOWN,
    val cloudExpiryMillis: Long? = null,
    val cloudVerifiedAtMillis: Long? = null,
    /** 旧 `ticketkeep_pro_yearly` 订阅仍有效时为 true；兼容本地高级，**不等于**永久买断，也不自动给云。 */
    val legacyYearlyActive: Boolean = false,
) {
    val hasLocalPremium: Boolean
        get() = localStatus == LocalUnlockStatus.OWNED || legacyYearlyActive

    val canAddUnlimited: Boolean
        get() = hasLocalPremium

    val canExportPdf: Boolean
        get() = hasLocalPremium

    val canImportExportCsv: Boolean
        get() = hasLocalPremium

    val canUploadCloud: Boolean
        get() = cloudStatus == CloudSubStatus.ACTIVE ||
            cloudStatus == CloudSubStatus.CANCELED_BUT_ACTIVE ||
            cloudStatus == CloudSubStatus.IN_GRACE

    /** TODO(retention)：保留窗口 TBD — Phase 1 暂与 [canUploadCloud] 相同。 */
    val canRestoreCloud: Boolean
        get() = canUploadCloud
}

/**
 * 纯函数合并逻辑：Billing 查询失败 / UNKNOWN 不得把已拥有缓存冲成未拥有。
 */
object EntitlementMerger {

    /**
     * 合并本地买断查询结果。
     *
     * @param querySucceeded false 时保留 [cached]（含 OWNED / UNKNOWN），绝不写成 NOT_OWNED。
     * @param owned Play 确认拥有非消耗型本地解锁。
     */
    fun mergeLocalAfterBillingQuery(
        cached: LocalUnlockStatus,
        querySucceeded: Boolean,
        owned: Boolean,
    ): LocalUnlockStatus {
        if (!querySucceeded) return cached
        return if (owned) LocalUnlockStatus.OWNED else LocalUnlockStatus.NOT_OWNED
    }

    /**
     * 合并云订阅查询结果。空结果仅在查询成功时落到 EXPIRED/NOT 有效；失败保留缓存。
     */
    fun mergeCloudAfterBillingQuery(
        cached: CloudSubStatus,
        querySucceeded: Boolean,
        activeLike: Boolean,
        canceledButActive: Boolean = false,
        inGrace: Boolean = false,
        onHold: Boolean = false,
        revoked: Boolean = false,
    ): CloudSubStatus {
        if (!querySucceeded) return cached
        return when {
            revoked -> CloudSubStatus.REVOKED
            onHold -> CloudSubStatus.ON_HOLD
            inGrace -> CloudSubStatus.IN_GRACE
            canceledButActive -> CloudSubStatus.CANCELED_BUT_ACTIVE
            activeLike -> CloudSubStatus.ACTIVE
            else -> CloudSubStatus.EXPIRED
        }
    }
}
