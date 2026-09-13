package com.ticketkeep.app.ui.screens.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.ocr.MlKitOcrHelper
import com.ticketkeep.app.ocr.OcrParseResult
import com.ticketkeep.app.util.DateFormats
import com.ticketkeep.app.util.ImageStorage
import com.ticketkeep.app.util.MoneyFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** 本次新建是否已经跑过 OCR（用于展示「识别原文」区块） */
    val ocrAttempted: Boolean = false,
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
                ocrAttempted = ticket.ocrRawText.isNotBlank(),
                useManualWarrantyEnd = ticket.warrantyMonths == null && ticket.warrantyEndEpochDay != null,
            )
        }
    }

    /**
     * 新建带图：先把 URI 落盘（消费一次性 content 流），再对本地文件做 OCR。
     * 切勿 persist 后再 recognize(原 uri)——Photo Picker 流常只能读一次，会导致 ML Kit 得到空图/空字。
     */
    private suspend fun ingestNewImage(uri: Uri) {
        _uiState.update {
            it.copy(isOcrRunning = true, errorMessage = null, ocrAttempted = false)
        }
        val path = imageStorage.persistImage(uri)
        if (path.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    isOcrRunning = false,
                    ocrAttempted = true,
                    purchaseDate = LocalDate.now(),
                    warrantyMonthsText = "12",
                    warrantyEndDate = LocalDate.now().plusMonths(12),
                    errorMessage = "无法读取图片。请换一张图，或用「拍照」重试。",
                )
            }
            return
        }
        val parse = try {
            ocrHelper.recognizeFile(path)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isOcrRunning = false,
                    ocrAttempted = true,
                    imagePath = path,
                    purchaseDate = LocalDate.now(),
                    warrantyMonthsText = "12",
                    warrantyEndDate = LocalDate.now().plusMonths(12),
                    errorMessage = "OCR 失败：${e.message ?: "未知错误"}。请对照照片手填，或换更清晰的图重试。",
                )
            }
            return
        }
        applyOcrResult(path, parse)
    }

    private fun applyOcrResult(path: String?, parse: OcrParseResult) {
        val rawBlank = parse.rawText.isBlank()
        val purchase = parse.purchaseDateEpochDay?.let(DateFormats::epochDayToLocalDate)
        val endFromOcr = parse.warrantyEndEpochDay?.let(DateFormats::epochDayToLocalDate)
        val months = parse.warrantyMonths
            ?: if (parse.isWarrantyForm) null else 12

        val useManualEnd = endFromOcr != null
        val purchaseOrToday = purchase ?: LocalDate.now()
        val endDate = when {
            endFromOcr != null -> endFromOcr
            months != null -> purchaseOrToday.plusMonths(months.toLong())
            else -> null
        }

        val hint = when {
            rawBlank ->
                "未能识别出文字（图片已保存）。请确认重装了最新包；仍失败可换拍照或手填。"
            parse.merchantName == null && purchase == null && months == null && endFromOcr == null ->
                "已得到识别原文，但未能自动解析字段。请对照下方原文手改。"
            parse.isWarrantyForm && purchase == null ->
                "已按保修单解析部分字段；购买日期未识别到，已暂用今天，请核对原文。"
            else -> null
        }

        _uiState.update {
            it.copy(
                isOcrRunning = false,
                ocrAttempted = true,
                imagePath = path,
                merchantName = parse.merchantName.orEmpty(),
                amountText = MoneyFormats.centsToYuanString(parse.amountCents),
                purchaseDate = purchaseOrToday,
                warrantyMonthsText = months?.toString().orEmpty(),
                warrantyEndDate = endDate,
                useManualWarrantyEnd = useManualEnd,
                note = parse.note.orEmpty(),
                ocrRawText = parse.rawText,
                errorMessage = hint,
            )
        }
    }

    fun updateMerchant(value: String) = _uiState.update { it.copy(merchantName = value) }
    fun updateAmount(value: String) = _uiState.update { it.copy(amountText = value) }
    fun updateNote(value: String) = _uiState.update { it.copy(note = value) }

    fun updatePurchaseDate(date: LocalDate) {
        _uiState.update { state ->
            val end = if (!state.useManualWarrantyEnd) {
                val m = state.warrantyMonthsText.toIntOrNull() ?: 0
                date.plusMonths(m.toLong())
            } else {
                state.warrantyEndDate
            }
            state.copy(purchaseDate = date, warrantyEndDate = end)
        }
    }

    fun updateWarrantyMonths(text: String) {
        _uiState.update { state ->
            val monthsDigits = text.filter { it.isDigit() }
            val end = if (!state.useManualWarrantyEnd) {
                val m = monthsDigits.toIntOrNull() ?: 0
                state.purchaseDate?.plusMonths(m.toLong())
            } else {
                state.warrantyEndDate
            }
            state.copy(warrantyMonthsText = monthsDigits, warrantyEndDate = end)
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
