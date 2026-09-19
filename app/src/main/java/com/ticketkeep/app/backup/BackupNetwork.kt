package com.ticketkeep.app.backup

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 按 Base URL 构建 [BackupApi]；允许 cleartext（由 network_security_config 配合）。
 */
object BackupNetwork {
    fun createApi(baseUrl: String): BackupApi {
        val root = baseUrl.trim().trimEnd('/') + "/"
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(root)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackupApi::class.java)
    }
}
