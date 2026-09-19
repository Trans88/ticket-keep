package com.ticketkeep.app.backup

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Retrofit 接口：健康检查、注册/登录、备份 CRUD（密文 multipart）。
 */
interface BackupApi {
    @POST("health")
    suspend fun health(): HealthResponse

    @POST("auth/register")
    suspend fun register(@Body body: AuthRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: AuthRequest): AuthResponse

    @GET("backups")
    suspend fun listBackups(
        @Header("Authorization") authorization: String,
    ): List<BackupListItem>

    @Multipart
    @POST("backups")
    suspend fun uploadBackup(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("meta") meta: RequestBody?,
    ): BackupListItem

    @GET("backups/{id}")
    suspend fun downloadBackup(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): ResponseBody

    @DELETE("backups/{id}")
    suspend fun deleteBackup(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): OkResponse
}
