package com.ticketkeep.app.ui.screens.list

import androidx.compose.runtime.LaunchedEffect
import com.ticketkeep.app.widget.WidgetIntents

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.ticketkeep.app.util.DateBounds
import com.ticketkeep.app.util.DateFormats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ticketkeep.app.R
import com.ticketkeep.app.channel.ChannelConfig
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.ui.components.PaperCard
import com.ticketkeep.app.ui.components.WarrantyStatusChip
import com.ticketkeep.app.util.ImageStorage
import com.ticketkeep.app.util.MoneyFormats
import java.io.File
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.ticketkeep.app.export.ExportShareHelper
import com.ticketkeep.app.export.TicketCsvImporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    initialExpiryFilter: String? = null,
    onOpenDetail: (Long) -> Unit,
    onCreateWithImage: (Uri) -> Unit,
    onCreateBlank: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenPrivacy: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialExpiryFilter) {
        when (initialExpiryFilter) {
            WidgetIntents.FILTER_NOT_EXPIRED -> viewModel.setExpiryBucket(ExpiryBucket.NOT_EXPIRED)
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAddSheet by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showFilterStartPicker by remember { mutableStateOf(false) }
    var showFilterEndPicker by remember { mutableStateOf(false) }
    var showRangePanel by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<TicketCsvImporter.Preview?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val imageStorage = remember { ImageStorage(context.applicationContext) }

    var notificationGranted: Boolean by remember {
        mutableStateOf(isPostNotificationsGranted(context))
    }
    var notificationBannerDismissed: Boolean by remember { mutableStateOf(false) }
    var notificationRequestedOnce: Boolean by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = isPostNotificationsGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val showNotificationBanner =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationGranted &&
            !notificationBannerDismissed

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted || isPostNotificationsGranted(context)
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onCreateWithImage(uri)
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) onCreateWithImage(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val (uri, _) = imageStorage.createCameraCacheUri()
            pendingCameraUri = uri
            takePicture.launch(uri)
        }
    }


    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.prepareCsvImport(
            uri = uri,
            onNeedPro = onOpenPaywall,
            onPreview = { preview -> importPreview = preview },
            onError = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            },
        )
    }

    fun tryAdd(action: () -> Unit) {
        scope.launch {
            if (viewModel.canAdd()) {
                action()
            } else {
                onOpenPaywall()
            }
        }
    }

    fun openAddSheet() {
        tryAdd { showAddSheet = true }
    }

    fun launchGallery() {
        tryAdd {
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    fun launchCamera() {
        tryAdd {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                val (uri, _) = imageStorage.createCameraCacheUri()
                pendingCameraUri = uri
                takePicture.launch(uri)
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("票证记") },
                actions = {
                    if (ChannelConfig.showProPurchase && !state.isPro) {
                        IconButton(onClick = onOpenPaywall) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = "升级 Pro")
                        }
                    }
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
                        if (ChannelConfig.showProExport) {
                            DropdownMenuItem(
                                text = { Text("导出全部 CSV") },
                                onClick = {
                                    showOverflow = false
                                    viewModel.exportAllCsv(
                                        onNeedPro = onOpenPaywall,
                                        onSuccess = { file ->
                                            try {
                                                ExportShareHelper.shareFile(
                                                    context,
                                                    file,
                                                    "text/csv",
                                                    "导出票证 CSV",
                                                )
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("已导出 CSV")
                                                }
                                            } catch (_: Exception) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("分享失败")
                                                }
                                            }
                                        },
                                        onError = { msg ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        },
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("从 CSV 导入") },
                                onClick = {
                                    showOverflow = false
                                    if (!state.isPro) {
                                        onOpenPaywall()
                                    } else {
                                        importCsvLauncher.launch(
                                            arrayOf(
                                                "text/*",
                                                "text/csv",
                                                "text/comma-separated-values",
                                                "application/csv",
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.privacy_policy)) },
                            onClick = {
                                showOverflow = false
                                onOpenPrivacy()
                            },
                        )
                    
                        DropdownMenuItem(
                            text = { Text("设置") },
                            onClick = {
                                showOverflow = false
                                onOpenSettings()
                            },
                        )}
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openAddSheet() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加票证")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索商家或备注") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            ListFilterBar(
                filter = state.filter,
                showRangePanel = showRangePanel,
                onToggleRangePanel = { showRangePanel = !showRangePanel },
                onExpiry = viewModel::setExpiryBucket,
                onRangeField = viewModel::setRangeField,
                onPickStart = { showFilterStartPicker = true },
                onPickEnd = { showFilterEndPicker = true },
                onClearRangeStart = { viewModel.setRangeStart(null) },
                onClearRangeEnd = { viewModel.setRangeEnd(null) },
                onClearAll = {
                    viewModel.clearFilter()
                    showRangePanel = false
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildListCountLabel(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (!state.isPro) {
                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    borderColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "需要更多条数？",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Pro 可无限保存，本地优先。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        OutlinedButton(onClick = onOpenPaywall) {
                            Text("了解 Pro")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (showNotificationBanner) {
                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    borderColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "保修提醒需要通知权限",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "未开启时，保修到期提醒可能无法送达。可再次授权，或到系统设置中打开。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { notificationBannerDismissed = true }) {
                                Text("稍后")
                            }
                            TextButton(
                                onClick = {
                                    val activity = context as? Activity
                                    val shouldShow = activity?.shouldShowRequestPermissionRationale(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) == true
                                    val openSettings = notificationRequestedOnce && !shouldShow
                                    if (openSettings) {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null),
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        notificationRequestedOnce = true
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                            ) {
                                Text("去开启")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.tickets.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_empty_ticket),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (state.isFilterOrSearchActive) "没有符合条件的票证" else stringResource(R.string.empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.isFilterOrSearchActive) {
                            "试试清除筛选，或换个关键词。"
                        } else {
                            stringResource(R.string.empty_body)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (state.isFilterOrSearchActive) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.clearFilter()
                                viewModel.onQueryChange("")
                                showRangePanel = false
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("清除筛选与搜索") }
                    }
                    if (!state.isFilterOrSearchActive) {
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { launchCamera() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.empty_action_camera))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { launchGallery() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.empty_action_gallery))
                    }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.tickets, key = { it.id }) { ticket ->
                        TicketRow(ticket = ticket, onClick = { onOpenDetail(ticket.id) })
                    }
                }
            }
        }
    }



    if (showFilterStartPicker) {
        ListFilterDatePickerDialog(
            initial = state.filter.rangeStart ?: LocalDate.now(),
            onDismiss = { showFilterStartPicker = false },
            onConfirm = {
                viewModel.setRangeStart(it)
                showFilterStartPicker = false
                showRangePanel = true
            },
        )
    }
    if (showFilterEndPicker) {
        ListFilterDatePickerDialog(
            initial = state.filter.rangeEnd ?: LocalDate.now(),
            onDismiss = { showFilterEndPicker = false },
            onConfirm = {
                viewModel.setRangeEnd(it)
                showFilterEndPicker = false
                showRangePanel = true
            },
        )
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("导入预览") },
            text = {
                Column {
                    Text("将新增 ${preview.tickets.size} 条票证（一律作为新记录；无效图片路径会清空）。")
                    if (preview.skippedRows > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("跳过 ${preview.skippedRows} 行。")
                    }
                    if (preview.rowWarnings.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            preview.rowWarnings.take(3).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                                    val extra = if (skipped > 0) "，跳过 ${skipped} 行" else ""
                                    snackbarHostState.showSnackbar("已导入 ${inserted} 条$extra")
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

    if (showAddSheet) {
        AlertDialog(
            onDismissRequest = { showAddSheet = false },
            title = { Text("添加票证") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showAddSheet = false
                            launchGallery()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("从相册选择")
                    }
                    TextButton(
                        onClick = {
                            showAddSheet = false
                            launchCamera()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("拍照")
                    }
                    TextButton(
                        onClick = {
                            showAddSheet = false
                            tryAdd { onCreateBlank() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("手动录入（无图）")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddSheet = false }) { Text("取消") }
            },
            shape = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun TicketRow(ticket: Ticket, onClick: () -> Unit) {
    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!ticket.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = File(ticket.imagePath),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = ticket.merchantName.ifBlank { "未命名商家" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    WarrantyStatusChip(warrantyEndEpochDay = ticket.warrantyEndEpochDay)
                }
                Spacer(Modifier.height(4.dp))
                val datePart = DateFormats.formatEpochDay(ticket.purchaseDateEpochDay)
                val amountPart = MoneyFormats.formatYuan(ticket.amountCents)
                Text(
                    text = "$datePart · $amountPart",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun isPostNotificationsGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}


private fun buildListCountLabel(state: ListUiState): String {
    val shown = state.tickets.size
    val base = if (state.isPro) {
        "已保存 ${state.totalCount} 条 · Pro"
    } else {
        "已保存 ${state.totalCount} / ${state.freeLimit} 条（免费上限）"
    }
    return if (state.isFilterOrSearchActive) {
        "$base · 显示 $shown 条"
    } else {
        base
    }
}

/**
 * 列表筛选条：到期桶芯片 + 可选时间范围；与搜索叠加。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListFilterBar(
    filter: TicketListFilterState,
    showRangePanel: Boolean,
    onToggleRangePanel: () -> Unit,
    onExpiry: (ExpiryBucket) -> Unit,
    onRangeField: (DateRangeField) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onClearRangeStart: () -> Unit,
    onClearRangeEnd: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpiryChip("全部", filter.expiry == ExpiryBucket.ALL) { onExpiry(ExpiryBucket.ALL) }
            Spacer(Modifier.width(8.dp))
            ExpiryChip("未过期", filter.expiry == ExpiryBucket.NOT_EXPIRED) { onExpiry(ExpiryBucket.NOT_EXPIRED) }
            Spacer(Modifier.width(8.dp))
            ExpiryChip("已过期", filter.expiry == ExpiryBucket.EXPIRED) { onExpiry(ExpiryBucket.EXPIRED) }
            Spacer(Modifier.width(8.dp))
            ExpiryChip("无到期", filter.expiry == ExpiryBucket.NO_END) { onExpiry(ExpiryBucket.NO_END) }
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = showRangePanel || filter.rangeStart != null || filter.rangeEnd != null,
                onClick = onToggleRangePanel,
                label = { Text("时间范围") },
                shape = RoundedCornerShape(8.dp),
            )
            if (filter.hasActiveConstraints) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClearAll) { Text("清除筛选") }
            }
        }
        if (showRangePanel || filter.rangeStart != null || filter.rangeEnd != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "无对应日期的票证不会出现在时间范围结果中；未过期不含「无到期」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = filter.rangeField == DateRangeField.WARRANTY_END,
                    onClick = { onRangeField(DateRangeField.WARRANTY_END) },
                    label = { Text("按到期日") },
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = filter.rangeField == DateRangeField.PURCHASE,
                    onClick = { onRangeField(DateRangeField.PURCHASE) },
                    label = { Text("按购买日") },
                    shape = RoundedCornerShape(8.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPickStart) {
                    Text(
                        "起：" + (filter.rangeStart?.let { DateFormats.display.format(it) } ?: "未选"),
                    )
                }
                if (filter.rangeStart != null) {
                    TextButton(onClick = onClearRangeStart) { Text("清除起") }
                }
                TextButton(onClick = onPickEnd) {
                    Text(
                        "止：" + (filter.rangeEnd?.let { DateFormats.display.format(it) } ?: "未选"),
                    )
                }
                if (filter.rangeEnd != null) {
                    TextButton(onClick = onClearRangeEnd) { Text("清除止") }
                }
            }
        }
    }
}

@Composable
private fun ExpiryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListFilterDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val clamped = initial.coerceIn(DateBounds.MIN, DateBounds.max())
    val millis = clamped.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = millis,
        yearRange = DateBounds.yearRange(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = pickerState.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                    if (!DateBounds.isAllowed(date)) return@TextButton
                    onConfirm(date)
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(16.dp),
    ) {
        DatePicker(state = pickerState)
    }
}

