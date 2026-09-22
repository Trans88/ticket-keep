package com.ticketkeep.app.ui.screens.batch

import android.content.Context
import android.content.Intent
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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 批量核对页 UI 状态。
 */
data class BatchReviewUiState(
    val drafts: List<BatchDraftItem> = emptyList(),
    val ocrDone: Int = 0,
    val ocrTotal: Int = 0,
    val isSaving: Boolean = false,
    val remainingSlots: Int = TicketRepository.FREE_TICKET_LIMIT,
    val hasLocalPremium: Boolean = false,
    val message: String? = null,
) {
    val progressLabel: String
        get() = "已处理 $ocrDone / $ocrTotal"

    val selectedUnsaved: List<BatchDraftItem>
        get() = drafts.filter { it.selected && it.savedTicketId == null }

    val hasUnsavedEdits: Boolean
        get() = drafts.any {
            it.savedTicketId == null && (it.userEdited || it.ocrStatus != BatchOcrStatus.PENDING)
        }
}

/**
 * 批量核对 ViewModel：顺序/限流 OCR、字段编辑、配额感知保存。
 * 落盘路径写入 [SavedStateHandle]，进程死后可恢复未保存草稿缩略图与字段。
 */
@HiltViewModel
class BatchReviewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val session: BatchImportSession,
    private val repository: TicketRepository,
    private val ocrHelper: MlKitOcrHelper,
    private val imageStorage: ImageStorage,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchReviewUiState())
    val uiState: StateFlow<BatchReviewUiState> = _uiState.asStateFlow()

    private val ocrSemaphore = Semaphore(MAX_OCR_CONCURRENCY)
    private var ocrJob: Job? = null

    init {
        viewModelScope.launch { refreshQuota() }
        val restored = restoreFromSavedState()
        if (restored != null) {
            _uiState.update {
                it.copy(
                    drafts = restored,
                    ocrTotal = restored.size,
                    ocrDone = restored.count {
                        it.ocrStatus == BatchOcrStatus.SUCCESS || it.ocrStatus == BatchOcrStatus.FAILED
                    },
                )
            }
            // 仅对仍 PENDING 且已有本地路径的项继续 OCR
            startOcrQueue(restored.filter { it.ocrStatus == BatchOcrStatus.PENDING && !it.localImagePath.isNullOrBlank() }.map { it.id })
        } else {
            bootstrapFromSession()
        }
    }

    private fun bootstrapFromSession() {
        val uris = session.consume()
        if (uris.isEmpty()) {
            _uiState.update { it.copy(message = "没有可导入的图片") }
            return
        }
        val drafts = uris.map { uri ->
            BatchDraftItem(sourceUri = uri.toString(), ocrStatus = BatchOcrStatus.PENDING)
        }
        persistDraftSnapshot(drafts)
        _uiState.update {
            it.copy(drafts = drafts, ocrTotal = drafts.size, ocrDone = 0)
        }
        viewModelScope.launch {
            drafts.forEach { draft ->
                takePersistableBestEffort(Uri.parse(draft.sourceUri))
            }
            // 先落盘再 OCR，路径写入 SavedState
            val withPaths = drafts.map { draft ->
                val path = withContext(Dispatchers.IO) {
                    imageStorage.persistImage(Uri.parse(draft.sourceUri))
                }
                draft.copy(
                    localImagePath = path,
                    ocrStatus = if (path.isNullOrBlank()) BatchOcrStatus.FAILED else BatchOcrStatus.PENDING,
                    errorMessage = if (path.isNullOrBlank()) "无法读取图片" else null,
                )
            }
            updateDrafts(withPaths)
            persistDraftSnapshot(withPaths)
            val readyIds = withPaths.filter { it.ocrStatus == BatchOcrStatus.PENDING }.map { it.id }
            startOcrQueue(readyIds)
            // 已失败的计入进度
            val failed = withPaths.count { it.ocrStatus == BatchOcrStatus.FAILED }
            if (failed > 0) {
                _uiState.update { it.copy(ocrDone = it.ocrDone + failed) }
            }
        }
    }

    private fun startOcrQueue(draftIds: List<String>) {
        if (draftIds.isEmpty()) return
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            // 并发上限 2：用 semaphore；仍按 id 列表启动协程
            draftIds.map { id ->
                launch {
                    ocrSemaphore.withPermit { runOcrFor(id) }
                }
            }.forEach { it.join() }
        }
    }

    private suspend fun runOcrFor(draftId: String) {
        val draft = _uiState.value.drafts.find { it.id == draftId } ?: return
        if (draft.savedTicketId != null) return
        val path = draft.localImagePath
        if (path.isNullOrBlank()) {
            patchDraft(draftId) {
                it.copy(ocrStatus = BatchOcrStatus.FAILED, errorMessage = "无法读取图片")
            }
            bumpOcrDone()
            return
        }
        patchDraft(draftId) { it.copy(ocrStatus = BatchOcrStatus.RUNNING, errorMessage = null) }
        val parse = try {
            ocrHelper.recognizeFile(path)
        } catch (e: Exception) {
            patchDraft(draftId) {
                it.copy(
                    ocrStatus = BatchOcrStatus.FAILED,
                    errorMessage = "OCR 失败：${e.message ?: "未知错误"}。可重试或手填。",
                )
            }
            bumpOcrDone()
            return
        }
        applyOcrResult(draftId, path, parse)
        bumpOcrDone()
    }

    private fun bumpOcrDone() {
        _uiState.update { state ->
            val done = state.drafts.count {
                it.ocrStatus == BatchOcrStatus.SUCCESS || it.ocrStatus == BatchOcrStatus.FAILED
            }
            state.copy(ocrDone = done)
        }
        persistDraftSnapshot(_uiState.value.drafts)
    }

    /**
     * 与 [com.ticketkeep.app.ui.screens.edit.EditViewModel] 相同的 OCR → 字段映射。
     */
    private fun applyOcrResult(draftId: String, path: String, parse: OcrParseResult) {
        val purchase = parse.purchaseDateEpochDay?.let(DateFormats::epochDayToLocalDate)
        val endFromOcr = parse.warrantyEndEpochDay?.let(DateFormats::epochDayToLocalDate)
        val months = parse.warrantyMonths
            ?: if (parse.isWarrantyForm) null else 12
        val useManualEnd = endFromOcr != null
        val endDate = when {
            endFromOcr != null -> endFromOcr
            months != null && purchase != null -> purchase.plusMonths(months.toLong())
            else -> null
        }
        val rawBlank = parse.rawText.isBlank()
        val keyFieldsEmpty = parse.merchantName.isNullOrBlank() &&
            parse.amountCents == null &&
            purchase == null &&
            months == null &&
            endFromOcr == null &&
            parse.note.isNullOrBlank()
        val hint = when {
            rawBlank -> "未能识别出文字。请手填或重试。"
            keyFieldsEmpty -> "已得到识别原文，但未能自动解析字段。请对照手改。"
            purchase == null -> "购买日期未识别到，请手动设置。"
            else -> null
        }
        patchDraft(draftId) { state ->
            state.copy(
                localImagePath = path,
                merchantName = parse.merchantName.orEmpty(),
                amountText = MoneyFormats.centsToYuanString(parse.amountCents),
                purchaseDate = purchase,
                warrantyMonthsText = months?.toString().orEmpty(),
                warrantyEndDate = endDate,
                useManualWarrantyEnd = useManualEnd,
                note = parse.note.orEmpty(),
                ocrRawText = parse.rawText,
                ocrStatus = BatchOcrStatus.SUCCESS,
                errorMessage = hint,
            )
        }
    }

    fun toggleSelect(id: String) {
        patchDraft(id) { it.copy(selected = !it.selected) }
        persistDraftSnapshot(_uiState.value.drafts)
    }

    fun toggleExpanded(id: String) {
        patchDraft(id) { it.copy(expanded = !it.expanded) }
    }

    fun remove(id: String) {
        val removed = _uiState.value.drafts.find { it.id == id }
        // 未入库的落盘图可删；已保存票证的图片归票证所有，不删
        if (removed != null && removed.savedTicketId == null) {
            imageStorage.deleteIfExists(removed.localImagePath)
        }
        _uiState.update { state ->
            val next = state.drafts.filterNot { it.id == id }
            state.copy(
                drafts = next,
                ocrTotal = next.size,
                ocrDone = next.count {
                    it.ocrStatus == BatchOcrStatus.SUCCESS || it.ocrStatus == BatchOcrStatus.FAILED
                },
            )
        }
        persistDraftSnapshot(_uiState.value.drafts)
    }

    fun retryOcr(id: String) {
        val draft = _uiState.value.drafts.find { it.id == id } ?: return
        if (draft.localImagePath.isNullOrBlank()) {
            viewModelScope.launch {
                val path = withContext(Dispatchers.IO) {
                    imageStorage.persistImage(Uri.parse(draft.sourceUri))
                }
                if (path.isNullOrBlank()) {
                    patchDraft(id) {
                        it.copy(ocrStatus = BatchOcrStatus.FAILED, errorMessage = "无法读取图片")
                    }
                    return@launch
                }
                patchDraft(id) {
                    it.copy(localImagePath = path, ocrStatus = BatchOcrStatus.PENDING, errorMessage = null)
                }
                startOcrQueue(listOf(id))
            }
        } else {
            patchDraft(id) { it.copy(ocrStatus = BatchOcrStatus.PENDING, errorMessage = null) }
            // 重试时从 ocrDone 中扣回，跑完再加
            _uiState.update { state ->
                state.copy(
                    ocrDone = state.drafts.count {
                        it.id != id &&
                            (it.ocrStatus == BatchOcrStatus.SUCCESS || it.ocrStatus == BatchOcrStatus.FAILED)
                    },
                )
            }
            startOcrQueue(listOf(id))
        }
    }

    fun updateMerchant(id: String, value: String) =
        patchDraft(id) { it.copy(merchantName = value, userEdited = true) }

    fun updateAmount(id: String, value: String) =
        patchDraft(id) { it.copy(amountText = value, userEdited = true) }

    fun updateNote(id: String, value: String) =
        patchDraft(id) { it.copy(note = value, userEdited = true) }

    fun updatePurchaseDate(id: String, date: LocalDate) {
        patchDraft(id) { state ->
            val end = if (!state.useManualWarrantyEnd) {
                val m = state.warrantyMonthsText.toIntOrNull() ?: 0
                date.plusMonths(m.toLong())
            } else {
                state.warrantyEndDate
            }
            state.copy(purchaseDate = date, warrantyEndDate = end, userEdited = true, errorMessage = null)
        }
    }

    fun updateWarrantyMonths(id: String, text: String) {
        val digits = text.filter { it.isDigit() }
        patchDraft(id) { state ->
            val end = if (!state.useManualWarrantyEnd) {
                val m = digits.toIntOrNull() ?: 0
                state.purchaseDate?.plusMonths(m.toLong())
            } else {
                state.warrantyEndDate
            }
            state.copy(warrantyMonthsText = digits, warrantyEndDate = end, userEdited = true)
        }
    }

    fun updateWarrantyEnd(id: String, date: LocalDate) {
        patchDraft(id) {
            it.copy(warrantyEndDate = date, useManualWarrantyEnd = true, userEdited = true)
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    suspend fun refreshQuota() {
        val premium = repository.observeHasLocalPremium().first()
        val count = repository.observeCount().first()
        val remaining = BatchQuotaLogic.remainingSlots(premium, count)
        _uiState.update {
            it.copy(hasLocalPremium = premium, remainingSlots = remaining)
        }
    }

    /**
     * 评估保存门槛；不足时返回 [BatchQuotaLogic.SaveGate] 供 UI 弹窗。
     */
    suspend fun evaluateSaveGate(): BatchQuotaLogic.SaveGate {
        refreshQuota()
        val state = _uiState.value
        return BatchQuotaLogic.decideSaveGate(
            remaining = state.remainingSlots,
            selectedUnsavedCount = state.selectedUnsaved.size,
        )
    }

    /**
     * 保存当前勾选且未入库的草稿。每条 insert 前再 [TicketRepository.canAddTicket]。
     */
    fun saveSelected(
        onNeedPro: () -> Unit,
        onNeedChoose: (remaining: Int, selectedCount: Int) -> Unit,
        onDone: (success: Int, fail: Int) -> Unit,
    ) {
        viewModelScope.launch {
            when (val gate = evaluateSaveGate()) {
                is BatchQuotaLogic.SaveGate.NothingToSave -> {
                    _uiState.update { it.copy(message = "请先勾选要保存的票证") }
                    return@launch
                }
                is BatchQuotaLogic.SaveGate.NeedUpgrade -> {
                    onNeedPro()
                    return@launch
                }
                is BatchQuotaLogic.SaveGate.NeedChoose -> {
                    onNeedChoose(gate.remaining, gate.selectedCount)
                    return@launch
                }
                BatchQuotaLogic.SaveGate.Proceed -> Unit
            }
            performSave(onNeedPro, onDone)
        }
    }

    /**
     * 在用户已确认名额/子集后执行保存（仍逐条 canAddTicket）。
     */
    fun confirmSaveSelected(
        onNeedPro: () -> Unit,
        onDone: (success: Int, fail: Int) -> Unit,
    ) {
        viewModelScope.launch { performSave(onNeedPro, onDone) }
    }

    fun retrySaveOne(
        id: String,
        onNeedPro: () -> Unit,
        onDone: (success: Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val draft = _uiState.value.drafts.find { it.id == id } ?: return@launch
            if (!BatchQuotaLogic.canInsert(draft)) {
                onDone(true)
                return@launch
            }
            if (!repository.canAddTicket()) {
                onNeedPro()
                onDone(false)
                return@launch
            }
            val ok = insertDraft(draft)
            onDone(ok)
        }
    }

    private suspend fun performSave(
        onNeedPro: () -> Unit,
        onDone: (success: Int, fail: Int) -> Unit,
    ) {
        _uiState.update { it.copy(isSaving = true, message = null) }
        var success = 0
        var fail = 0
        val targets = _uiState.value.selectedUnsaved.toList()
        for (draft in targets) {
            // 已保存跳过
            val latest = _uiState.value.drafts.find { it.id == draft.id } ?: continue
            if (!BatchQuotaLogic.canInsert(latest)) continue
            if (!repository.canAddTicket()) {
                // 中途名额用尽：剩余记失败，引导升级
                fail += 1
                onNeedPro()
                // 不再继续插入
                val rest = targets.dropWhile { it.id != draft.id }.drop(1)
                    .count { BatchQuotaLogic.canInsert(_uiState.value.drafts.find { d -> d.id == it.id } ?: it) }
                fail += rest
                break
            }
            if (insertDraft(latest)) success += 1 else fail += 1
        }
        _uiState.update {
            it.copy(
                isSaving = false,
                message = "保存完成：成功 $success，失败 $fail",
            )
        }
        refreshQuota()
        onDone(success, fail)
    }

    private suspend fun insertDraft(draft: BatchDraftItem): Boolean {
        return try {
            val ticket = Ticket(
                id = 0L,
                merchantName = draft.merchantName.trim(),
                amountCents = MoneyFormats.parseYuanToCents(draft.amountText),
                purchaseDateEpochDay = draft.purchaseDate?.toEpochDay(),
                warrantyMonths = draft.warrantyMonthsText.toIntOrNull(),
                warrantyEndEpochDay = draft.warrantyEndDate?.toEpochDay(),
                note = draft.note.trim(),
                imagePath = draft.localImagePath,
                ocrRawText = draft.ocrRawText,
            )
            val id = repository.saveTicket(ticket)
            patchDraft(draft.id) { BatchQuotaLogic.markSaved(it, id) }
            persistDraftSnapshot(_uiState.value.drafts)
            true
        } catch (e: Exception) {
            patchDraft(draft.id) {
                it.copy(errorMessage = "保存失败：${e.message ?: "未知错误"}")
            }
            false
        }
    }

    private fun patchDraft(id: String, transform: (BatchDraftItem) -> BatchDraftItem) {
        _uiState.update { state ->
            state.copy(drafts = state.drafts.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun updateDrafts(drafts: List<BatchDraftItem>) {
        _uiState.update { it.copy(drafts = drafts) }
    }

    private fun takePersistableBestEffort(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // Photo Picker 常不支持 persistable；落盘后即可不依赖
        }
    }

    private fun persistDraftSnapshot(drafts: List<BatchDraftItem>) {
        // SavedStateHandle 只存可序列化快照：路径 + 关键字段
        val paths = ArrayList<String>(drafts.size)
        val ids = ArrayList<String>(drafts.size)
        val merchants = ArrayList<String>(drafts.size)
        val amounts = ArrayList<String>(drafts.size)
        val purchaseDays = ArrayList<String>(drafts.size)
        val months = ArrayList<String>(drafts.size)
        val endDays = ArrayList<String>(drafts.size)
        val notes = ArrayList<String>(drafts.size)
        val uris = ArrayList<String>(drafts.size)
        val statuses = ArrayList<String>(drafts.size)
        val selected = ArrayList<String>(drafts.size)
        val savedIds = ArrayList<String>(drafts.size)
        drafts.forEach { d ->
            ids += d.id
            uris += d.sourceUri
            paths += d.localImagePath.orEmpty()
            merchants += d.merchantName
            amounts += d.amountText
            purchaseDays += d.purchaseDate?.toEpochDay()?.toString().orEmpty()
            months += d.warrantyMonthsText
            endDays += d.warrantyEndDate?.toEpochDay()?.toString().orEmpty()
            notes += d.note
            statuses += d.ocrStatus.name
            selected += if (d.selected) "1" else "0"
            savedIds += d.savedTicketId?.toString().orEmpty()
        }
        savedStateHandle[KEY_IDS] = ids
        savedStateHandle[KEY_URIS] = uris
        savedStateHandle[KEY_PATHS] = paths
        savedStateHandle[KEY_MERCHANTS] = merchants
        savedStateHandle[KEY_AMOUNTS] = amounts
        savedStateHandle[KEY_PURCHASE] = purchaseDays
        savedStateHandle[KEY_MONTHS] = months
        savedStateHandle[KEY_ENDS] = endDays
        savedStateHandle[KEY_NOTES] = notes
        savedStateHandle[KEY_STATUS] = statuses
        savedStateHandle[KEY_SELECTED] = selected
        savedStateHandle[KEY_SAVED] = savedIds
    }

    private fun restoreFromSavedState(): List<BatchDraftItem>? {
        val ids: ArrayList<String> = savedStateHandle[KEY_IDS] ?: return null
        if (ids.isEmpty()) return null
        val uris: ArrayList<String> = savedStateHandle[KEY_URIS] ?: return null
        val paths: ArrayList<String> = savedStateHandle[KEY_PATHS] ?: arrayListOf()
        val merchants: ArrayList<String> = savedStateHandle[KEY_MERCHANTS] ?: arrayListOf()
        val amounts: ArrayList<String> = savedStateHandle[KEY_AMOUNTS] ?: arrayListOf()
        val purchase: ArrayList<String> = savedStateHandle[KEY_PURCHASE] ?: arrayListOf()
        val months: ArrayList<String> = savedStateHandle[KEY_MONTHS] ?: arrayListOf()
        val ends: ArrayList<String> = savedStateHandle[KEY_ENDS] ?: arrayListOf()
        val notes: ArrayList<String> = savedStateHandle[KEY_NOTES] ?: arrayListOf()
        val statuses: ArrayList<String> = savedStateHandle[KEY_STATUS] ?: arrayListOf()
        val selected: ArrayList<String> = savedStateHandle[KEY_SELECTED] ?: arrayListOf()
        val saved: ArrayList<String> = savedStateHandle[KEY_SAVED] ?: arrayListOf()
        return ids.indices.map { i ->
            BatchDraftItem(
                id = ids[i],
                sourceUri = uris.getOrElse(i) { "" },
                localImagePath = paths.getOrNull(i)?.takeIf { it.isNotBlank() },
                merchantName = merchants.getOrElse(i) { "" },
                amountText = amounts.getOrElse(i) { "" },
                purchaseDate = purchase.getOrNull(i)?.toLongOrNull()?.let(LocalDate::ofEpochDay),
                warrantyMonthsText = months.getOrElse(i) { "" },
                warrantyEndDate = ends.getOrNull(i)?.toLongOrNull()?.let(LocalDate::ofEpochDay),
                note = notes.getOrElse(i) { "" },
                ocrStatus = runCatching {
                    BatchOcrStatus.valueOf(statuses.getOrElse(i) { BatchOcrStatus.PENDING.name })
                }.getOrDefault(BatchOcrStatus.PENDING),
                selected = selected.getOrElse(i) { "1" } == "1",
                savedTicketId = saved.getOrNull(i)?.toLongOrNull(),
            )
        }
    }

    companion object {
        private const val MAX_OCR_CONCURRENCY = 2
        private const val KEY_IDS = "batch_ids"
        private const val KEY_URIS = "batch_uris"
        private const val KEY_PATHS = "batch_paths"
        private const val KEY_MERCHANTS = "batch_merchants"
        private const val KEY_AMOUNTS = "batch_amounts"
        private const val KEY_PURCHASE = "batch_purchase"
        private const val KEY_MONTHS = "batch_months"
        private const val KEY_ENDS = "batch_ends"
        private const val KEY_NOTES = "batch_notes"
        private const val KEY_STATUS = "batch_status"
        private const val KEY_SELECTED = "batch_selected"
        private const val KEY_SAVED = "batch_saved"
    }
}
