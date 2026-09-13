package com.ticketkeep.app.ui.screens.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.ticketkeep.app.BuildConfig
import com.ticketkeep.app.billing.BillingConnectionState
import com.ticketkeep.app.billing.BillingManager
import com.ticketkeep.app.data.repository.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pro 墙：展示价格、发起购买/恢复，并观察本地 Pro 状态。
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val repository: TicketRepository,
    private val billingManager: BillingManager,
) : ViewModel() {

    val isPro: StateFlow<Boolean> = repository.observeIsPro()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val connectionState: StateFlow<BillingConnectionState> = billingManager.connectionState
    val productDetails: StateFlow<ProductDetails?> = billingManager.productDetails
    val priceFormatted: StateFlow<String?> = billingManager.priceFormatted
    val purchaseInProgress: StateFlow<Boolean> = billingManager.purchaseInProgress
    val errorMessage: StateFlow<String?> = billingManager.errorMessage

    val isDebug: Boolean = BuildConfig.DEBUG

    init {
        // 进入 Paywall 时再拉一次商品与购买状态
        billingManager.startConnectionAndRefresh()
        billingManager.refreshProductDetails()
    }

    fun purchase(activity: Activity?) {
        if (activity == null) {
            return
        }
        billingManager.launchPurchaseFlow(activity)
    }

    fun restore() {
        billingManager.restorePurchases()
    }

    fun clearError() = billingManager.clearError()

    /** Debug 假开关，正式路径请用 [purchase] / [restore]。 */
    fun debugSetPro(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch { repository.setPro(enabled) }
    }
}
