package com.ticketkeep.app.ui.screens.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.ticketkeep.app.BuildConfig
import com.ticketkeep.app.billing.BillingConfig
import com.ticketkeep.app.billing.BillingConnectionState
import com.ticketkeep.app.billing.BillingManager
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.entitlement.EntitlementRepository
import com.ticketkeep.app.entitlement.EntitlementSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 权益购买页：本地高级版与加密云备份两套方案独立展示与调试。
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val repository: TicketRepository,
    private val entitlementRepository: EntitlementRepository,
    private val billingManager: BillingManager,
) : ViewModel() {

    val entitlement: StateFlow<EntitlementSnapshot> = entitlementRepository.snapshot

    /** 兼容旧 UI：本地高级或旧年订。 */
    val isPro: StateFlow<Boolean> = repository.observeHasLocalPremium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val hasLocalPremium: StateFlow<Boolean> = isPro

    val canUseCloud: StateFlow<Boolean> = repository.observeCanUseCloud()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val ticketCount: StateFlow<Int> = repository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val connectionState: StateFlow<BillingConnectionState> = billingManager.connectionState
    val productDetails: StateFlow<ProductDetails?> = billingManager.productDetails
    val localProductDetails: StateFlow<ProductDetails?> = billingManager.localProductDetails
    val cloudProductDetails: StateFlow<ProductDetails?> = billingManager.cloudProductDetails
    val priceFormatted: StateFlow<String?> = billingManager.priceFormatted
    val localPriceFormatted: StateFlow<String?> = billingManager.localPriceFormatted
    val cloudPriceFormatted: StateFlow<String?> = billingManager.cloudPriceFormatted
    val purchaseInProgress: StateFlow<Boolean> = billingManager.purchaseInProgress
    val errorMessage: StateFlow<String?> = billingManager.errorMessage
    val lastRestoreMessage: StateFlow<String?> = billingManager.lastRestoreMessage

    val isDebug: Boolean = BuildConfig.DEBUG

    val newProductSalesEnabled: Boolean get() = BillingConfig.enableNewProductSales

    /** Play 价优先；否则用已确认展示价 ¥39。 */
    fun resolveLocalPrice(playPrice: String?): String =
        playPrice?.takeIf { it.isNotBlank() } ?: BillingConfig.DISPLAY_PRICE_LOCAL_UNLOCK

    /** Play 价优先；否则用已确认展示价 ¥29/年。 */
    fun resolveCloudPrice(playPrice: String?): String =
        playPrice?.takeIf { it.isNotBlank() } ?: BillingConfig.DISPLAY_PRICE_CLOUD_YEARLY


    init {
        billingManager.startConnectionAndRefresh()
        billingManager.refreshProductDetails()
    }

    fun refresh() {
        billingManager.startConnectionAndRefresh()
        billingManager.refreshProductDetails()
    }

    fun purchaseLocal(activity: Activity?) {
        if (activity == null) return
        billingManager.launchPurchaseFlow(activity, BillingConfig.ProductKind.LOCAL_UNLOCK)
    }

    fun purchaseCloud(activity: Activity?) {
        if (activity == null) return
        billingManager.launchPurchaseFlow(activity, BillingConfig.ProductKind.CLOUD_YEARLY)
    }

    /** @deprecated 旧单一购买；仅在销售策略允许时走旧年订。 */
    fun purchase(activity: Activity?) {
        if (activity == null) return
        billingManager.launchPurchaseFlow(activity, BillingConfig.ProductKind.LEGACY_YEARLY)
    }

    fun restore() {
        billingManager.restorePurchases()
    }

    fun clearError() = billingManager.clearError()

    fun clearRestoreMessage() = billingManager.clearRestoreMessage()

    fun debugSetLocal(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch { repository.setDebugLocal(enabled) }
    }

    fun debugSetCloud(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch { repository.setDebugCloud(enabled) }
    }

    fun debugAllOff() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch { repository.setDebugAllOff() }
    }

    /** @deprecated 仅模拟本地高级。 */
    fun debugSetPro(enabled: Boolean) = debugSetLocal(enabled)
}
