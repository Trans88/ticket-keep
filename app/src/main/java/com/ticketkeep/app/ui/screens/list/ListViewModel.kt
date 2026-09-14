package com.ticketkeep.app.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.data.repository.TicketRepository
import android.content.Context
import android.net.Uri
import com.ticketkeep.app.export.TicketCsvExporter
import com.ticketkeep.app.export.TicketCsvImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 列表页 UI 状态：搜索+筛选后的票证、总条数、筛选条件与 Pro/免费额度。
 * 筛选状态说明：保存在 ViewModel，直至用户点「清除筛选」；同进程内进详情再返回仍保持。
 */
data class ListUiState(
    val tickets: List<Ticket> = emptyList(),
    val query: String = "",
    val filter: TicketListFilterState = TicketListFilterState(),
    val totalCount: Int = 0,
    val count: Int = 0,
    val isPro: Boolean = false,
    val freeLimit: Int = TicketRepository.FREE_TICKET_LIMIT,
) {
    val isFilterOrSearchActive: Boolean
        get() = query.isNotBlank() || filter.hasActiveConstraints
}

/**
 * 列表页逻辑：搜索、到期/时间筛选、额度判断、导出/导入 CSV。
 */
@HiltViewModel
class ListViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: TicketRepository,
    private val csvExporter: TicketCsvExporter,
) : ViewModel() {

    private val query = MutableStateFlow("")
    /** 列表筛选：进程内保持，直到 [clearFilter]。 */
    private val filter = MutableStateFlow(TicketListFilterState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ListUiState> = combine(
        query.flatMapLatest { q -> repository.observeTickets(q) },
        query,
        filter,
        repository.observeCount(),
        repository.observeIsPro(),
    ) { tickets, q, f, total, isPro ->
        val filtered = TicketListFilter.apply(tickets, f)
        ListUiState(
            tickets = filtered,
            query = q,
            filter = f,
            totalCount = total,
            count = total,
            isPro = isPro,
            freeLimit = TicketRepository.FREE_TICKET_LIMIT,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setExpiryBucket(bucket: ExpiryBucket) {
        filter.value = filter.value.copy(expiry = bucket)
    }

    fun setRangeField(field: DateRangeField) {
        filter.value = filter.value.copy(rangeField = field)
    }

    fun setRangeStart(date: LocalDate?) {
        filter.value = filter.value.copy(rangeStart = date)
    }

    fun setRangeEnd(date: LocalDate?) {
        filter.value = filter.value.copy(rangeEnd = date)
    }

    fun clearFilter() {
        filter.value = TicketListFilterState()
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

    /**
     * 解析 SAF 选中的 CSV：非 Pro → [onNeedPro]；表头错误 → [onError]；成功 → [onPreview]。
     */
    fun prepareCsvImport(
        uri: Uri,
        onNeedPro: () -> Unit,
        onPreview: (TicketCsvImporter.Preview) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            if (!repository.observeIsPro().first()) {
                onNeedPro()
                return@launch
            }
            try {
                val text = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("无法读取文件")
                }
                when (val outcome = TicketCsvImporter.parse(text)) {
                    is TicketCsvImporter.Outcome.Error -> onError(outcome.message)
                    is TicketCsvImporter.Outcome.Ready -> onPreview(outcome.preview)
                }
            } catch (e: Exception) {
                onError(e.message?.takeIf { it.isNotBlank() } ?: "读取 CSV 失败")
            }
        }
    }

    /**
     * 确认导入：一律新插入；清理无效图片路径；回报成功条数。
     */
    fun confirmCsvImport(
        preview: TicketCsvImporter.Preview,
        onDone: (inserted: Int, skipped: Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            if (!repository.observeIsPro().first()) {
                onError("需要 Pro 才能导入")
                return@launch
            }
            try {
                val ready = TicketCsvImporter.sanitizeImagePaths(preview.tickets)
                var inserted = 0
                withContext(Dispatchers.IO) {
                    for (t in ready) {
                        repository.saveTicket(t.copy(id = 0L))
                        inserted++
                    }
                }
                onDone(inserted, preview.skippedRows)
            } catch (e: Exception) {
                onError(e.message?.takeIf { it.isNotBlank() } ?: "导入失败")
            }
        }
    }
}
