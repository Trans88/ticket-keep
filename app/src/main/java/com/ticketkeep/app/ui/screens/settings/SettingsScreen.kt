package com.ticketkeep.app.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ticketkeep.app.BuildConfig
import com.ticketkeep.app.channel.ChannelConfig
import com.ticketkeep.app.export.ExportShareHelper
import com.ticketkeep.app.export.TicketCsvImporter
import com.ticketkeep.app.notification.NotificationSettingsHelper
import com.ticketkeep.app.ui.components.PaperCard
import com.ticketkeep.app.ui.components.SettingsRow
import com.ticketkeep.app.ui.theme.TicketKeepRadius
import com.ticketkeep.app.ui.theme.TicketKeepSpacing
import kotlinx.coroutines.launch

/**
 * 「我的」— 对齐 index.html `mine()`：
 * 标题「我的」+ YOUR SPACE；本地票证册；soft capacity；
 * 数据与备份 / 使用偏好；导入导出 sheet；保修提醒 sheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCloudBackup: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    showUpNavigation: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var notifEnabled by remember {
        mutableStateOf(NotificationSettingsHelper.areNotificationsEnabled(context))
    }
    var showNotifSheet by remember { mutableStateOf(false) }
    var showImportExportSheet by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<TicketCsvImporter.Preview?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifEnabled = NotificationSettingsHelper.areNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var permissionRequestedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notifEnabled = NotificationSettingsHelper.areNotificationsEnabled(context)
        permissionRequestedOnce = true
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.prepareCsvImport(
            uri = uri,
            onNeedPro = onOpenPaywall,
            onPreview = { preview -> importPreview = preview },
            onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
        )
    }

    fun requestOrOpenNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            NotificationSettingsHelper.openAppNotificationSettings(context)
            return
        }
        val activity = context as? Activity
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            NotificationSettingsHelper.openAppNotificationSettings(context)
            return
        }
        val shouldShow = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } == true
        if (permissionRequestedOnce && !shouldShow) {
            NotificationSettingsHelper.openAppNotificationSettings(context)
        } else {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun exportCsv() {
        viewModel.exportAllCsv(
            onNeedPro = onOpenPaywall,
            onSuccess = { file ->
                ExportShareHelper.shareFile(
                    context,
                    file,
                    "text/csv",
                    "导出全部 CSV",
                )
            },
            onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TicketKeepSpacing.page),
        ) {
            // Header: 我的 + YOUR SPACE — statusBars + 8dp（status 底→标题 ≈8–12dp）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showUpNavigation) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                    Text(
                        "我的",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.8).sp,
                        ),
                    )
                }
                Text(
                    "YOUR SPACE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Profile — 本地票证册
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(61.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "本地票证册",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "无需登录，也能好好记录。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Capacity card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(19.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 0.dp,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "已收好 ${ui.totalCount} 张票证",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            Text(
                                text = if (ui.hasLocalPremium) "本地高级" else "普通版",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (!ui.hasLocalPremium) {
                        Spacer(Modifier.height(14.dp))
                        val progress = (ui.totalCount.toFloat() / ui.freeLimit.toFloat())
                            .coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = androidx.compose.ui.graphics.Color(0xFFCDDCC5),
                            strokeCap = StrokeCap.Round,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${ui.totalCount} / ${ui.freeLimit} 张",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (ChannelConfig.showProPurchase) {
                                TextButton(
                                    onClick = onOpenPaywall,
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        "了解升级 →",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 12.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                Text(
                                    "本地保存",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(11.dp))
                        Text(
                            text = if (ChannelConfig.showProPurchase) {
                                "需要更多空间？本地高级版可不限条数（不含云备份）。"
                            } else {
                                "当前版本暂未开放会员购买。"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "本地高级版 · 不限条数",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("数据与备份")
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(
                        title = "加密云备份",
                        subtitle = if (ui.canUseCloud) {
                            "服务有效 · 手动加密备份"
                        } else {
                            "单独开通 · 不自动解锁本地高级版"
                        },
                        leadingIcon = Icons.Default.Cloud,
                        onClick = {
                            // 始终可进入云备份页（登录/说明）；上传门禁在页内
                            onOpenCloudBackup()
                        },
                    )
                    // 双状态行（本地 / 云）
                    Text(
                        text = "本地高级版：" + (if (ui.hasLocalPremium) "已解锁" else "未解锁") +
                            "  ·  云备份：" + (if (ui.canUseCloud) "有效" else "未开通/已到期"),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (ChannelConfig.showProExport) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsRow(
                            title = "导入与导出",
                            subtitle = "CSV、PDF 与送修材料",
                            leadingIcon = Icons.Default.Upload,
                            onClick = { showImportExportSheet = true },
                        )
                    }
                }
            }

            SectionLabel("使用偏好")
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(
                        title = "保修提醒",
                        subtitle = "检查通知与后台设置",
                        leadingIcon = Icons.Default.Notifications,
                        trailing = {
                            Text(
                                if (notifEnabled) "已开启" else "未开启",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { showNotifSheet = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "隐私与数据",
                        subtitle = "了解本地保存与加密备份",
                        leadingIcon = Icons.Default.VerifiedUser,
                        onClick = onOpenPrivacy,
                    )
                }
            }


            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("调试")
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.debugSetLocal(true)
                                scope.launch {
                                    snackbarHostState.showSnackbar("已模拟本地高级版")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(TicketKeepRadius.button),
                        ) { Text("模拟本地高级版") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.debugSetCloud(true)
                                scope.launch {
                                    snackbarHostState.showSnackbar("已模拟云备份有效")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(TicketKeepRadius.button),
                        ) { Text("模拟云备份有效") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.debugAllOff()
                                scope.launch {
                                    snackbarHostState.showSnackbar("已全部关闭")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(TicketKeepRadius.button),
                        ) { Text("全部关闭") }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "票证记 · 让凭证有处可寻",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showImportExportSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showImportExportSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 8.dp)
                    .padding(bottom = 22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "导入与导出",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    IconButton(onClick = { showImportExportSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                Text(
                    "正式版复用现有 CSV 预览、确认导入及导出逻辑。PDF 和送修材料在单张票证详情中生成。",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 21.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        showImportExportSheet = false
                        exportCsv()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        "导出全部 CSV",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        showImportExportSheet = false
                        importCsvLauncher.launch(arrayOf("text/*", "text/csv", "application/csv"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "从 CSV 导入",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (showNotifSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNotifSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 8.dp)
                    .padding(bottom = 22.dp),
            ) {
                Text(
                    "保修到期提醒",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "提前 7 天、1 天和到期当天提醒你。送达时间可能受手机后台限制影响。",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 21.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = if (notifEnabled) "通知已开启" else "通知尚未开启",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (notifEnabled) {
                            try {
                                NotificationSettingsHelper.postTestNotification(context)
                                scope.launch { snackbarHostState.showSnackbar("已发送测试通知") }
                            } catch (e: Exception) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "发送失败：${e.message ?: "请检查通知权限"}",
                                    )
                                }
                            }
                        } else {
                            requestOrOpenNotificationPermission()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text(
                        if (notifEnabled) "发送测试通知" else "开启通知",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        NotificationSettingsHelper.openAppNotificationSettings(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "收不到提醒？",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("确认导入 ${preview.tickets.size} 张票证") },
            text = {
                Text(
                    "将新增 ${preview.tickets.size} 张，跳过 ${preview.skippedRows} 行。" +
                        "导入会新增记录，不覆盖本地数据；重复导入可能产生重复记录。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = preview
                        importPreview = null
                        viewModel.confirmCsvImport(
                            preview = p,
                            onDone = { inserted, skipped ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "已导入 $inserted 张 · 跳过 $skipped 行",
                                    )
                                }
                            },
                            onError = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                        )
                    },
                ) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = { importPreview = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 17.dp, bottom = 12.dp),
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}
