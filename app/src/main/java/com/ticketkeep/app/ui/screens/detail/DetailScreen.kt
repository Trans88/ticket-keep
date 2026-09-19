package com.ticketkeep.app.ui.screens.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ticketkeep.app.channel.ChannelConfig
import com.ticketkeep.app.export.ExportShareHelper
import com.ticketkeep.app.notification.NotificationSettingsHelper
import com.ticketkeep.app.ui.components.PaperCard
import com.ticketkeep.app.ui.components.TallScrollableImage
import com.ticketkeep.app.ui.components.WarrantyStatusChip
import com.ticketkeep.app.ui.theme.OnWarningContainer
import com.ticketkeep.app.ui.theme.TicketKeepRadius
import com.ticketkeep.app.ui.theme.TicketKeepSpacing
import com.ticketkeep.app.ui.theme.WarningContainer
import com.ticketkeep.app.util.DateFormats
import com.ticketkeep.app.util.MoneyFormats
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 票证详情页：对齐设计 SoT `detail()`。
 * 自定义顶栏 +「票证操作」底部表；摘要 / 临期警告 / 购买信息 / 原始凭证 / OCR 折叠；
 * 渠道门禁 [ChannelConfig.showProExport] 控制导出行与底部主 CTA（Play 送修材料 / China 编辑）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenPaywall: () -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val ticket by viewModel.ticket.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var showReceiptSheet by remember { mutableStateOf(false) }
    var showNotifSheet by remember { mutableStateOf(false) }
    var ocrExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var notifEnabled by remember {
        mutableStateOf(NotificationSettingsHelper.areNotificationsEnabled(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notifEnabled = NotificationSettingsHelper.areNotificationsEnabled(context)
        if (!notifEnabled) {
            scope.launch {
                snackbarHostState.showSnackbar("请在系统设置中开启通知")
            }
        }
    }

    fun refreshNotifStatus() {
        notifEnabled = NotificationSettingsHelper.areNotificationsEnabled(context)
    }

    fun requestOrOpenNotification() {
        if (NotificationSettingsHelper.areNotificationsEnabled(context)) {
            NotificationSettingsHelper.postTestNotification(context)
            scope.launch { snackbarHostState.showSnackbar("已发送测试通知") }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        try {
            NotificationSettingsHelper.openAppNotificationSettings(context)
        } catch (_: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar("请在系统设置中开启通知")
            }
        }
    }

    fun shareClaimPack(chooserTitle: String, successMsg: String) {
        viewModel.exportClaimPack(
            onNeedPro = onOpenPaywall,
            onSuccess = { file ->
                try {
                    ExportShareHelper.shareFile(
                        context,
                        file,
                        "application/pdf",
                        chooserTitle,
                    )
                    scope.launch { snackbarHostState.showSnackbar(successMsg) }
                } catch (_: Exception) {
                    scope.launch { snackbarHostState.showSnackbar("分享失败") }
                }
            },
            onError = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DetailTopBar(
                showMore = ticket != null,
                onBack = onBack,
                onMore = { showActionsSheet = true },
            )
        },
        bottomBar = {
            val t = ticket
            if (t != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Button(
                            onClick = {
                                if (ChannelConfig.showProExport) {
                                    shareClaimPack("送修材料包", "已生成送修材料")
                                } else {
                                    onEdit(t.id)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = TicketKeepSpacing.page)
                                .padding(top = 12.dp, bottom = 24.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(TicketKeepRadius.button),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = if (ChannelConfig.showProExport) {
                                    Icons.Default.Share
                                } else {
                                    Icons.Default.Edit
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (ChannelConfig.showProExport) "准备送修材料" else "编辑票证",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        val t = ticket
        if (t == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(TicketKeepSpacing.page),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("票证已删除", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(TicketKeepRadius.button),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("返回票证册") }
            }
            return@Scaffold
        }

        val today = LocalDate.now()
        val daysLeft = t.warrantyEndEpochDay?.let { end ->
            (end - today.toEpochDay()).toInt()
        }
        val isSoon = daysLeft != null && daysLeft in 0..30
        val productLine = t.note.ifBlank { "暂无品名备注" }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TicketKeepSpacing.page, vertical = TicketKeepSpacing.md),
        ) {
            // 1. Summary hero
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = t.merchantName.ifBlank { "未命名商家" },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = productLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = MoneyFormats.formatYuan(t.amountCents),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 37.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-1.3).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    WarrantyStatusChip(
                        warrantyEndEpochDay = t.warrantyEndEpochDay,
                        showNoneLabel = true,
                    )
                    Spacer(Modifier.height(11.dp))
                    Text(
                        text = t.warrantyEndEpochDay?.let {
                            "保修至 ${DateFormats.formatEpochDay(it)}"
                        } ?: "尚未设置保修到期日",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 2. Soon warning
            if (isSoon && daysLeft != null) {
                Spacer(Modifier.height(TicketKeepSpacing.md))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = WarningContainer,
                    contentColor = OnWarningContainer,
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (daysLeft == 0) {
                                    "今天保修就到期了"
                                } else {
                                    "还有 $daysLeft 天，保修就到期了"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "如需检修，可以提前准备购买凭证。\n提醒时间：提前 7 天、1 天和到期当天。",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 20.sp,
                            ),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "查看通知状态 →",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = OnWarningContainer,
                            modifier = Modifier
                                .clickable {
                                    refreshNotifStatus()
                                    showNotifSheet = true
                                }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }

            // 3. 购买信息
            Spacer(Modifier.height(TicketKeepSpacing.md))
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "购买信息",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Spacer(Modifier.height(13.dp))
                    KvRow("购买日期", DateFormats.formatEpochDay(t.purchaseDateEpochDay))
                    KvRow("保修到期", DateFormats.formatEpochDay(t.warrantyEndEpochDay))
                    KvRow("备注", t.note.ifBlank { "—" }, last = true)
                }
            }

            // 4. 原始凭证（整卡可点）
            Spacer(Modifier.height(TicketKeepSpacing.md))
            PaperCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showReceiptSheet = true },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "原始凭证",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "点按查看完整票据",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    val imagePath = t.imagePath
                    if (!imagePath.isNullOrBlank()) {
                        AsyncImage(
                            model = File(imagePath),
                            contentDescription = "收据缩略图",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 56.dp, height = 72.dp)
                                .clip(RoundedCornerShape(TicketKeepRadius.field))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 72.dp)
                                .clip(RoundedCornerShape(TicketKeepRadius.field))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 5. OCR collapsible
            if (t.ocrRawText.isNotBlank()) {
                Spacer(Modifier.height(TicketKeepSpacing.md))
                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { ocrExpanded = !ocrExpanded },
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "查看识别原文",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (ocrExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (ocrExpanded) "收起" else "展开",
                            )
                        }
                        if (ocrExpanded) {
                            Spacer(Modifier.height(8.dp))
                            Text(t.ocrRawText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showActionsSheet && ticket != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val t = ticket!!
        ModalBottomSheet(
            onDismissRequest = { showActionsSheet = false },
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
                        "票证操作",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showActionsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(19.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(19.dp),
                        )
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    ActionSheetRow(
                        title = "编辑票证",
                        icon = Icons.Outlined.Edit,
                        onClick = {
                            showActionsSheet = false
                            onEdit(t.id)
                        },
                    )
                    if (ChannelConfig.showProExport) {
                        ActionSheetRow(
                            title = "导出 PDF / 送修材料",
                            icon = Icons.Outlined.Upload,
                            onClick = {
                                showActionsSheet = false
                                shareClaimPack("送修材料包", "已生成送修材料")
                            },
                        )
                    }
                    ActionSheetRow(
                        title = "删除这张票证",
                        icon = Icons.Outlined.Delete,
                        onClick = {
                            showActionsSheet = false
                            confirmDelete = true
                        },
                        danger = true,
                        showDivider = false,
                    )
                }
            }
        }
    }

    if (showReceiptSheet && ticket != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val imagePath = ticket!!.imagePath
        ModalBottomSheet(
            onDismissRequest = { showReceiptSheet = false },
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
                        "原始凭证",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showReceiptSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!imagePath.isNullOrBlank()) {
                    Text(
                        "双指缩放、上下滚动可查看完整票据。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    TallScrollableImage(
                        imagePath = imagePath,
                        contentDescription = "收据原图",
                        maxHeight = 480.dp,
                        corner = TicketKeepRadius.field,
                    )
                } else {
                    Text(
                        "未添加原始凭证",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "保修到期提醒",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showNotifSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "提前 7 天、1 天和到期当天提醒你。送达时间可能受手机后台限制影响。",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(TicketKeepRadius.field),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (notifEnabled) "通知已开启" else "通知尚未开启",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        requestOrOpenNotification()
                        refreshNotifStatus()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(TicketKeepRadius.button),
                ) {
                    Text(if (notifEnabled) "发送测试通知" else "开启通知")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这张票证？") },
            text = {
                Text("本地记录将被删除，对应保修提醒也会取消。此操作无法撤销。")
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("保留票证")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onBack)
                    },
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            shape = RoundedCornerShape(TicketKeepRadius.card),
        )
    }
}

/** 顶栏：返回 | 居中「票证详情」| ⋯；statusBarsPadding 一次 + top 8.dp。 */
@Composable
private fun DetailTopBar(
    showMore: Boolean,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp)
            .padding(horizontal = 4.dp)
            .height(48.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            "票证详情",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
        if (showMore) {
            IconButton(
                onClick = onMore,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(Icons.Default.MoreHoriz, contentDescription = "更多票证操作")
            }
        } else {
            Spacer(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp),
            )
        }
    }
}

@Composable
private fun ActionSheetRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = if (danger) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun KvRow(label: String, value: String, last: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = 18.dp),
            )
        }
        if (!last) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
