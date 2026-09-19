package com.ticketkeep.app.backup

import com.google.gson.JsonElement

/**
 * 云备份 REST DTO，与已验收后端契约对齐。
 */
data class AuthRequest(val email: String, val password: String)

data class AuthResponse(val token: String, val userId: String)

data class HealthResponse(val ok: Boolean = false)

data class BackupListItem(
    val id: String,
    val createdAt: String? = null,
    val size: Long = 0,
    val meta: JsonElement? = null,
)

data class ApiErrorBody(
    val error: String? = null,
    val message: String? = null,
)


data class OkResponse(val ok: Boolean = false, val id: String? = null)
