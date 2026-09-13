package com.ticketkeep.app.ui.screens.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.repository.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val repository: TicketRepository,
) : ViewModel() {

    val isPro: StateFlow<Boolean> = repository.observeIsPro()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** MVP 占位：本地模拟开通/关闭 Pro，不接 Google Play Billing。 */
    fun setPro(enabled: Boolean) {
        viewModelScope.launch { repository.setPro(enabled) }
    }
}
