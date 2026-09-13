package com.ticketkeep.app.ui.screens.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.ocr.MlKitOcrHelper
import com.ticketkeep.app.util.DateFormats
import com.ticketkeep.app.util.ImageStorage
import com.ticketkeep.app.util.MoneyFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditUiState(
    val ticketId: Long = 0L,
    val merchantName: String = "",
    val amountText: String = "",
    val purchaseDate: LocalDate? = null,
    val warrantyMonthsText: String = "12",
    val warrantyEndDate: LocalDate? = null,
    val note: String = "",
    val imagePath: String? = null,
    val ocrRawText: String = "",
    val isOcrRunning: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val useManualWarrantyEnd: Boolean = false,
)

@HiltViewModel
class EditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TicketRepository,
    private val ocrHelper: MlKitOcrHelper,
    private val imageStorage: ImageStorage,
) : ViewModel() {

    private val argTicketId: Long = savedStateHandle["ticketId"] ?: -1L
    private val argImageUri: String = savedStateHandle["imageUri"] ?: ""

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    init {
        if (argTicketId > 0) {
            viewModelScope.launch { loadExisting(argTicketId) }
        } else if (argImageUri.isNotBlank()) {
            viewModelScope.launch { ingestNewImage(Uri.parse(argImageUri)) }
        } else {
            _uiState.update {
                it.copy(
                    purchaseDate = LocalDate.now(),
                    warrantyEndDate = LocalDate.now().plusMonths(12),
                )
            }
        }
    }

    private suspend fun loadExisting(id: Long) {
        val ticket = repository.getTicket(id) ?: return
        _uiState.update {
            it.copy(
                ticketId = ticket.id,
                merchantName = ticket.merchantName,
                amountText = MoneyFormats.centsToYuanString(ticket.amountCents),
                purchaseDate = ticket.purchaseDateEpochDay?.let(DateFormats::epochDayToLocalDate),
                warrantyMonthsText = ticket.warrantyMonths?.toString().orEmpty(),
                warrantyEndDate = ticket.warrantyEndEpochDay?.let(DateFormats::epochDayToLocalDate),
                note = ticket.note,
                imagePath = ticket.imagePath,
                ocrRawText = ticket.ocrRawText,
                useManualWarrantyEnd = ticket.warrantyMonths == null && ticket.warrantyEndEpochDay != null,
            )
        }
    }

    private suspend fun ingestNewImage(uri: Uri) {
        _uiState.update { it.copy(isOcrRunning = true, errorMessage = null) }
        val path = imageStorage.persistImage(uri)
        val parse = try {
            ocrHelper.recognize(uri)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isOcrRunning = false,
                    imagePath = path,
                    errorMessage = "OCR 失败：${e.message ?: "未知错误"}，请手填",
                )
            }
            return
        }
        val purchase = parse.purchaseDateEpochDay?.let(DateFormats::epochDayToLocalDate) ?: LocalDate.now()
        val months = 12
        _uiState.update {
            it.copy(
                isOcrRunning = false,
                imagePath = path,
                merchantName = parse.merchantName.orEmpty(),
                amountText = MoneyFormats.centsToYuanString(parse.amountCents),
                purchaseDate = purchase,
                warrantyMonthsText = months.toString(),
                warrantyEndDate = purchase.plusMonths(months.toLong()),
                ocrRawText = parse.rawText,
            )
        }
    }

    fun updateMerchant(value: String) = _uiState.update { it.copy(merchantName = value) }
    fun updateAmount(value: String) = _uiState.update { it.copy(amountText = value) }
    fun updateNote(value: String) = _uiState.update { it.copy(note = value) }

    fun updatePurchaseDate(date: LocalDate) {
        _uiState.update { state ->
            val end = if (!state.useManualWarrantyEnd) {
                val months = state.warrantyMonthsText.toIntOrNull() ?: 0
                date.plusMonths(months.toLong())
            } else state.warrantyEndDate
            state.copy(purchaseDate = date, warrantyEndDate = end)
        }
    }

    fun updateWarrantyMonths(text: String) {
        _uiState.update { state ->
            val months = text.filter { it.isDigit() }
            val end = if (!state.useManualWarrantyEnd) {
                val m = months.toIntOrNull() ?: 0
                state.purchaseDate?.plusMonths(m.toLong())
            } else state.warrantyEndDate
            state.copy(warrantyMonthsText = months, warrantyEndDate = end)
        }
    }

    fun updateWarrantyEnd(date: LocalDate) {
        _uiState.update { it.copy(warrantyEndDate = date, useManualWarrantyEnd = true) }
    }

    fun toggleManualWarrantyEnd(manual: Boolean) {
        _uiState.update { state ->
            if (!manual) {
                val m = state.warrantyMonthsText.toIntOrNull() ?: 0
                state.copy(
                    useManualWarrantyEnd = false,
                    warrantyEndDate = state.purchaseDate?.plusMonths(m.toLong()),
                )
            } else {
                state.copy(useManualWarrantyEnd = true)
            }
        }
    }

    fun save(onSaved: (Long) -> Unit, onNeedPro: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.ticketId == 0L && !repository.canAddTicket()) {
                onNeedPro()
                return@launch
            }
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val ticket = Ticket(
                id = state.ticketId,
                merchantName = state.merchantName.trim(),
                amountCents = MoneyFormats.parseYuanToCents(state.amountText),
                purchaseDateEpochDay = state.purchaseDate?.toEpochDay(),
                warrantyMonths = state.warrantyMonthsText.toIntOrNull(),
                warrantyEndEpochDay = state.warrantyEndDate?.toEpochDay(),
                note = state.note.trim(),
                imagePath = state.imagePath,
                ocrRawText = state.ocrRawText,
            )
            val id = repository.saveTicket(ticket)
            _uiState.update { it.copy(isSaving = false, ticketId = id) }
            onSaved(id)
        }
    }
}
