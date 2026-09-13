package com.ticketkeep.app.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.data.repository.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class ListUiState(
    val tickets: List<Ticket> = emptyList(),
    val query: String = "",
    val count: Int = 0,
    val isPro: Boolean = false,
    val freeLimit: Int = TicketRepository.FREE_TICKET_LIMIT,
)

@HiltViewModel
class ListViewModel @Inject constructor(
    private val repository: TicketRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ListUiState> = combine(
        query.flatMapLatest { q -> repository.observeTickets(q) },
        query,
        repository.observeCount(),
        repository.observeIsPro(),
    ) { tickets, q, count, isPro ->
        ListUiState(
            tickets = tickets,
            query = q,
            count = count,
            isPro = isPro,
            freeLimit = TicketRepository.FREE_TICKET_LIMIT,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    suspend fun canAdd(): Boolean = repository.canAddTicket()
}
