package com.ticketkeep.app.ui.screens.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ticketkeep.app.util.DateFormats
import java.io.File
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
        topBar = {
            TopAppBar(
                title = { Text(if (state.ticketId > 0) "编辑票证" else "新建票证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
                AsyncImage(
                    model = File(state.imagePath!!),
                    contentDescription = "票证图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(12.dp))
            }

            // 识别原文放在表单上方，便于核对保修表是否被 ML Kit 读出
            if (state.ocrAttempted) {
                OcrRawTextCard(rawText = state.ocrRawText)
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.merchantName,
                onValueChange = viewModel::updateMerchant,
                label = { Text("商家名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::updateAmount,
                label = { Text("金额（元，可空）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { showPurchasePicker = true }) {
                Text("购买日：${state.purchaseDate?.let { DateFormats.display.format(it) } ?: "未设置"}")
            }

            OutlinedTextField(
                value = state.warrantyMonthsText,
                onValueChange = viewModel::updateWarrantyMonths,
                label = { Text("保修月数") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.useManualWarrantyEnd,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(4.dp))
            Row {
                FilterChip(
                    selected = !state.useManualWarrantyEnd,
                    onClick = { viewModel.toggleManualWarrantyEnd(false) },
                    label = { Text("按月数计算到期日") },
                )
                Spacer(Modifier.padding(4.dp))
                FilterChip(
                    selected = state.useManualWarrantyEnd,
                    onClick = { viewModel.toggleManualWarrantyEnd(true) },
                    label = { Text("手选到期日") },
                )
            }
            TextButton(onClick = { showWarrantyPicker = true }) {
                Text("保修到期：${state.warrantyEndDate?.let { DateFormats.display.format(it) } ?: "未设置"}")
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注 / 故障描述") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.save(onSaved = onSaved, onNeedPro = onNeedPro) },
                enabled = !state.isSaving && !state.isOcrRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "保存中…" else "保存")
            }
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

@Composable
private fun OcrRawTextCard(rawText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "识别原文（只读）",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (rawText.isBlank()) {
                    "（空）ML Kit 未返回文字，请检查拍照角度与清晰度。"
                } else {
                    rawText
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (rawText.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp, max = 220.dp)
                    .verticalScroll(rememberScrollState()),
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
    val millis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
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
    ) {
        DatePicker(state = pickerState)
    }
}
