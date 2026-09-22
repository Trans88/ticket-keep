package com.ticketkeep.app.ui.screens.batch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ticketkeep.app.ui.components.PaperCard
import com.ticketkeep.app.ui.theme.TicketKeepSpacing
import com.ticketkeep.app.util.DateFormats
import com.ticketkeep.app.util.MoneyFormats
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 批量核对页：多图各自成草稿，OCR 进度、行内编辑、勾选保存；配额不足时弹窗选择或升级。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchReviewScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: BatchReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showQuotaDialog by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }

    fun requestBack() {
        val unsaved = state.drafts.any { it.savedTicketId == null }
        val edited = state.hasUnsavedEdits
        if (unsaved && edited) {
            showDiscardConfirm = true
        } else {
            onBack()
        }
    }

    BackHandler { requestBack() }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = TicketKeepSpacing.page, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { requestBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "批量核对",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            state.progressLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.ocrTotal > 0 && state.ocrDone < state.ocrTotal) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = state.ocrDone.toFloat() / state.ocrTotal.coerceAtLeast(1).toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = TicketKeepSpacing.page, vertical = 12.dp),
            ) {
                val selectedCount = state.selectedUnsaved.size
                Button(
                    onClick = {
                        viewModel.saveSelected(
                            onNeedPro = onOpenPaywall,
                            onNeedChoose = { remaining, selected ->
                                showQuotaDialog = remaining to selected
                            },
                            onDone = { success, fail ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("保存完成：成功 $success，失败 $fail")
                                }
                                // 全部草稿均已入库（或列表空）再返回；留下未选/失败项继续处理
                                val remain = viewModel.uiState.value.drafts.any { it.savedTicketId == null }
                                if (fail == 0 && success > 0 && !remain) {
                                    onDone()
                                }
                            },
                        )
                    },
                    enabled = !state.isSaving && selectedCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("保存所选票证" + if (selectedCount > 0) "（$selectedCount）" else "")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = TicketKeepSpacing.page),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.drafts, key = { it.id }) { draft ->
                BatchDraftCard(
                    draft = draft,
                    onToggleSelect = { viewModel.toggleSelect(draft.id) },
                    onRemove = { viewModel.remove(draft.id) },
                    onRetryOcr = { viewModel.retryOcr(draft.id) },
                    onExpandEdit = { editingId = draft.id },
                    onRetrySave = {
                        viewModel.retrySaveOne(
                            id = draft.id,
                            onNeedPro = onOpenPaywall,
                            onDone = { ok ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (ok) "已保存" else "保存失败",
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    editingId?.let { id ->
        val draft = state.drafts.find { it.id == id }
        if (draft != null) {
            BatchDraftEditDialog(
                draft = draft,
                onDismiss = { editingId = null },
                onMerchant = { viewModel.updateMerchant(id, it) },
                onAmount = { viewModel.updateAmount(id, it) },
                onNote = { viewModel.updateNote(id, it) },
                onWarrantyMonths = { viewModel.updateWarrantyMonths(id, it) },
                onPurchaseToday = {
                    viewModel.updatePurchaseDate(id, LocalDate.now())
                },
            )
        } else {
            editingId = null
        }
    }

    showQuotaDialog?.let { (remaining, selected) ->
        AlertDialog(
            onDismissRequest = { showQuotaDialog = null },
            title = { Text("免费名额不足") },
            text = {
                Text(
                    "还可以保存 $remaining 张，当前勾选了 $selected 张。\n" +
                        "请取消部分勾选后再保存，或升级解锁无限条数。\n" +
                        "不会自动截断所选草稿。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showQuotaDialog = null
                        onOpenPaywall()
                    },
                ) { Text("去升级") }
            },
            dismissButton = {
                TextButton(onClick = { showQuotaDialog = null }) {
                    Text("去勾选")
                }
            },
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃未保存草稿？") },
            text = {
                Text("未保存的草稿将被丢弃；已成功保存的票证不会删除。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onBack()
                    },
                ) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("继续编辑")
                }
            },
        )
    }
}

@Composable
private fun BatchDraftCard(
    draft: BatchDraftItem,
    onToggleSelect: () -> Unit,
    onRemove: () -> Unit,
    onRetryOcr: () -> Unit,
    onExpandEdit: () -> Unit,
    onRetrySave: () -> Unit,
) {
    val saved = draft.savedTicketId != null
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(
                onClick = onToggleSelect,
                enabled = !saved,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (draft.selected && !saved) {
                        Icons.Default.CheckBox
                    } else {
                        Icons.Default.CheckBoxOutlineBlank
                    },
                    contentDescription = if (draft.selected) "取消选择" else "选择",
                    tint = if (saved) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            val thumb = draft.localImagePath
            if (!thumb.isNullOrBlank()) {
                AsyncImage(
                    model = File(thumb),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = draft.merchantName.ifBlank { "未识别商家" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    when (draft.ocrStatus) {
                        BatchOcrStatus.PENDING, BatchOcrStatus.RUNNING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        BatchOcrStatus.FAILED -> {
                            Text(
                                "识别失败",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        BatchOcrStatus.SUCCESS -> Unit
                    }
                }
                Spacer(Modifier.height(4.dp))
                val amount = MoneyFormats.parseYuanToCents(draft.amountText)
                    ?.let { MoneyFormats.formatYuan(it) }
                    ?: draft.amountText.ifBlank { "—" }
                val date = draft.purchaseDate?.let { DateFormats.display.format(it) } ?: "购买日未设"
                val warranty = when {
                    draft.warrantyEndDate != null ->
                        "保修至 " + DateFormats.display.format(draft.warrantyEndDate)
                    draft.warrantyMonthsText.isNotBlank() ->
                        "保修 ${draft.warrantyMonthsText} 个月"
                    else -> "无保修信息"
                }
                Text(
                    "$amount · $date",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    warranty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!draft.errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        draft.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (saved) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "已保存",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onExpandEdit, enabled = !saved) {
                        Text("编辑")
                    }
                    if (draft.ocrStatus == BatchOcrStatus.FAILED ||
                        draft.ocrStatus == BatchOcrStatus.SUCCESS
                    ) {
                        TextButton(onClick = onRetryOcr, enabled = !saved) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重试 OCR")
                        }
                    }
                    if (!saved && draft.errorMessage?.contains("保存失败") == true) {
                        TextButton(onClick = onRetrySave) { Text("重试保存") }
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "移除")
            }
        }
    }
}

@Composable
private fun BatchDraftEditDialog(
    draft: BatchDraftItem,
    onDismiss: () -> Unit,
    onMerchant: (String) -> Unit,
    onAmount: (String) -> Unit,
    onNote: (String) -> Unit,
    onWarrantyMonths: (String) -> Unit,
    onPurchaseToday: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑草稿") },
        text = {
            Column {
                OutlinedTextField(
                    value = draft.merchantName,
                    onValueChange = onMerchant,
                    label = { Text("商家") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = onAmount,
                    label = { Text("金额（元）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.warrantyMonthsText,
                    onValueChange = onWarrantyMonths,
                    label = { Text("保修月数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = onNote,
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val purchaseLabel = draft.purchaseDate?.let { DateFormats.display.format(it) } ?: "未设置"
                Text("购买日：$purchaseLabel", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onPurchaseToday) { Text("设为今天") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}
