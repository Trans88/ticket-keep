package com.ticketkeep.app.ui.screens.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ticketkeep.app.util.DateFormats
import com.ticketkeep.app.util.MoneyFormats
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val ticket by viewModel.ticket.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ticket?.merchantName?.ifBlank { "票证详情" } ?: "票证详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (ticket != null) {
                        IconButton(onClick = { onEdit(ticket!!.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val t = ticket
        if (t == null) {
            Text("票证不存在或已删除", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (!t.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = File(t.imagePath),
                    contentDescription = "收据图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(16.dp))
            }
            DetailLine("商家", t.merchantName.ifBlank { "—" })
            DetailLine("金额", MoneyFormats.formatYuan(t.amountCents))
            DetailLine("购买日", DateFormats.formatEpochDay(t.purchaseDateEpochDay))
            DetailLine("保修月数", t.warrantyMonths?.let { "$it 个月" } ?: "—")
            DetailLine("保修到期", DateFormats.formatEpochDay(t.warrantyEndEpochDay))
            DetailLine("备注", t.note.ifBlank { "—" })
            if (t.ocrRawText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("OCR 原文", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(t.ocrRawText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除票证？") },
            text = { Text("删除后无法恢复，本地提醒也会取消。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onBack)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(value, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
}
