package com.ticketkeep.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import com.ticketkeep.app.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.export.TicketCsvExporter
import com.ticketkeep.app.export.TicketCsvImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「我的」页状态：本地票证容量与 Pro 标记；CSV 导入/导出（Play · Pro）。
 */
data class SettingsUiState(
    val totalCount: Int = 0,
    val isPro: Boolean = false,
    val freeLimit: Int = TicketRepository.FREE_TICKET_LIMIT,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: TicketRepository,
    private val csvExporter: TicketCsvExporter,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeCount(),
        repository.observeIsPro(),
    ) { count, isPro ->
        SettingsUiState(
            totalCount = count,
            isPro = isPro,
            freeLimit = TicketRepository.FREE_TICKET_LIMIT,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

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

    /**
     * 仅 Debug：模拟 Pro / 普通版，写入 [TicketRepository.setPro]（走 debug_pro_override）。
     */
    fun debugSetPro(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch { repository.setPro(enabled) }
    }

}
