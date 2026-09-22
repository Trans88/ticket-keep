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
import com.ticketkeep.app.entitlement.CloudSubStatus
import com.ticketkeep.app.entitlement.EntitlementMerger
import com.ticketkeep.app.entitlement.EntitlementRepository
import com.ticketkeep.app.entitlement.LocalUnlockStatus
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
 * Google Play Billing：分别查询 INAPP（本地买断）与 SUBS（云 / 旧年订），
 * 独立映射到 [EntitlementRepository]，互不清空。
 *
 * 规则：
 * - Play 不可用 / 查询失败 → 不清本地也不清云缓存
 * - 云查询空结果 → 仅更新云状态，**不**清本地买断
 * - PENDING 不授予任何权益
 * - [BillingConfig.enableNewProductSales]==false 时仍可恢复/查询旧商品；新 SKU 购买由 UI 禁用
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlementRepository: EntitlementRepository,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    /** 旧年订商品详情（兼容）。 */
    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _localProductDetails = MutableStateFlow<ProductDetails?>(null)
    val localProductDetails: StateFlow<ProductDetails?> = _localProductDetails.asStateFlow()

    private val _cloudProductDetails = MutableStateFlow<ProductDetails?>(null)
    val cloudProductDetails: StateFlow<ProductDetails?> = _cloudProductDetails.asStateFlow()

    private val _legacyPriceFormatted = MutableStateFlow<String?>(null)
    val priceFormatted: StateFlow<String?> = _legacyPriceFormatted.asStateFlow()

    private val _localPriceFormatted = MutableStateFlow<String?>(null)
    val localPriceFormatted: StateFlow<String?> = _localPriceFormatted.asStateFlow()

    private val _cloudPriceFormatted = MutableStateFlow<String?>(null)
    val cloudPriceFormatted: StateFlow<String?> = _cloudPriceFormatted.asStateFlow()

    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastRestoreMessage = MutableStateFlow<String?>(null)
    val lastRestoreMessage: StateFlow<String?> = _lastRestoreMessage.asStateFlow()

    private var billingClient: BillingClient? = null
    private var started = false

    fun startConnectionAndRefresh() {
        if (started) {
            scope.launch { refreshPurchasesInternal() }
            return
        }
        started = true
        _connectionState.value = BillingConnectionState.CONNECTING
        val client = BillingClient.newBuilder(context)
            .setListener(this)
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
                        refreshPurchasesInternal()
                    }
                } else {
                    // Play 不可用：不清本地也不清云
                    _connectionState.value = BillingConnectionState.UNAVAILABLE
                    _errorMessage.value =
                        mapBillingError(billingResult.responseCode, billingResult.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = BillingConnectionState.DISCONNECTED
            }
        })
    }

    fun endConnection() {
        billingClient?.endConnection()
        billingClient = null
        started = false
        _connectionState.value = BillingConnectionState.DISCONNECTED
    }

    /**
     * 恢复购买：分别查询 INAPP + SUBS，并生成说明文案（本地 / 云 / 旧年订 / 无）。
     */
    fun restorePurchases() {
        scope.launch {
            _purchaseInProgress.value = true
            try {
                ensureConnected()
                val result = refreshPurchasesInternal()
                if (_connectionState.value == BillingConnectionState.CONNECTED) {
                    _lastRestoreMessage.value = result.toMessage()
                    if (!result.anyRestored) {
                        _errorMessage.value = result.toMessage()
                    } else {
                        _errorMessage.value = null
                    }
                }
            } finally {
                _purchaseInProgress.value = false
            }
        }
    }

    fun clearRestoreMessage() {
        _lastRestoreMessage.value = null
    }

    fun refreshProductDetails() {
        scope.launch { queryProductDetailsInternal() }
    }

    /**
     * 按商品类型拉起购买。新商品在 [BillingConfig.enableNewProductSales]==false 时拒绝并提示待配置。
     */
    fun launchPurchaseFlow(activity: Activity, kind: BillingConfig.ProductKind) {
        scope.launch {
            if (!BillingConfig.salesEnabledFor(kind)) {
                _errorMessage.value = "商品待配置"
                return@launch
            }
            val product = detailsFor(kind)
            if (product == null) {
                _errorMessage.value =
                    "暂未从 Google Play 获取到商品，请稍后重试"
                return@launch
            }
            val client = billingClient
            if (client == null || !client.isReady) {
                _errorMessage.value = "Google Play 结算服务不可用"
                return@launch
            }
            _purchaseInProgress.value = true
            _errorMessage.value = null
            val builder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
            if (BillingConfig.isSubs(kind)) {
                val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
                if (offerToken.isNullOrBlank()) {
                    _purchaseInProgress.value = false
                    _errorMessage.value = "订阅优惠信息不可用，请稍后重试"
                    return@launch
                }
                builder.setOfferToken(offerToken)
            }
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(builder.build()))
                .build()
            val result = client.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _purchaseInProgress.value = false
                _errorMessage.value = mapBillingError(result.responseCode, result.debugMessage)
            }
        }
    }

    /** 兼容旧调用：默认旧年订（若销售策略允许）。 */
    fun launchPurchaseFlow(activity: Activity, details: ProductDetails? = _productDetails.value) {
        if (details != null && details.productId == BillingConfig.LOCAL_UNLOCK_INAPP) {
            launchPurchaseFlow(activity, BillingConfig.ProductKind.LOCAL_UNLOCK)
            return
        }
        if (details != null && details.productId == BillingConfig.CLOUD_BACKUP_YEARLY) {
            launchPurchaseFlow(activity, BillingConfig.ProductKind.CLOUD_YEARLY)
            return
        }
        launchPurchaseFlow(activity, BillingConfig.ProductKind.LEGACY_YEARLY)
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
                            acknowledgePurchases(purchases)
                            applyPurchasesToEntitlements(purchases, inappQueryOk = true, subsQueryOk = true)
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

    private fun detailsFor(kind: BillingConfig.ProductKind): ProductDetails? = when (kind) {
        BillingConfig.ProductKind.LOCAL_UNLOCK -> _localProductDetails.value
        BillingConfig.ProductKind.CLOUD_YEARLY -> _cloudProductDetails.value
        BillingConfig.ProductKind.LEGACY_YEARLY -> _productDetails.value
    }

    private suspend fun ensureConnected() {
        val client = billingClient
        if (client != null && client.isReady) return
        withContext(Dispatchers.Main) {
            startConnectionAndRefresh()
        }
    }

    private suspend fun queryProductDetailsInternal() = mutex.withLock {
        val client = billingClient ?: return
        if (!client.isReady) return

        // SUBS：旧年订 + 云年订（占位仍查询，失败不致命）
        val subsIds = listOf(
            BillingConfig.LEGACY_PRO_YEARLY,
            BillingConfig.CLOUD_BACKUP_YEARLY,
        )
        queryDetailsOfType(client, subsIds, BillingClient.ProductType.SUBS) { list ->
            _productDetails.value = list.find { it.productId == BillingConfig.LEGACY_PRO_YEARLY }
            _legacyPriceFormatted.value = _productDetails.value?.let { formatSubsPrice(it) }
            _cloudProductDetails.value = list.find { it.productId == BillingConfig.CLOUD_BACKUP_YEARLY }
            _cloudPriceFormatted.value = _cloudProductDetails.value?.let { formatSubsPrice(it) }
        }

        // INAPP：本地买断
        queryDetailsOfType(
            client,
            listOf(BillingConfig.LOCAL_UNLOCK_INAPP),
            BillingClient.ProductType.INAPP,
        ) { list ->
            _localProductDetails.value = list.firstOrNull()
            _localPriceFormatted.value = _localProductDetails.value?.let { formatInAppPrice(it) }
        }
    }

    private suspend fun queryDetailsOfType(
        client: BillingClient,
        ids: List<String>,
        type: String,
        onOk: (List<ProductDetails>) -> Unit,
    ) {
        val productList = ids.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(type)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return
        }
        onOk(result.productDetailsList.orEmpty())
    }

    private data class RefreshOutcome(
        val local: Boolean = false,
        val cloud: Boolean = false,
        val legacy: Boolean = false,
    ) {
        val anyRestored: Boolean get() = local || cloud || legacy
        fun toMessage(): String = when {
            local && cloud && legacy -> "已恢复：本地高级版、云备份与旧年订"
            local && cloud -> "已恢复：本地高级版与云备份"
            local && legacy -> "已恢复：本地高级版与旧年订"
            cloud && legacy -> "已恢复：云备份与旧年订"
            local -> "已恢复：本地高级版"
            cloud -> "已恢复：云备份"
            legacy -> "已恢复：旧年订（本地高级兼容）"
            else -> "未找到可恢复的购买"
        }
    }

    /**
     * 分别 queryPurchases(INAPP) 与 queryPurchases(SUBS)。
     * 任一侧失败 → 该侧不更新；另一侧仍可更新。
     */
    private suspend fun refreshPurchasesInternal(): RefreshOutcome = mutex.withLock {
        val client = billingClient ?: return RefreshOutcome()
        if (!client.isReady) return RefreshOutcome()

        val inapp = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        )
        val subs = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        )

        val inappOk = inapp.billingResult.responseCode == BillingClient.BillingResponseCode.OK
        val subsOk = subs.billingResult.responseCode == BillingClient.BillingResponseCode.OK

        if (inappOk) acknowledgePurchases(inapp.purchasesList)
        if (subsOk) acknowledgePurchases(subs.purchasesList)

        val all = buildList {
            if (inappOk) addAll(inapp.purchasesList)
            if (subsOk) addAll(subs.purchasesList)
        }
        return applyPurchasesToEntitlements(all, inappQueryOk = inappOk, subsQueryOk = subsOk)
    }

    private suspend fun applyPurchasesToEntitlements(
        purchases: List<Purchase>,
        inappQueryOk: Boolean,
        subsQueryOk: Boolean,
    ): RefreshOutcome {
        val skipLocal = entitlementRepository.isDebugLocalOverride().first()
        val skipCloud = entitlementRepository.isDebugCloudOverride().first()
        val current = entitlementRepository.snapshot.value

        val localOwned = purchases.any { isPurchasedLocalUnlock(it) }
        val legacyActive = purchases.any { isPurchasedLegacyYearly(it) }
        val cloudActive = purchases.any { isPurchasedCloudYearly(it) }

        if (!skipLocal && inappQueryOk) {
            val merged = EntitlementMerger.mergeLocalAfterBillingQuery(
                cached = current.localStatus,
                querySucceeded = true,
                owned = localOwned,
            )
            entitlementRepository.applyPlayLocalUnlock(merged == LocalUnlockStatus.OWNED)
        }
        // inapp 失败：保留本地缓存（含 UNKNOWN / OWNED）

        if (!skipLocal && subsQueryOk) {
            entitlementRepository.applyLegacyYearly(legacyActive)
        }

        if (!skipCloud && subsQueryOk) {
            val mergedCloud = EntitlementMerger.mergeCloudAfterBillingQuery(
                cached = current.cloudStatus,
                querySucceeded = true,
                activeLike = cloudActive,
            )
            if (cloudActive) {
                entitlementRepository.applyPlayCloudSub(CloudSubStatus.ACTIVE)
            } else {
                // 空云结果只更新云，不清本地
                entitlementRepository.applyPlayCloudSub(mergedCloud)
            }
        }
        // subs 失败或 Play 不可用：不清云、更不清本地

        return RefreshOutcome(
            local = localOwned,
            cloud = cloudActive,
            legacy = legacyActive,
        )
    }

    private suspend fun acknowledgePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (purchase.isAcknowledged) continue
            if (!isKnownProduct(purchase)) continue
            val client = billingClient ?: continue
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val ackResult = client.acknowledgePurchase(ackParams)
            if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _errorMessage.value = "购买确认失败，请稍后点「恢复购买」"
            }
        }
    }

    private fun isKnownProduct(purchase: Purchase): Boolean {
        val ids = purchase.products
        return BillingConfig.LOCAL_UNLOCK_INAPP in ids ||
            BillingConfig.CLOUD_BACKUP_YEARLY in ids ||
            BillingConfig.LEGACY_PRO_YEARLY in ids
    }

    /** PENDING 不授予；仅 PURCHASED。 */
    private fun isPurchasedLocalUnlock(purchase: Purchase): Boolean {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        return purchase.products.contains(BillingConfig.LOCAL_UNLOCK_INAPP)
    }

    private fun isPurchasedCloudYearly(purchase: Purchase): Boolean {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        return purchase.products.contains(BillingConfig.CLOUD_BACKUP_YEARLY)
    }

    private fun isPurchasedLegacyYearly(purchase: Purchase): Boolean {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        return purchase.products.contains(BillingConfig.LEGACY_PRO_YEARLY)
    }

    private fun formatSubsPrice(details: ProductDetails): String? {
        val phase = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
        return phase?.formattedPrice
    }

    private fun formatInAppPrice(details: ProductDetails): String? {
        return details.oneTimePurchaseOfferDetails?.formattedPrice
    }

    private fun mapBillingError(code: Int, debug: String?): String {
        return when (code) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "设备上 Google Play 结算不可用（模拟器无 Play 或未登录时可正常用免费版）"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            -> "无法连接 Google Play，请检查网络后重试"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "商品暂不可用"
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                "已拥有该商品，正在同步…"
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                "网络错误，请稍后重试"
            BillingClient.BillingResponseCode.USER_CANCELED ->
                ""
            else -> debug?.takeIf { it.isNotBlank() } ?: "结算错误（$code）"
        }.ifBlank { "结算暂时不可用" }
    }
}
