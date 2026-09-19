package com.ticketkeep.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.proDataStore: DataStore<Preferences> by preferencesDataStore(name = "pro_prefs")

/**
 * Pro 会员本地缓存（DataStore）。
 *
 * Keys:
 * - `is_pro`：当前是否按 Pro 对待（UI / 门禁观察此值）
 * - `debug_pro_override`：Debug 模拟开通锁定；为 true 时 Billing 同步不得改写 `is_pro`
 *
 * 正式权威来源仍是 Google Play Billing；[setPro] 仅供 Billing 正式路径。
 * Debug 假开关必须走 [setDebugPro]，避免 refreshPurchases 把模拟状态冲掉。
 */
@Singleton
class ProPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyIsPro = booleanPreferencesKey("is_pro")
    private val keyDebugOverride = booleanPreferencesKey("debug_pro_override")

    val isPro: Flow<Boolean> = context.proDataStore.data.map { prefs ->
        prefs[keyIsPro] ?: false
    }

    /** Debug 模拟开通是否锁定中（Billing 同步应跳过）。 */
    val isDebugOverride: Flow<Boolean> = context.proDataStore.data.map { prefs ->
        prefs[keyDebugOverride] ?: false
    }

    /**
     * 由 Billing 成功查询/购买后调用以同步缓存。
     * 正式路径必须走 Play Billing；Debug 假开关请用 [setDebugPro]。
     */
    suspend fun setPro(enabled: Boolean) {
        context.proDataStore.edit { it[keyIsPro] = enabled }
    }

    /**
     * Debug 专用：同时写入 `is_pro` 与 `debug_pro_override`。
     * enabled=true → 锁定模拟 Pro；enabled=false → 解除锁定并回到免费。
     */
    suspend fun setDebugPro(enabled: Boolean) {
        context.proDataStore.edit { prefs ->
            prefs[keyIsPro] = enabled
            prefs[keyDebugOverride] = enabled
        }
    }
}
