package com.ticketkeep.app.backup

import android.content.Context
import com.ticketkeep.app.BuildConfig
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.data.repository.TicketRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException

/**
 * 云备份编排：Pro 门禁、打包加密上传、下载解密确认导入。
 * 恢复策略：一律按新票证插入（不清空本地）；用户确认后才写入。
 */
@Singleton
class CloudBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ticketRepository: TicketRepository,
    private val authStore: BackupAuthStore,
) {
    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val message: String) : Result<Nothing>()
    }

    data class RestorePreview(
        val tickets: List<Ticket>,
        val imageBytesByRelPath: Map<String, ByteArray>,
        val skippedRows: Int,
        val warnings: List<String>,
    )

    private fun api(): BackupApi = BackupNetwork.createApi(authStore.currentBaseUrl())

    private fun bearer(): String {
        val t = authStore.tokenOrNull() ?: throw IllegalStateException("未登录")
        return "Bearer $t"
    }

    suspend fun requirePro(): Boolean = ticketRepository.observeIsPro().first()

    suspend fun register(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val res = api().register(AuthRequest(email.trim(), password))
            authStore.saveAuth(res.token, res.userId, email.trim())
            Result.Ok(Unit)
        } catch (e: Exception) {
            Result.Err(mapError(e, "注册失败"))
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val res = api().login(AuthRequest(email.trim(), password))
            authStore.saveAuth(res.token, res.userId, email.trim())
            Result.Ok(Unit)
        } catch (e: Exception) {
            Result.Err(mapError(e, "登录失败"))
        }
    }

    fun logout() = authStore.clearAuth()

    suspend fun listBackups(): Result<List<BackupListItem>> = withContext(Dispatchers.IO) {
        try {
            Result.Ok(api().listBackups(bearer()))
        } catch (e: Exception) {
            Result.Err(mapError(e, "获取备份列表失败"))
        }
    }

    suspend fun uploadEncryptedBackup(passphrase: CharArray, deviceLabel: String? = null): Result<BackupListItem> =
        withContext(Dispatchers.IO) {
            try {
                if (!requirePro()) return@withContext Result.Err("需要 Pro 才能使用云备份")
                val tickets = ticketRepository.getAllTickets()
                when (val packed = BackupPackager.pack(tickets, BuildConfig.VERSION_NAME)) {
                    is BackupPackager.PackOutcome.Error -> return@withContext Result.Err(packed.message)
                    is BackupPackager.PackOutcome.Ok -> {
                        when (val enc = BackupCrypto.encrypt(packed.packed.zipBytes, passphrase)) {
                            is BackupCrypto.Outcome.Error -> return@withContext Result.Err(enc.message)
                            is BackupCrypto.Outcome.Ok -> {
                                val metaJson = JSONObject()
                                    .put("format", "tkbk1")
                                    .put("ticketCount", packed.packed.ticketCount)
                                    .put("imageCount", packed.packed.imageCount)
                                    .put("appVersion", BuildConfig.VERSION_NAME)
                                    .put("device", deviceLabel ?: android.os.Build.MODEL)
                                    .toString()
                                val fileBody = enc.bytes.toRequestBody("application/octet-stream".toMediaType())
                                val part = MultipartBody.Part.createFormData(
                                    "file",
                                    "ticketkeep-backup.tkbk",
                                    fileBody,
                                )
                                val metaBody = metaJson.toRequestBody("text/plain".toMediaType())
                                val item = api().uploadBackup(bearer(), part, metaBody)
                                Result.Ok(item)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Result.Err(mapError(e, "上传失败"))
            }
        }

    suspend fun prepareRestore(backupId: String, passphrase: CharArray): Result<RestorePreview> =
        withContext(Dispatchers.IO) {
            try {
                if (!requirePro()) return@withContext Result.Err("需要 Pro 才能恢复云备份")
                val body = api().downloadBackup(bearer(), backupId)
                val blob = body.bytes()
                when (val dec = BackupCrypto.decrypt(blob, passphrase)) {
                    is BackupCrypto.Outcome.Error -> return@withContext Result.Err(dec.message)
                    is BackupCrypto.Outcome.Ok -> when (val up = BackupPackager.unpack(dec.bytes)) {
                        is BackupPackager.UnpackOutcome.Error -> Result.Err(up.message)
                        is BackupPackager.UnpackOutcome.Ok -> Result.Ok(
                            RestorePreview(
                                tickets = up.unpacked.tickets,
                                imageBytesByRelPath = up.unpacked.images,
                                skippedRows = up.unpacked.skippedRows,
                                warnings = up.unpacked.warnings,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                Result.Err(mapError(e, "下载/解密失败"))
            }
        }

    /**
     * 确认导入：落盘图片后按新票证插入，不删除本地已有数据。
     */
    suspend fun confirmRestore(preview: RestorePreview): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!requirePro()) return@withContext Result.Err("需要 Pro 才能恢复云备份")
            val imagesDir = File(context.filesDir, "ticket_images").also { if (!it.exists()) it.mkdirs() }
            var inserted = 0
            for (t in preview.tickets) {
                val rel = t.imagePath
                val newPath = if (!rel.isNullOrBlank() && preview.imageBytesByRelPath.containsKey(rel)) {
                    val dest = File(imagesDir, "${UUID.randomUUID()}.jpg")
                    dest.writeBytes(preview.imageBytesByRelPath.getValue(rel))
                    dest.absolutePath
                } else {
                    null
                }
                ticketRepository.saveTicket(
                    t.copy(id = 0L, imagePath = newPath),
                )
                inserted++
            }
            Result.Ok(inserted)
        } catch (e: Exception) {
            Result.Err(e.message?.takeIf { it.isNotBlank() } ?: "导入失败")
        }
    }

    suspend fun deleteBackup(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            api().deleteBackup(bearer(), id)
            Result.Ok(Unit)
        } catch (e: Exception) {
            Result.Err(mapError(e, "删除失败"))
        }
    }

    private fun mapError(e: Exception, fallback: String): String {
        when (e) {
            is HttpException -> {
                val code = e.code()
                val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                val serverMsg = raw?.let {
                    runCatching { JSONObject(it).optString("message") }.getOrNull()
                        ?.takeIf { m -> m.isNotBlank() }
                }
                return when (code) {
                    401 -> serverMsg ?: "登录已失效，请重新登录"
                    409 -> serverMsg ?: "该邮箱已注册"
                    413 -> serverMsg ?: "备份文件过大（上限 50MB）"
                    429 -> serverMsg ?: "请求过于频繁，请稍后再试"
                    else -> {
                        if (raw?.contains("backup_limit") == true || serverMsg?.contains("上限") == true) {
                            serverMsg ?: "已达备份数量上限（每用户 20 个）"
                        } else {
                            serverMsg ?: "$fallback（HTTP $code）"
                        }
                    }
                }
            }
            is java.net.UnknownHostException, is java.net.ConnectException ->
                return "网络不可用，请检查网络或 Base URL"
            is java.net.SocketTimeoutException -> return "连接超时，请稍后重试"
            is IllegalStateException -> return e.message ?: fallback
            else -> return e.message?.takeIf { it.isNotBlank() } ?: fallback
        }
    }
}
