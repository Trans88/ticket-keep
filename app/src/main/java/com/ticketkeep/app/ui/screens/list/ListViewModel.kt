package com.ticketkeep.app.ui.screens.list

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.export.TicketCsvExporter
import com.ticketkeep.app.export.TicketCsvImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
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
 * 首页快捷筛选（UI）：全部 / 即将到期 / 未过期 / 未设保修。
 * 「即将到期」映射为 NOT_EXPIRED + WARRANTY_END + [today, today+30]。
 */
enum class ListQuickFilter {
    ALL,
    SOON,
    NOT_EXPIRED,
    NO_WARRANTY,
}

/**
 * 列表页 UI 状态：搜索+筛选后的票证、全量摘要、筛选条件与 Pro/免费额度。
 */
data class ListUiState(
    val tickets: List<Ticket> = emptyList(),
    val query: String = "",
    val filter: TicketListFilterState = TicketListFilterState(),
    val quickFilter: ListQuickFilter = ListQuickFilter.ALL,
    val totalCount: Int = 0,
    /** 全量临期数（与搜索无关）：[today, today+30] 含今天 */
    val soonCount: Int = 0,
    val count: Int = 0,
    val isPro: Boolean = false,
    val freeLimit: Int = TicketRepository.FREE_TICKET_LIMIT,
) {
    val isFilterOrSearchActive: Boolean
        get() = query.isNotBlank() || filter.hasActiveConstraints
}

/**
 * 列表页逻辑：搜索、到期/时间筛选、临期摘要、额度判断、导出/导入 CSV。
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
    private val quickFilter = MutableStateFlow(ListQuickFilter.ALL)

    private data class QueryFilter(
        val query: String,
        val filter: TicketListFilterState,
        val quick: ListQuickFilter,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ListUiState> =
        combine(query, filter, quickFilter) { q, f, qf -> QueryFilter(q, f, qf) }
            .flatMapLatest { qf ->
                combine(
                    repository.observeTickets(qf.query),
                    repository.observeTickets(""),
                    repository.observeCount(),
                    repository.observeIsPro(),
                ) { filteredByQuery, allTickets, total, isPro ->
                    val today = LocalDate.now()
                    val effectiveFilter = if (qf.quick == ListQuickFilter.SOON) {
                        soonFilterState(today)
                    } else {
                        qf.filter
                    }
                    val filtered = TicketListFilter.apply(filteredByQuery, effectiveFilter, today)
                    val todayEpoch = today.toEpochDay()
                    val endSoon = today.plusDays(30).toEpochDay()
                    val soon = allTickets.count { t ->
                        val end = t.warrantyEndEpochDay ?: return@count false
                        end in todayEpoch..endSoon
                    }
                    ListUiState(
                        tickets = filtered,
                        query = qf.query,
                        filter = effectiveFilter,
                        quickFilter = qf.quick,
                        totalCount = total,
                        soonCount = soon,
                        count = total,
                        isPro = isPro,
                        freeLimit = TicketRepository.FREE_TICKET_LIMIT,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setQuickFilter(value: ListQuickFilter) {
        quickFilter.value = value
        val today = LocalDate.now()
        filter.value = when (value) {
            ListQuickFilter.ALL -> TicketListFilterState()
            ListQuickFilter.SOON -> soonFilterState(today)
            ListQuickFilter.NOT_EXPIRED -> TicketListFilterState(expiry = ExpiryBucket.NOT_EXPIRED)
            ListQuickFilter.NO_WARRANTY -> TicketListFilterState(expiry = ExpiryBucket.NO_END)
        }
    }

    /** 点击摘要「查看 30 天内到期」 */
    fun applySoonFilter() {
        setQuickFilter(ListQuickFilter.SOON)
    }

    fun setExpiryBucket(bucket: ExpiryBucket) {
        // 高级筛选改到期桶时退出快捷 SOON 语义
        quickFilter.value = when (bucket) {
            ExpiryBucket.ALL -> ListQuickFilter.ALL
            ExpiryBucket.NOT_EXPIRED -> ListQuickFilter.NOT_EXPIRED
            ExpiryBucket.NO_END -> ListQuickFilter.NO_WARRANTY
            ExpiryBucket.EXPIRED -> ListQuickFilter.ALL
        }
        filter.value = filter.value.copy(expiry = bucket)
    }

    fun setRangeField(field: DateRangeField) {
        if (quickFilter.value == ListQuickFilter.SOON) {
            quickFilter.value = ListQuickFilter.ALL
        }
        filter.value = filter.value.copy(rangeField = field)
    }

    fun setRangeStart(date: LocalDate?) {
        if (quickFilter.value == ListQuickFilter.SOON) {
            quickFilter.value = ListQuickFilter.ALL
        }
        filter.value = filter.value.copy(rangeStart = date)
    }

    fun setRangeEnd(date: LocalDate?) {
        if (quickFilter.value == ListQuickFilter.SOON) {
            quickFilter.value = ListQuickFilter.ALL
        }
        filter.value = filter.value.copy(rangeEnd = date)
    }

    fun clearFilter() {
        filter.value = TicketListFilterState()
        quickFilter.value = ListQuickFilter.ALL
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

    companion object {
        fun soonFilterState(today: LocalDate = LocalDate.now()): TicketListFilterState =
            TicketListFilterState(
                expiry = ExpiryBucket.NOT_EXPIRED,
                rangeField = DateRangeField.WARRANTY_END,
                rangeStart = today,
                rangeEnd = today.plusDays(30),
            )
    }
}
