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

@Singleton
class ProPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyIsPro = booleanPreferencesKey("is_pro")

    val isPro: Flow<Boolean> = context.proDataStore.data.map { prefs ->
        prefs[keyIsPro] ?: false
    }

    /**
     * MVP 占位：本地开关模拟 Pro，不接真实 Billing。
     */
    suspend fun setPro(enabled: Boolean) {
        context.proDataStore.edit { it[keyIsPro] = enabled }
    }
}
