package com.ticketkeep.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.export.TicketPdfExporter
import com.ticketkeep.app.util.ImageStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情页状态：加载票证、删除、Pro 校验后导出 PDF。
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TicketRepository,
    private val imageStorage: ImageStorage,
    private val pdfExporter: TicketPdfExporter,
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

    /**
     * Pro 门禁：非 Pro → [onNeedPro]（去 Paywall）；成功 → [onSuccess] 返回缓存 PDF。
     */
    fun exportPdf(
        onNeedPro: () -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            if (!repository.observeIsPro().first()) {
                onNeedPro()
                return@launch
            }
            val current = ticket.value ?: repository.getTicket(ticketId)
            if (current == null) {
                onError("票证不存在")
                return@launch
            }
            try {
                val file = withContext(Dispatchers.IO) { pdfExporter.exportToCache(current) }
                onSuccess(file)
            } catch (e: Exception) {
                onError(e.message?.takeIf { it.isNotBlank() } ?: "导出失败")
            }
        }
    }
}
