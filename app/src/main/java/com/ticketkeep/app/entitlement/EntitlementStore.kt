package com.ticketkeep.app.entitlement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.proDataStore: DataStore<Preferences> by preferencesDataStore(name = "pro_prefs")

/**
 * 权益本地缓存（DataStore）。
 *
 * 仍使用原 `pro_prefs` 存储，以便迁移旧 `is_pro` / `debug_pro_override`。
 *
 * ## Keys
 * - 旧：`is_pro`、`debug_pro_override`（只读迁移 / 兼容）
 * - 新：`local_owned`、`local_source`、`local_verified_at`
 * - 新：`cloud_status`、`cloud_expiry`、`cloud_verified_at`
 * - 新：`legacy_yearly_active`
 * - Debug：`debug_local_override`、`debug_cloud_override`
 *
 * ## 迁移规则（重要）
 * 首次读到 `is_pro==true` 且尚无任何新 key 时：将 [legacyYearlyActive] 置为 true，
 * 并标记来源为 [EntitlementSource.MIGRATION_CACHE]。
 * **这不是永久买断证明**，仅作待验证缓存/迁移提示；权威仍以 Play 查询为准。
 * Debug 模拟状态不得迁移成正式购买。
 *
 * [UNKNOWN] / 查询失败不得把已有 OWNED 冲成 NOT_OWNED。
 */
