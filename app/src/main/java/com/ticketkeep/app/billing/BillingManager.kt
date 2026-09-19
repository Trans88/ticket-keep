package com.ticketkeep.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.ticketkeep.app.data.local.ProPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Google Play Billing 管理器：订阅查询、购买、恢复与 Pro 缓存同步。
 * 权威来源 = Play 购买结果；DataStore [ProPreferences] 仅作缓存。
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proPreferences: ProPreferences,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _priceFormatted = MutableStateFlow<String?>(null)
    val priceFormatted: StateFlow<String?> = _priceFormatted.asStateFlow()

    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var billingClient: BillingClient? = null
    private var started = false

    /**
     * 启动连接并刷新一次购买/商品。可在 Application.onCreate 调用。
     * startConnection：建立与 Play 的 Billing 服务连接。
     */
    fun startConnectionAndRefresh() {
        if (started) {
            scope.launch { refreshPurchasesInternal(clearProIfNone = true) }
            return
        }
        started = true
        _connectionState.value = BillingConnectionState.CONNECTING
        val client = BillingClient.newBuilder(context)
            .setListener(this)
            // Billing 7：必须 enablePendingPurchases（即使当前仅订阅）
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .build()
        billingClient = client
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connectionState.value = BillingConnectionState.CONNECTED
                    _errorMessage.value = null
                    scope.launch {
                        queryProductDetailsInternal()
                        refreshPurchasesInternal(clearProIfNone = true)
                    }
                } else {
                    // Play 不可用时不清除本地 Pro 缓存
                    _connectionState.value = BillingConnectionState.UNAVAILABLE
                    _errorMessage.value = mapBillingError(billingResult.responseCode, billingResult.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = BillingConnectionState.DISCONNECTED
                // 断开时不改 Pro 缓存；下次可再 startConnection
            }
        })
    }

    /** endConnection：释放 Billing 服务连接。 */
    fun endConnection() {
        billingClient?.endConnection()
        billingClient = null
        started = false
        _connectionState.value = BillingConnectionState.DISCONNECTED
    }

    /** 恢复购买：重新 queryPurchases 并同步 Pro。 */
    fun restorePurchases() {
        scope.launch {
            _purchaseInProgress.value = true
            try {
                ensureConnected()
                refreshPurchasesInternal(clearProIfNone = true)
                if (_connectionState.value == BillingConnectionState.CONNECTED) {
                    val owned = hasActiveProPurchase()
                    if (!owned) {
                        _errorMessage.value = "未找到有效的 Pro 订阅"
                    } else {
                        _errorMessage.value = null
                    }
                }
            } finally {
                _purchaseInProgress.value = false
            }
        }
    }

    /** 主动查询商品详情（订阅）。 */
    fun refreshProductDetails() {
        scope.launch { queryProductDetailsInternal() }
    }

    /**
     * launchPurchaseFlow：拉起 Play 购买界面。
     * 需要 Activity；Compose 侧用 LocalContext.current as? Activity。
     */
    fun launchPurchaseFlow(activity: Activity, details: ProductDetails? = _productDetails.value) {
        scope.launch {
            val product = details ?: _productDetails.value
            if (product == null) {
                _errorMessage.value =
                    "暂未从 Google Play 获取到商品，请确认应用已上架内测且已创建订阅 ${BillingConfig.PRODUCT_ID}"
                return@launch
            }
            val offerToken = product.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
            if (offerToken.isNullOrBlank()) {
                _errorMessage.value = "订阅优惠信息不可用，请稍后重试"
                return@launch
            }
            val client = billingClient
            if (client == null || !client.isReady) {
                _errorMessage.value = "Google Play 结算服务不可用"
                return@launch
            }
            _purchaseInProgress.value = true
            _errorMessage.value = null
            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .setOfferToken(offerToken)
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()
            // launchBillingFlow：打开系统购买页
            val result = client.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _purchaseInProgress.value = false
                _errorMessage.value = mapBillingError(result.responseCode, result.debugMessage)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch {
            try {
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        if (purchases != null) {
                            handlePurchases(purchases)
                        }
                        _errorMessage.value = null
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> {
                        _errorMessage.value = null
                    }
                    else -> {
                        _errorMessage.value =
                            mapBillingError(billingResult.responseCode, billingResult.debugMessage)
                    }
                }
            } finally {
                _purchaseInProgress.value = false
            }
        }
    }

    private suspend fun ensureConnected() {
        val client = billingClient
        if (client != null && client.isReady) return
        withContext(Dispatchers.Main) {
            startConnectionAndRefresh()
        }
    }

    /** queryProductDetails：查询订阅商品详情与价格。 */
    private suspend fun queryProductDetailsInternal() = mutex.withLock {
        val client = billingClient ?: return
        if (!client.isReady) return
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingConfig.PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        // billing-ktx 挂起扩展
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _productDetails.value = null
            _priceFormatted.value = null
            _errorMessage.value = mapBillingError(
                result.billingResult.responseCode,
                result.billingResult.debugMessage,
            )
            return
        }
        val details = result.productDetailsList?.firstOrNull()
        _productDetails.value = details
        _priceFormatted.value = details?.let { formatPrice(it) }
        if (details == null) {
            _errorMessage.value =
                "暂未从 Google Play 获取到商品，请确认应用已上架内测且已创建订阅 ${BillingConfig.PRODUCT_ID}"
        }
    }

    /**
     * queryPurchases / restore：查询已拥有订阅。
     * 仅在查询成功且无有效订阅时将 Pro 置为 false；失败则保留缓存。
     */
    private suspend fun refreshPurchasesInternal(clearProIfNone: Boolean) = mutex.withLock {
        val client = billingClient ?: return
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            // 查询失败：不改 Pro 缓存
            return
        }
        val purchases = result.purchasesList
        handlePurchases(purchases)
        val owned = purchases.any { isActiveProPurchase(it) }
        syncProFromBilling(owned = owned, clearIfNone = clearProIfNone)
    }

    private suspend fun hasActiveProPurchase(): Boolean {
        val client = billingClient ?: return false
        if (!client.isReady) return false
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return false
        return result.purchasesList.any { isActiveProPurchase(it) }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (!isActiveProPurchase(purchase)) continue
            // acknowledge：未确认的购买需确认，否则会退款
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                val client = billingClient ?: continue
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                val ackResult = client.acknowledgePurchase(ackParams)
                if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    _errorMessage.value = "购买确认失败，请稍后点「恢复购买」"
                    continue
                }
            }
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                syncProFromBilling(owned = true, clearIfNone = false)
            }
        }
    }


    /**
     * 将 Play 查询结果同步到 Pro 缓存。
     * Debug 模拟开通锁定（[ProPreferences.isDebugOverride]）时跳过，避免冲掉假 Pro。
     */
    private suspend fun syncProFromBilling(owned: Boolean, clearIfNone: Boolean) {
        if (proPreferences.isDebugOverride.first()) {
            return
        }
        if (owned) {
            proPreferences.setPro(true)
        } else if (clearIfNone) {
            proPreferences.setPro(false)
        }
    }

    private fun isActiveProPurchase(purchase: Purchase): Boolean {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        return purchase.products.contains(BillingConfig.PRODUCT_ID)
    }

    private fun formatPrice(details: ProductDetails): String? {
        val phase = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
        return phase?.formattedPrice
    }

    private fun mapBillingError(code: Int, debug: String?): String {
        return when (code) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "设备上 Google Play 结算不可用（模拟器无 Play 或未登录时可正常用免费版）"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            -> "无法连接 Google Play，请检查网络后重试"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "商品暂不可用，请确认内测轨道已上架且已创建订阅 ${BillingConfig.PRODUCT_ID}"
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                "已拥有该订阅，正在同步…"
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                "网络错误，请稍后重试"
            BillingClient.BillingResponseCode.USER_CANCELED ->
                ""
            else -> debug?.takeIf { it.isNotBlank() } ?: "结算错误（$code）"
        }.ifBlank { "结算暂时不可用" }
    }
}
