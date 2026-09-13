package com.ticketkeep.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.util.ImageStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TicketRepository,
    private val imageStorage: ImageStorage,
) : ViewModel() {

    private val ticketId: Long = checkNotNull(savedStateHandle["ticketId"])

    val ticket: StateFlow<Ticket?> = repository.observeTicket(ticketId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = ticket.value
            imageStorage.deleteIfExists(current?.imagePath)
            repository.deleteTicket(ticketId)
            onDone()
        }
    }
}
