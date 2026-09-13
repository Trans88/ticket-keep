package com.ticketkeep.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ticketkeep.app.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application 入口：Hilt、WorkManager 配置，并在启动时刷新 Play Billing 订阅状态。
 */
@HiltAndroidApp
class TicketKeepApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()
        // 启动时连接 Play Billing 并刷新一次购买状态（权威来源 → 同步 Pro 缓存）
        billingManager.startConnectionAndRefresh()
    }

    override fun onTerminate() {
        billingManager.endConnection()
        super.onTerminate()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
