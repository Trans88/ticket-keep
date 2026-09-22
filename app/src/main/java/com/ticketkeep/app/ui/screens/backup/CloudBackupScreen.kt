package com.ticketkeep.app.ui.screens.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ticketkeep.app.BuildConfig
import com.ticketkeep.app.backup.BackupListItem
import com.ticketkeep.app.ui.components.PaperCard

/**
 * 云备份页（UX 第三刀 · 状态机分段）：
 * - 未登录：仅注册/登录；不展示备份列表主操作、不一键恢复主路径。
 * - 已登录：备份状态 + 一键加密上传 + 备份列表；口令在上传/恢复需要时强调。
 * - 恢复：二次确认对话框 → 输入并确认口令 → 再下载解密导入。
 * - 高级：Base URL 仅 [BuildConfig.DEBUG] 可见，收入「高级」折叠，默认折叠。
 * 云上传/恢复看 canUseCloud；本地高级版不自动授予云。退出账号不清本地买断。
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

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var passphraseConfirm by remember { mutableStateOf("") }
    var baseUrlDraft by remember { mutableStateOf(state.baseUrl) }
    // 高级区默认折叠（验收：高级默认折叠）
    var advancedExpanded by remember { mutableStateOf(false) }

    // 恢复两步：先二次确认，再口令（含确认口令）
    var restoreConfirmId by remember { mutableStateOf<String?>(null) }
    var restorePassphraseTargetId by remember { mutableStateOf<String?>(null) }
    var restorePassphrase by remember { mutableStateOf("") }
    var restorePassphraseConfirm by remember { mutableStateOf("") }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.baseUrl) { baseUrlDraft = state.baseUrl }
    LaunchedEffect(state.email) { if (state.email.isNotBlank()) email = state.email }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("加密云备份") },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 云权益状态条（与本地高级分离）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    if (state.canUseCloud == true) {
                        "云备份服务有效 · 不自动解锁本地高级版"
                    } else {
                        "云备份须单独开通 · 不自动解锁本地高级版"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.canUseCloud != true) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onNeedPro) {
                        Text("查看升级与备份方案")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!state.isLoggedIn) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(122.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(56.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(62.dp),
                    )
                }
                Text(
                    "为重要的票证，\n再留一份。",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 34.sp,
                        letterSpacing = (-0.5).sp,
                    ),
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    "票证在手机里加密后上传。\n云端不保存你的备份口令。",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            } else {
                Text(
                    "本地优先。云端只存加密密文；备份口令不会上传。登录后约 3 步即可上传：设口令 → 上传 → 看列表。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (state.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
                Spacer(Modifier.height(12.dp))
            }

            // —— 分段：账号（未登录主路径；已登录仅状态条）——
            if (state.isLoggedIn) {
                Text("账号", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
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
                        Text(
                            "先连接你的备份账号",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "仅云备份需要账号，本地记录始终可用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(15.dp),
                            enabled = !state.busy,
                        ) { Text("登录 / 注册备份账号") }
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { viewModel.register(email, password) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                        ) {
                            Text(
                                "没有账号？注册",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (!state.isLoggedIn) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "请妥善保管备份口令。忘记后无法解密已有备份。恢复会新增票证，保留本机已有记录。",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // —— 已登录主路径：备份状态 + 一键加密上传 + 备份列表 ——
            if (state.isLoggedIn) {
                Spacer(Modifier.height(20.dp))
                Text("备份状态", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        val count = state.backups.size
                        val latest = state.backups.firstOrNull()
                        Text(
                            if (count == 0) "尚未上传过云端备份"
                            else "云端共 $count 份备份",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (latest != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "最近：${latest.createdAt ?: latest.id.take(8)}… · ${formatSize(latest.size)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "首版备份含票证 CSV 与本地可读图片；恢复为「新增导入」，不会静默清空本地。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("一键加密上传", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        // 上传需要时再强调设备份口令
                        Text(
                            "请设置备份口令（与登录密码不同）。口令仅本机用于加密，丢失将无法解密。",
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
                        if (passphrase.isNotEmpty() && passphrase.length < 6) {
                            Spacer(Modifier.height(6.dp))
                            Text("备份口令至少 6 位", color = MaterialTheme.colorScheme.error)
                        } else if (passphraseConfirm.isNotEmpty() && passphrase != passphraseConfirm) {
                            Spacer(Modifier.height(6.dp))
                            Text("两次口令不一致", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "云端备份列表",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.refreshList() }, enabled = !state.busy) {
                        Text("刷新")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.backups.isEmpty()) {
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "暂无备份。完成上方「设口令 → 上传」后会出现在这里。",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.backups.forEach { item ->
                        BackupRow(
                            item = item,
                            enabled = !state.busy,
                            // 恢复入口：先进入二次确认，而非直接填口令
                            onRestore = { restoreConfirmId = item.id },
                            onDelete = { deleteTargetId = item.id },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // —— 高级：Base URL 仅 Debug；默认折叠 ——
            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(20.dp))
                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { advancedExpanded = !advancedExpanded },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "高级",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (advancedExpanded) "收起高级" else "展开高级",
                            )
                        }
                        if (advancedExpanded) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "仅 Debug 可见。正式包不展示服务器地址。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
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
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // 恢复步骤 1：二次确认（降低误触）
    restoreConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { restoreConfirmId = null },
            title = { Text("确认恢复？") },
            text = {
                Text(
                    "将从此云端备份下载并解密，随后以「新增导入」写入本地（不会静默清空已有票证）。请确认这是你要恢复的那一份。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreConfirmId = null
                        restorePassphraseTargetId = id
                        restorePassphrase = ""
                        restorePassphraseConfirm = ""
                    },
                ) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { restoreConfirmId = null }) { Text("取消") }
            },
        )
    }

    // 恢复步骤 2：输入并确认备份口令，确认后再下载解密
    restorePassphraseTargetId?.let { id ->
        AlertDialog(
            onDismissRequest = {
                restorePassphraseTargetId = null
                restorePassphrase = ""
                restorePassphraseConfirm = ""
            },
            title = { Text("输入备份口令") },
            text = {
                Column {
                    Text("请输入该备份的「备份口令」，并再次确认。口令正确后才会下载解密并预览条数。")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text("备份口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restorePassphraseConfirm,
                        onValueChange = { restorePassphraseConfirm = it },
                        label = { Text("确认备份口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (restorePassphraseConfirm.isNotEmpty() &&
                        restorePassphrase != restorePassphraseConfirm
                    ) {
                        Spacer(Modifier.height(6.dp))
                        Text("两次口令不一致", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            restorePassphrase.length < 6 ->
                                viewModel.showMessage("备份口令至少 6 位")
                            restorePassphrase != restorePassphraseConfirm ->
                                viewModel.showMessage("两次口令不一致")
                            else -> {
                                val pass = restorePassphrase
                                restorePassphraseTargetId = null
                                restorePassphrase = ""
                                restorePassphraseConfirm = ""
                                viewModel.prepareRestore(id, pass)
                            }
                        }
                    },
                ) { Text("解密预览") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        restorePassphraseTargetId = null
                        restorePassphrase = ""
                        restorePassphraseConfirm = ""
                    },
                ) { Text("取消") }
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
                        Text(
                            preview.warnings.take(3).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                        )
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
