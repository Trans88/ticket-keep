package com.ticketkeep.app.ui.screens.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ticketkeep.app.backup.BackupListItem
import com.ticketkeep.app.ui.components.PaperCard

/**
 * 云备份设置子页：注册/登录、备份口令、上传、列表、恢复确认、删除、登出、Base URL。
 * 非 Pro 由外层导航到 Paywall，本页仍再校验。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    onNeedPro: () -> Unit,
    viewModel: CloudBackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearMessage()
    }

    LaunchedEffect(state.isPro) {
        if (state.isPro == false) onNeedPro()
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var passphraseConfirm by remember { mutableStateOf("") }
    var baseUrlDraft by remember { mutableStateOf(state.baseUrl) }
    var restorePassphrase by remember { mutableStateOf("") }
    var restoreTargetId by remember { mutableStateOf<String?>(null) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.baseUrl) { baseUrlDraft = state.baseUrl }
    LaunchedEffect(state.email) { if (state.email.isNotBlank()) email = state.email }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("云备份") },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "本地优先。云端只存你用「备份口令」加密后的密文；口令不会上传。账号密码与备份口令相互独立。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (state.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
                Spacer(Modifier.height(12.dp))
            }

            Text("服务器", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = baseUrlDraft,
                        onValueChange = { baseUrlDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Base URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.setBaseUrl(baseUrlDraft) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !state.busy,
                    ) { Text("保存地址") }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("账号", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (state.isLoggedIn) {
                        Text("已登录：${state.email}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !state.busy,
                        ) { Text("登出") }
                    } else {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("邮箱") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("账号密码（≥8 位）") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.login(email, password) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !state.busy,
                        ) { Text("登录") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.register(email, password) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !state.busy,
                        ) { Text("注册") }
                    }
                }
            }

            if (state.isLoggedIn) {
                Spacer(Modifier.height(20.dp))
                Text("备份口令与上传", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "备份口令用于本地加密，与登录密码不同。请牢记，丢失将无法解密。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("备份口令") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passphraseConfirm,
                            onValueChange = { passphraseConfirm = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("确认备份口令") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                when {
                                    passphrase.length < 6 ->
                                        viewModel.showMessage("备份口令至少 6 位")
                                    passphrase != passphraseConfirm ->
                                        viewModel.showMessage("两次口令不一致")
                                    else -> viewModel.upload(passphrase)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !state.busy,
                        ) { Text("加密并上传备份") }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "首版备份包含票证 CSV 与本地可读图片；恢复为「新增导入」，不会静默清空本地。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // validation feedback via LaunchedEffect-style inline:
                        if (passphrase.isNotEmpty() && passphrase.length < 6) {
                            Text("备份口令至少 6 位", color = MaterialTheme.colorScheme.error)
                        } else if (passphraseConfirm.isNotEmpty() && passphrase != passphraseConfirm) {
                            Text("两次口令不一致", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("云端备份", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.refreshList() }, enabled = !state.busy) {
                        Text("刷新")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.backups.isEmpty()) {
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "暂无备份",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.backups.forEach { item ->
                        BackupRow(
                            item = item,
                            enabled = !state.busy,
                            onRestore = { restoreTargetId = item.id },
                            onDelete = { deleteTargetId = item.id },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    restoreTargetId?.let { id ->
        AlertDialog(
            onDismissRequest = { restoreTargetId = null; restorePassphrase = "" },
            title = { Text("恢复备份") },
            text = {
                Column {
                    Text("请输入该备份的「备份口令」。解密后会预览条数，确认才导入为新票证。")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text("备份口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pass = restorePassphrase
                        restoreTargetId = null
                        restorePassphrase = ""
                        viewModel.prepareRestore(id, pass)
                    },
                ) { Text("解密预览") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTargetId = null; restorePassphrase = "" }) {
                    Text("取消")
                }
            },
        )
    }

    state.restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestorePreview() },
            title = { Text("确认导入") },
            text = {
                Column {
                    Text("将新增 ${preview.tickets.size} 条票证（不会删除本地已有数据）。")
                    if (preview.skippedRows > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("跳过 ${preview.skippedRows} 行。")
                    }
                    if (preview.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(preview.warnings.take(3).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore() }) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRestorePreview() }) { Text("取消") }
            },
        )
    }

    deleteTargetId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("删除云端备份？") },
            text = { Text("删除后无法恢复该密文备份。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTargetId = null
                        viewModel.deleteBackup(id)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun BackupRow(
    item: BackupListItem,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(item.createdAt ?: item.id, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "大小 ${formatSize(item.size)} · id ${item.id.take(8)}…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = onRestore, enabled = enabled, shape = RoundedCornerShape(10.dp)) {
                    Text("恢复")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onDelete, enabled = enabled, shape = RoundedCornerShape(10.dp)) {
                    Text("删除")
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    if (bytes < 1024 * 1024) return "%.1fKB".format(bytes / 1024.0)
    return "%.2fMB".format(bytes / (1024.0 * 1024.0))
}
