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
 * Pro 会员本地缓存（DataStore key `is_pro`）。
 *
 * 权威来源是 Google Play Billing 的购买/查询结果；
 * [BillingManager] 在查询成功后写入本缓存。离线或 Play 不可用时保留上次缓存，
 * 不要把本类的 [setPro] 当作正式开通路径（Debug 假开关除外）。
 */
@Singleton
class ProPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyIsPro = booleanPreferencesKey("is_pro")

    val isPro: Flow<Boolean> = context.proDataStore.data.map { prefs ->
        prefs[keyIsPro] ?: false
    }

    /**
     * 由 Billing 成功查询/购买后调用以同步缓存。
     * Debug 假开关也可调用，但正式路径必须走 Play Billing。
     */
    suspend fun setPro(enabled: Boolean) {
        context.proDataStore.edit { it[keyIsPro] = enabled }
    }
}
