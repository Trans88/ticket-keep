package com.ticketkeep.app.ui.screens.paywall

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ticketkeep.app.data.repository.TicketRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("升级 Pro") },
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
                .padding(16.dp),
        ) {
            Text("票证记 Pro", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "免费版最多保存 ${TicketRepository.FREE_TICKET_LIMIT} 条票证。升级 Pro 可无限保存（计费尚未接入）。",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("· 无限票证条数", style = MaterialTheme.typography.bodyLarge)
                    Text("· 本地优先，无账号", style = MaterialTheme.typography.bodyLarge)
                    Text("· 保修到期提醒", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(24.dp))
            if (isPro) {
                Text("当前已是 Pro（本地模拟）", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.setPro(false) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("关闭 Pro（调试）") }
            } else {
                Button(
                    onClick = { viewModel.setPro(true) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("模拟开通 Pro（占位）") }
                Spacer(Modifier.height(8.dp))
                Text(
                    "正式版将接入 Google Play Billing，此处仅为 UI 占位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
