package com.ticketkeep.app.ui.screens.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.ocr.MlKitOcrHelper
import com.ticketkeep.app.ocr.OcrParseResult
import com.ticketkeep.app.util.DateBounds
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

/**
 * 编辑页 UI 状态：表单字段、图片路径、OCR 进行中与提示。
 * 购买日可为 null（OCR 未识别时不静默填「今天」）。
 */
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
    /** 本次是否已经跑过 OCR（用于展示「识别原文」与「重新识别」） */
    val ocrAttempted: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val useManualWarrantyEnd: Boolean = false,
)

/**
 * 编辑/新建页 ViewModel：OCR 填表、重新识别、手改、保存到 Repository。
 * 受免费条数上限约束；超限应引导 Pro。
 */
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
                ocrAttempted = ticket.ocrRawText.isNotBlank() || !ticket.imagePath.isNullOrBlank(),
                useManualWarrantyEnd = ticket.warrantyMonths == null && ticket.warrantyEndEpochDay != null,
            )
        }
    }

    /**
     * 新建带图：先把 URI 落盘（消费一次性 content 流），再对本地文件做 OCR。
     * 切勿 persist 后再 recognize(原 uri)——Photo Picker 流常只能读一次。
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
                    errorMessage = "OCR 失败：${e.message ?: "未知错误"}。请对照照片手填。",
                )
            }
            return
        }
        applyOcrResult(path, parse, preserveHandFields = false)
    }

    /**
     * 对当前已落盘 [EditUiState.imagePath] 重新跑 ML Kit + 解析。
     * 不使用 content URI；OCR 进行中禁用按钮；表单字段仍可手改。
     */
    fun rerecognize() {
        val path = _uiState.value.imagePath
        if (path.isNullOrBlank() || _uiState.value.isOcrRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrRunning = true, errorMessage = null) }
            val parse = try {
                ocrHelper.recognizeFile(path)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isOcrRunning = false,
                        ocrAttempted = true,
                        errorMessage = "重新识别失败：${e.message ?: "未知错误"}。请手填。",
                    )
                }
                return@launch
            }
            applyOcrResult(path, parse, preserveHandFields = false)
        }
    }

    /**
     * 将 OCR 结果写入表单。购买日未识别时不静默填「今天」，并给出明确提示。
     * 原文为空或关键字段全空时，仅提示手填或重新识别，不展示拍摄技巧类文案。
     */
    private fun applyOcrResult(
        path: String?,
        parse: OcrParseResult,
        preserveHandFields: Boolean,
    ) {
        val rawBlank = parse.rawText.isBlank()
        val purchase = parse.purchaseDateEpochDay?.let(DateFormats::epochDayToLocalDate)
        val endFromOcr = parse.warrantyEndEpochDay?.let(DateFormats::epochDayToLocalDate)
        val months = parse.warrantyMonths
            ?: if (parse.isWarrantyForm) null else 12

        val keyFieldsEmpty = parse.merchantName.isNullOrBlank() &&
            parse.amountCents == null &&
            purchase == null &&
            months == null &&
            endFromOcr == null &&
            parse.note.isNullOrBlank()

        val useManualEnd = endFromOcr != null
        val endDate = when {
            endFromOcr != null -> endFromOcr
            months != null && purchase != null -> purchase.plusMonths(months.toLong())
            else -> null
        }

        val hint = when {
            rawBlank || keyFieldsEmpty ->
                // 空结果时只提示手填/重试，避免拍摄技巧类文案
                if (rawBlank) {
                    "未能识别出文字。请手填下方字段，或点击「重新识别」。"
                } else {
                    "已得到识别原文，但未能自动解析字段。请对照原文手改。"
                }
            purchase == null ->
                "购买日期未识别到，请手动设置（不会自动填今天）。"
            else -> null
        }

        _uiState.update { state ->
            val base = if (preserveHandFields) state else state
            base.copy(
                isOcrRunning = false,
                ocrAttempted = true,
                imagePath = path ?: state.imagePath,
                merchantName = parse.merchantName.orEmpty(),
                amountText = MoneyFormats.centsToYuanString(parse.amountCents),
                purchaseDate = purchase, // 可为 null：不静默 today
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
        if (!DateBounds.isAllowed(date)) {
            _uiState.update { it.copy(errorMessage = DateBounds.OUT_OF_RANGE_MESSAGE) }
            return
        }
        _uiState.update { state ->
            val end = if (!state.useManualWarrantyEnd) {
                val m = state.warrantyMonthsText.toIntOrNull() ?: 0
                date.plusMonths(m.toLong())
            } else {
                state.warrantyEndDate
            }
            state.copy(purchaseDate = date, warrantyEndDate = end, errorMessage = null)
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
        if (!DateBounds.isAllowed(date)) {
            _uiState.update { it.copy(errorMessage = DateBounds.OUT_OF_RANGE_MESSAGE) }
            return
        }
        _uiState.update {
            it.copy(warrantyEndDate = date, useManualWarrantyEnd = true, errorMessage = null)
        }
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