@Singleton
class EntitlementStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyIsPro = booleanPreferencesKey("is_pro")
    private val keyDebugProOverride = booleanPreferencesKey("debug_pro_override")

    private val keyLocalOwned = booleanPreferencesKey("local_owned")
    private val keyLocalSource = stringPreferencesKey("local_source")
    private val keyLocalVerifiedAt = longPreferencesKey("local_verified_at")

    private val keyCloudStatus = stringPreferencesKey("cloud_status")
    private val keyCloudExpiry = longPreferencesKey("cloud_expiry")
    private val keyCloudVerifiedAt = longPreferencesKey("cloud_verified_at")

    private val keyLegacyYearlyActive = booleanPreferencesKey("legacy_yearly_active")

    private val keyDebugLocalOverride = booleanPreferencesKey("debug_local_override")
    private val keyDebugCloudOverride = booleanPreferencesKey("debug_cloud_override")

    /** 是否已写入过任一新权益 key（用于一次性迁移判断）。 */
    private val keyMigrationDone = booleanPreferencesKey("entitlement_migration_v1")

    val snapshot: Flow<EntitlementSnapshot> = context.proDataStore.data.map { prefs ->
        mapPrefs(prefs)
    }

    /** Debug 本地模拟是否锁定（Billing 同步应跳过本地字段）。 */
    val isDebugLocalOverride: Flow<Boolean> = context.proDataStore.data.map { prefs ->
        prefs[keyDebugLocalOverride] ?: prefs[keyDebugProOverride] ?: false
    }

    /** Debug 云模拟是否锁定。 */
    val isDebugCloudOverride: Flow<Boolean> = context.proDataStore.data.map { prefs ->
        prefs[keyDebugCloudOverride] ?: false
    }

    private fun mapPrefs(prefs: Preferences): EntitlementSnapshot {
        val migrationDone = prefs[keyMigrationDone] == true
        val hasNewKeys = prefs.contains(keyLocalOwned) ||
            prefs.contains(keyCloudStatus) ||
            prefs.contains(keyLegacyYearlyActive) ||
            prefs.contains(keyDebugLocalOverride) ||
            prefs.contains(keyDebugCloudOverride)

        val oldIsPro = prefs[keyIsPro] == true
        val oldDebug = prefs[keyDebugProOverride] == true

        // 迁移提示：旧 is_pro=true 且无新 key → 当作 legacyYearlyActive 缓存，非永久买断
        val legacyFromMigration = !migrationDone && !hasNewKeys && oldIsPro && !oldDebug

        val debugLocal = prefs[keyDebugLocalOverride]
        val debugCloud = prefs[keyDebugCloudOverride]

        val localOwnedFlag = when {
            debugLocal == true -> true
            debugLocal == false -> false
            prefs.contains(keyLocalOwned) -> prefs[keyLocalOwned] == true
            legacyFromMigration -> false // 迁移走 legacy，不直接标买断
            oldDebug && oldIsPro -> true // 旧 debug Pro → 视为 debug 本地
            else -> false
        }

        val localStatus = when {
            debugLocal == true -> LocalUnlockStatus.OWNED
            debugLocal == false && prefs[keyLocalOwned] != true -> LocalUnlockStatus.NOT_OWNED
            prefs.contains(keyLocalOwned) ->
                if (prefs[keyLocalOwned] == true) LocalUnlockStatus.OWNED
                else LocalUnlockStatus.NOT_OWNED
            legacyFromMigration -> LocalUnlockStatus.UNKNOWN // 待 Play 验证
            oldDebug && oldIsPro -> LocalUnlockStatus.OWNED
            prefs.contains(keyIsPro) && !oldIsPro -> LocalUnlockStatus.NOT_OWNED
            else -> LocalUnlockStatus.UNKNOWN
        }

        val localSource = when {
            debugLocal == true || (oldDebug && oldIsPro && !prefs.contains(keyLocalSource)) ->
                EntitlementSource.DEBUG
            prefs[keyLocalSource] != null ->
                runCatching { EntitlementSource.valueOf(prefs[keyLocalSource]!!) }
                    .getOrDefault(EntitlementSource.NONE)
            legacyFromMigration -> EntitlementSource.MIGRATION_CACHE
            localOwnedFlag -> EntitlementSource.PLAY_INAPP
            else -> EntitlementSource.NONE
        }

        val cloudStatus = when {
            debugCloud == true -> CloudSubStatus.ACTIVE
            debugCloud == false -> CloudSubStatus.EXPIRED
            prefs[keyCloudStatus] != null ->
                runCatching { CloudSubStatus.valueOf(prefs[keyCloudStatus]!!) }
                    .getOrDefault(CloudSubStatus.UNKNOWN)
            else -> CloudSubStatus.UNKNOWN
        }

        val legacyYearly = when {
            prefs.contains(keyLegacyYearlyActive) -> prefs[keyLegacyYearlyActive] == true
            legacyFromMigration -> true
            else -> false
        }

        return EntitlementSnapshot(
            localStatus = localStatus,
            localSource = localSource,
            localVerifiedAtMillis = prefs[keyLocalVerifiedAt],
            cloudStatus = cloudStatus,
            cloudExpiryMillis = prefs[keyCloudExpiry],
            cloudVerifiedAtMillis = prefs[keyCloudVerifiedAt],
            legacyYearlyActive = legacyYearly,
        )
    }

    /**
     * 确保迁移标记写入；在 Repository 首次观察时调用。
     * 仅把旧 is_pro 映射为 legacy 缓存提示，**不**写成永久 local_owned。
     */
    suspend fun ensureMigration() {
        context.proDataStore.edit { prefs ->
            if (prefs[keyMigrationDone] == true) return@edit
            val hasNewKeys = prefs.contains(keyLocalOwned) ||
                prefs.contains(keyCloudStatus) ||
                prefs.contains(keyLegacyYearlyActive)
            val oldIsPro = prefs[keyIsPro] == true
            val oldDebug = prefs[keyDebugProOverride] == true
            if (!hasNewKeys && oldIsPro && !oldDebug) {
                prefs[keyLegacyYearlyActive] = true
                // 不写 local_owned=true：旧年订 ≠ 永久买断
            }
            if (!hasNewKeys && oldDebug && oldIsPro) {
                prefs[keyDebugLocalOverride] = true
                prefs[keyLocalOwned] = true
                prefs[keyLocalSource] = EntitlementSource.DEBUG.name
            }
            prefs[keyMigrationDone] = true
        }
    }

    suspend fun applyPlayLocalUnlock(owned: Boolean, verifiedAtMillis: Long = System.currentTimeMillis()) {
        context.proDataStore.edit { prefs ->
            if (prefs[keyDebugLocalOverride] == true) return@edit
            prefs[keyLocalOwned] = owned
            prefs[keyLocalSource] =
                if (owned) EntitlementSource.PLAY_INAPP.name else EntitlementSource.NONE.name
            prefs[keyLocalVerifiedAt] = verifiedAtMillis
            // 兼容旧观察者：本地买断时同步 is_pro 派生缓存（仅本地，不含云）
            prefs[keyIsPro] = owned || (prefs[keyLegacyYearlyActive] == true)
            prefs[keyMigrationDone] = true
        }
    }

    suspend fun applyPlayCloudSub(
        status: CloudSubStatus,
        expiryMillis: Long? = null,
        verifiedAtMillis: Long = System.currentTimeMillis(),
    ) {
        context.proDataStore.edit { prefs ->
            if (prefs[keyDebugCloudOverride] == true) return@edit
            prefs[keyCloudStatus] = status.name
            if (expiryMillis != null) {
                prefs[keyCloudExpiry] = expiryMillis
            } else {
                prefs.remove(keyCloudExpiry)
            }
            prefs[keyCloudVerifiedAt] = verifiedAtMillis
            prefs[keyMigrationDone] = true
        }
    }

    suspend fun applyLegacyYearly(active: Boolean, verifiedAtMillis: Long = System.currentTimeMillis()) {
        context.proDataStore.edit { prefs ->
            if (prefs[keyDebugLocalOverride] == true) return@edit
            prefs[keyLegacyYearlyActive] = active
            prefs[keyLocalVerifiedAt] = verifiedAtMillis
            val localOwned = prefs[keyLocalOwned] == true
            prefs[keyIsPro] = localOwned || active
            if (active) {
                prefs[keyLocalSource] = EntitlementSource.PLAY_SUBS_LEGACY.name
            }
            prefs[keyMigrationDone] = true
        }
    }

    /** 仅清云状态；**不得**触碰本地买断 / legacy。 */
    suspend fun clearCloudOnly() {
        context.proDataStore.edit { prefs ->
            if (prefs[keyDebugCloudOverride] == true) return@edit
            prefs[keyCloudStatus] = CloudSubStatus.EXPIRED.name
            prefs.remove(keyCloudExpiry)
            prefs[keyCloudVerifiedAt] = System.currentTimeMillis()
        }
    }

    suspend fun setDebugLocal(enabled: Boolean) {
        context.proDataStore.edit { prefs ->
            prefs[keyDebugLocalOverride] = enabled
            prefs[keyLocalOwned] = enabled
            prefs[keyLocalSource] =
                if (enabled) EntitlementSource.DEBUG.name else EntitlementSource.NONE.name
            prefs[keyLocalVerifiedAt] = System.currentTimeMillis()
            // 兼容旧 debug_pro_override
            prefs[keyDebugProOverride] = enabled
            prefs[keyIsPro] = enabled || (prefs[keyLegacyYearlyActive] == true)
            prefs[keyMigrationDone] = true
        }
    }

    suspend fun setDebugCloud(enabled: Boolean) {
        context.proDataStore.edit { prefs ->
            prefs[keyDebugCloudOverride] = enabled
            prefs[keyCloudStatus] =
                if (enabled) CloudSubStatus.ACTIVE.name else CloudSubStatus.EXPIRED.name
            prefs[keyCloudVerifiedAt] = System.currentTimeMillis()
            prefs[keyMigrationDone] = true
        }
    }

    /** Debug：全部关闭（本地 + 云 + legacy 模拟）。 */
    suspend fun setDebugAllOff() {
        context.proDataStore.edit { prefs ->
            prefs[keyDebugLocalOverride] = false
            prefs[keyDebugCloudOverride] = false
            prefs[keyDebugProOverride] = false
            prefs[keyLocalOwned] = false
            prefs[keyLocalSource] = EntitlementSource.NONE.name
            prefs[keyCloudStatus] = CloudSubStatus.EXPIRED.name
            prefs[keyLegacyYearlyActive] = false
            prefs[keyIsPro] = false
            prefs[keyMigrationDone] = true
        }
    }
}
