package com.ticketkeep.app.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ticketkeep.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 云备份会话与配置的安全存储：JWT / 邮箱 / Base URL。
 * 备份口令不在此持久化（与 JWT 分离，仅上传/恢复时内存输入）。
 */
@Singleton
class BackupAuthStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "backup_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        // 个别机型 Keystore 异常时降级，仍不写明文口令
        context.getSharedPreferences("backup_secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    private val _session = MutableStateFlow(readSession())
    val session: StateFlow<Session> = _session.asStateFlow()

    data class Session(
        val token: String? = null,
        val userId: String? = null,
        val email: String? = null,
        val baseUrl: String = DEFAULT_BASE_URL,
    ) {
        val isLoggedIn: Boolean get() = !token.isNullOrBlank()
    }

    fun defaultBaseUrl(): String {
        val fromBuild = runCatching { BuildConfig.BACKUP_BASE_URL }.getOrNull()
        return fromBuild?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
    }

    fun currentBaseUrl(): String {
        val override = prefs.getString(KEY_BASE_URL, null)?.trim().orEmpty()
        return override.ifBlank { defaultBaseUrl() }.trimEnd('/')
    }

    fun setBaseUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        prefs.edit().putString(KEY_BASE_URL, cleaned.ifBlank { null }).apply()
        _session.value = _session.value.copy(baseUrl = currentBaseUrl())
    }

    fun saveAuth(token: String, userId: String, email: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .apply()
        _session.value = Session(token, userId, email, currentBaseUrl())
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .apply()
        _session.value = Session(baseUrl = currentBaseUrl())
    }

    fun tokenOrNull(): String? = prefs.getString(KEY_TOKEN, null)

    private fun readSession(): Session = Session(
        token = prefs.getString(KEY_TOKEN, null),
        userId = prefs.getString(KEY_USER_ID, null),
        email = prefs.getString(KEY_EMAIL, null),
        baseUrl = currentBaseUrl(),
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.trans88.cn"
        private const val KEY_TOKEN = "jwt"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_BASE_URL = "base_url"
    }
}
