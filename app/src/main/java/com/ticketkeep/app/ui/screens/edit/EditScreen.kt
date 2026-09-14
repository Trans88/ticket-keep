package com.ticketkeep.app.ui.screens.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ticketkeep.app.ui.components.PaperCard
import com.ticketkeep.app.ui.components.TallScrollableImage
import com.ticketkeep.app.util.DateBounds
import com.ticketkeep.app.util.DateFormats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onNeedPro: () -> Unit,
    viewModel: EditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPurchasePicker by remember { mutableStateOf(false) }
    var showWarrantyPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.ticketId > 0) "编辑票证" else "新建票证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = { viewModel.save(onSaved = onSaved, onNeedPro = onNeedPro) },
                        enabled = !state.isSaving && !state.isOcrRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (state.isSaving) "保存中…" else "保存")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (state.isOcrRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.padding(8.dp))
                    Text("正在识别文字…")
                }
                Spacer(Modifier.height(12.dp))
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            if (!state.imagePath.isNullOrBlank()) {
                // 长图：Fit 宽度 + 纵向滚动，点击全屏缩放（非 Crop）
                TallScrollableImage(
                    imagePath = state.imagePath!!,
                    contentDescription = "票证图片",
                    maxHeight = 200.dp,
                    corner = 12.dp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.rerecognize() },
                    enabled = !state.isOcrRunning && !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (state.isOcrRunning) "识别中…" else "重新识别")
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.ocrAttempted) {
                CollapsibleOcrRawTextCard(rawText = state.ocrRawText)
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.merchantName,
                onValueChange = viewModel::updateMerchant,
                label = { Text("商家名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::updateAmount,
                label = { Text("金额（元，可空）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(16.dp))

            DatePickField(
                label = "购买日",
                date = state.purchaseDate,
                placeholder = "未设置（点此选择）",
                onClick = { showPurchasePicker = true },
            )
            if (state.ocrAttempted && state.purchaseDate == null) {
                Text(
                    "购买日期未识别，请手动设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.warrantyMonthsText,
                onValueChange = viewModel::updateWarrantyMonths,
                label = { Text("保修月数") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.useManualWarrantyEnd,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                FilterChip(
                    selected = !state.useManualWarrantyEnd,
                    onClick = { viewModel.toggleManualWarrantyEnd(false) },
                    label = { Text("按月数计算到期日") },
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.padding(4.dp))
                FilterChip(
                    selected = state.useManualWarrantyEnd,
                    onClick = { viewModel.toggleManualWarrantyEnd(true) },
                    label = { Text("手选到期日") },
                    shape = RoundedCornerShape(8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (state.useManualWarrantyEnd) {
                DatePickField(
                    label = "保修到期日",
                    date = state.warrantyEndDate,
                    placeholder = "未设置（点此选择）",
                    onClick = { showWarrantyPicker = true },
                )
            } else {
                DatePickField(
                    label = "保修到期日",
                    date = state.warrantyEndDate,
                    placeholder = "未设置",
                    enabled = false,
                    supportingText = "由保修月数自动计算",
                    onClick = {},
                )
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注 / 故障描述") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPurchasePicker) {
        EpochDayPickerDialog(
            initial = state.purchaseDate ?: LocalDate.now(),
            onDismiss = { showPurchasePicker = false },
            onConfirm = {
                viewModel.updatePurchaseDate(it)
                showPurchasePicker = false
            },
        )
    }
    if (showWarrantyPicker) {
        EpochDayPickerDialog(
            initial = state.warrantyEndDate ?: LocalDate.now().plusMonths(12),
            onDismiss = { showWarrantyPicker = false },
            onConfirm = {
                viewModel.updateWarrantyEnd(it)
                showWarrantyPicker = false
            },
        )
    }
}

/** OCR 原文卡：默认收起约 2 行预览，点击展开。 */
@Composable
private fun CollapsibleOcrRawTextCard(rawText: String) {
    var expanded by remember { mutableStateOf(false) }
    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "识别原文（只读）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                )
            }
            Spacer(Modifier.height(8.dp))
            val display = if (rawText.isBlank()) {
                "（空）未识别到文字。请手填字段，或点击「重新识别」。"
            } else {
                rawText
            }
            if (expanded) {
                Text(
                    text = display,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rawText.isBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp, max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                )
            } else {
                Text(
                    text = display,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 可点击的日期字段：Outlined 外观 + 日历图标，整行点按打开 DatePicker。
 */
@Composable
private fun DatePickField(
    label: String,
    date: LocalDate?,
    placeholder: String,
    enabled: Boolean = true,
    supportingText: String? = null,
    onClick: () -> Unit,
) {
    val text = date?.let { DateFormats.display.format(it) } ?: placeholder
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = if (enabled) "打开日期选择" else null,
                )
            },
            supportingText = supportingText?.let { msg -> { Text(msg) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onClick),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpochDayPickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val min = DateBounds.MIN
    val max = DateBounds.max()
    val initialClamped = initial.coerceIn(min, max)
    val millis = initialClamped.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = millis,
        yearRange = DateBounds.yearRange(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return DateBounds.isAllowed(date)
            }

            override fun isSelectableYear(year: Int): Boolean = year in DateBounds.yearRange()
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = pickerState.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date)
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        DatePicker(state = pickerState)
    }
}
