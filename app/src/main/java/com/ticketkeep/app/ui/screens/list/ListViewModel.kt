package com.ticketkeep.app.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.export.TicketCsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 列表页 UI 状态：搜索结果、条数与 Pro/免费额度展示。
 */
data class ListUiState(
    val tickets: List<Ticket> = emptyList(),
    val query: String = "",
    val count: Int = 0,
    val isPro: Boolean = false,
    val freeLimit: Int = TicketRepository.FREE_TICKET_LIMIT,
)

/**
 * 列表页逻辑：搜索、额度判断、空态添加、Pro 校验后导出 CSV。
 */
@HiltViewModel
class ListViewModel @Inject constructor(
    private val repository: TicketRepository,
    private val csvExporter: TicketCsvExporter,
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

    /**
     * Pro 门禁：非 Pro → [onNeedPro]；成功 → [onSuccess] 返回 UTF-8 BOM CSV。
     */
    fun exportAllCsv(
        onNeedPro: () -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            if (!repository.observeIsPro().first()) {
                onNeedPro()
                return@launch
            }
            try {
                val all = withContext(Dispatchers.IO) { repository.getAllTickets() }
                val file = withContext(Dispatchers.IO) { csvExporter.exportToCache(all) }
                onSuccess(file)
            } catch (e: Exception) {
                onError(e.message?.takeIf { it.isNotBlank() } ?: "导出失败")
            }
        }
    }
}
