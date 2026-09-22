package com.ticketkeep.app.ui.screens.list

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import com.ticketkeep.app.notification.NotificationSettingsHelper
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ticketkeep.app.R
import com.ticketkeep.app.channel.ChannelConfig
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.export.TicketCsvImporter
import com.ticketkeep.app.ui.components.PaperCard
import com.ticketkeep.app.ui.components.TicketPaperCard
import com.ticketkeep.app.ui.components.WarrantyStatusChip
import com.ticketkeep.app.ui.theme.AmountTextStyle
import com.ticketkeep.app.ui.theme.DarkHeroBackground
import com.ticketkeep.app.ui.theme.DarkOnHero
import com.ticketkeep.app.ui.theme.DarkOnHeroSecondary
import com.ticketkeep.app.ui.theme.LightAccent
import com.ticketkeep.app.ui.theme.LightHeroBackground
import com.ticketkeep.app.ui.theme.LightOnHero
import com.ticketkeep.app.ui.theme.LightOnHeroSecondary
import com.ticketkeep.app.ui.theme.TicketKeepRadius
import com.ticketkeep.app.ui.theme.TicketKeepSpacing
import com.ticketkeep.app.util.DateBounds
import com.ticketkeep.app.util.DateFormats
import com.ticketkeep.app.util.ImageStorage
import com.ticketkeep.app.util.MoneyFormats
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * 票证首页 — 对齐 index.html `home()`：
 * 标题「票证记.」+ slogan + 仅铃铛；深色英雄卡（有数据时）；
 * 搜索+筛选；chips；分区头「最近收好」空态也显示（0 张 · 最近添加）；
 * emptyContent(true) 单 CTA「存入第一张票证」开加票 sheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    onOpenDetail: (Long) -> Unit,
    onCreateWithImage: (Uri) -> Unit,
    onCreateWithImages: (List<Uri>) -> Unit = {},
    onCreateBlank: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenPrivacy: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    addTrigger: Int = 0,
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAddSheet by remember { mutableStateOf(false) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var showFilterStartPicker by remember { mutableStateOf(false) }
    var showFilterEndPicker by remember { mutableStateOf(false) }
    var showMoreFilters by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<TicketCsvImporter.Preview?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val imageStorage = remember { ImageStorage(context.applicationContext) }

    val bannerPrefs = remember {
        context.getSharedPreferences(BANNER_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var notificationGranted: Boolean by remember {
        mutableStateOf(isPostNotificationsGranted(context))
    }
    var notificationBannerDismissed: Boolean by remember {
        mutableStateOf(bannerPrefs.getBoolean(KEY_NOTIF_BANNER_DISMISSED, false))
    }
    var proBannerDismissed: Boolean by remember {
        mutableStateOf(bannerPrefs.getBoolean(KEY_PRO_BANNER_DISMISSED, false))
    }
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
            !notificationBannerDismissed &&
            state.totalCount > 0
    val showProBanner =
        !state.isPro &&
            ChannelConfig.showProPurchase &&
            !proBannerDismissed &&
            !showNotificationBanner &&
            state.totalCount > 0

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted || isPostNotificationsGranted(context)
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val capped = if (uris.size > 10) {
            scope.launch { snackbarHostState.showSnackbar("最多 10 张") }
            uris.take(10)
        } else {
            uris
        }
        when {
            capped.size == 1 -> onCreateWithImage(capped.first())
            else -> onCreateWithImages(capped)
        }
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
        // 相册批量允许在名额为 0 时进入核对页，保存时再门禁；故打开 sheet 不预检额度
        showAddSheet = true
    }

    // 底栏用递增计数触发打开；List 离场再回来时 addTrigger 仍 >0，
    // 必须记住已消费值，否则存完票 / 从「我的」回首页会再次弹出。
    var consumedAddTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(addTrigger) {
        if (addTrigger > 0 && addTrigger != consumedAddTrigger) {
            consumedAddTrigger = addTrigger
            openAddSheet()
        }
    }

    fun launchGallery() {
        // 多选相册：不预检额度（规则：可先选再在批量页决定子集 / 升级）
        pickMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
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

    val isFirstEmpty = state.totalCount == 0 && !state.isFilterOrSearchActive
    val showHero = !state.isFilterOrSearchActive && state.query.isBlank() // design SoT: hero always, even 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = TicketKeepSpacing.page),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 整页正文同一 LazyColumn：顶栏/英雄卡/搜索筛选/分区头/列表同滚；底栏在外层固定。
            item(key = "home_header") {
                // —— 自定义标题区（非挤在一起的 TopAppBar）——
                // 顶栏仅铃铛（对齐原型）；CSV/隐私/Pro 在「我的」等 overflow 入口
                HomeHeader(
                    onBellClick = { showReminderSheet = true },
                )
            }

            if (showHero) {
                item(key = "summary_hero") {
                    TicketSummaryHero(
                        totalCount = state.totalCount,
                        soonCount = state.soonCount,
                        onActionClick = {
                            if (state.soonCount > 0) {
                                viewModel.applySoonFilter()
                            } else {
                                viewModel.clearFilter()
                                viewModel.setQuickFilter(ListQuickFilter.ALL)
                            }
                        },
                    )
                    Spacer(Modifier.height(TicketKeepSpacing.md))
                }
            }

            item(key = "search_filter") {
                // —— 搜索行 + 筛选 iconbtn（真机缺口）——
                SearchFilterRow(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onFilterClick = { showMoreFilters = true },
                )
                Spacer(Modifier.height(10.dp))

                QuickFilterChips(
                    quickFilter = state.quickFilter,
                    onQuick = viewModel::setQuickFilter,
                )
                Spacer(Modifier.height(4.dp))
            }

            item(key = "section_head") {
                // 空态也显示分区头（对齐原型 home()：section-head + emptyContent）
                SectionHeadRow(
                    title = when {
                        state.query.isNotBlank() -> "搜索结果"
                        state.quickFilter != ListQuickFilter.ALL || state.filter.hasActiveConstraints ->
                            "筛选结果"
                        else -> "最近收好"
                    },
                    countLabel = "${state.tickets.size} 张 · 最近添加",
                )
            }

            if (showNotificationBanner) {
                item(key = "notif_banner") {
                    NotifBanner(
                        onDismiss = {
                            notificationBannerDismissed = true
                            bannerPrefs.edit().putBoolean(KEY_NOTIF_BANNER_DISMISSED, true).apply()
                        },
                        onEnable = {
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
                    )
                    Spacer(Modifier.height(12.dp))
                }
            } else if (showProBanner) {
                item(key = "pro_banner") {
                    ProBanner(
                        onOpenPaywall = onOpenPaywall,
                        onDismiss = {
                            proBannerDismissed = true
                            bannerPrefs.edit().putBoolean(KEY_PRO_BANNER_DISMISSED, true).apply()
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (state.tickets.isEmpty()) {
                item(key = "empty_tickets") {
                    EmptyTicketContent(
                        firstRun = isFirstEmpty || (!state.isFilterOrSearchActive && state.totalCount == 0),
                        onPrimary = {
                            if (state.isFilterOrSearchActive || state.query.isNotBlank()) {
                                viewModel.clearFilter()
                                viewModel.onQueryChange("")
                                showMoreFilters = false
                            } else {
                                openAddSheet()
                            }
                        },
                    )
                }
            } else {
                items(
                    items = state.tickets,
                    key = { it.id },
                ) { ticket ->
                    TicketRow(ticket = ticket, onClick = { onOpenDetail(ticket.id) })
                    Spacer(Modifier.height(10.dp))
                }
                item(key = "local_lock_footer") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "票证保存在本机，备份由你决定",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showMoreFilters) {
        MoreFiltersDialog(
            filter = state.filter,
            onDismiss = { showMoreFilters = false },
            onExpiry = {
                viewModel.setExpiryBucket(it)
            },
            onRangeField = viewModel::setRangeField,
            onPickStart = {
                showMoreFilters = false
                showFilterStartPicker = true
            },
            onPickEnd = {
                showMoreFilters = false
                showFilterEndPicker = true
            },
            onClearRangeStart = { viewModel.setRangeStart(null) },
            onClearRangeEnd = { viewModel.setRangeEnd(null) },
            onClearAll = {
                viewModel.clearFilter()
                viewModel.setQuickFilter(ListQuickFilter.ALL)
                showMoreFilters = false
            },
            onApply = { showMoreFilters = false },
        )
    }

    if (showFilterStartPicker) {
        ListFilterDatePickerDialog(
            initial = state.filter.rangeStart ?: LocalDate.now(),
            onDismiss = { showFilterStartPicker = false },
            onConfirm = {
                viewModel.setRangeStart(it)
                showFilterStartPicker = false
                showMoreFilters = true
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
                showMoreFilters = true
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
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
                    "存一张票证",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "拍下来、选一张，或从空白开始。",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 21.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
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
                    SheetActionRow(
                        icon = Icons.Default.CameraAlt,
                        title = "拍照识别",
                        subtitle = "打开相机，拍下收据或保修单",
                        onClick = {
                            showAddSheet = false
                            launchCamera()
                        },
                    )
                    SheetActionRow(
                        icon = Icons.Default.PhotoLibrary,
                        title = "从相册选择",
                        subtitle = "可多选，最多一次 10 张",
                        onClick = {
                            showAddSheet = false
                            launchGallery()
                        },
                    )
                    SheetActionRow(
                        icon = Icons.Default.Edit,
                        title = "手动填写",
                        subtitle = "没有照片也可以记录",
                        onClick = {
                            showAddSheet = false
                            tryAdd { onCreateBlank() }
                        },
                        showDivider = false,
                    )
                }
            }
        }
    }

    if (showReminderSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showReminderSheet = false },
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
                        text = if (notificationGranted) "通知已开启" else "通知尚未开启",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (notificationGranted) {
                            NotificationSettingsHelper.postTestNotification(context)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            NotificationSettingsHelper.openAppNotificationSettings(context)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text(
                        if (notificationGranted) "发送测试通知" else "开启通知",
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
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 65.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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


/** 首页顶栏：标题「票证记.」+ slogan；仅铃铛（无三点） */
@Composable
private fun HomeHeader(
    onBellClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "票证记",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                        lineHeight = 36.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    ".",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "重要的凭证，好好收着。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        QuietIconButton(onClick = onBellClick, contentDescription = "保修提醒") {
            Icon(Icons.Default.Notifications, contentDescription = "保修提醒")
        }
    }
}

@Composable
private fun QuietIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // contentDescription carried by caller Icon when possible
        content()
    }
}

@Composable
private fun SearchFilterRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(49.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(15.dp),
                )
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "搜索商家、备注",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotBlank()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "清除搜索",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        QuietIconButton(onClick = onFilterClick, contentDescription = "筛选票证") {
            Icon(Icons.Default.Tune, contentDescription = "筛选票证")
        }
    }
}

@Composable
private fun QuickFilterChips(
    quickFilter: ListQuickFilter,
    onQuick: (ListQuickFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProtoChip("全部", quickFilter == ListQuickFilter.ALL) {
            onQuick(ListQuickFilter.ALL)
        }
        Spacer(Modifier.width(5.dp))
        ProtoChip("即将到期", quickFilter == ListQuickFilter.SOON) {
            onQuick(ListQuickFilter.SOON)
        }
        Spacer(Modifier.width(5.dp))
        ProtoChip("未过期", quickFilter == ListQuickFilter.NOT_EXPIRED) {
            onQuick(ListQuickFilter.NOT_EXPIRED)
        }
        Spacer(Modifier.width(5.dp))
        ProtoChip("未设保修", quickFilter == ListQuickFilter.NO_WARRANTY) {
            onQuick(ListQuickFilter.NO_WARRANTY)
        }
    }
}

@Composable
private fun ProtoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // Align index.html `.chip`: minH 48, pad 14×9, r12;
    // selected primaryContainer #EAF0E6 + primary #285A45 (via theme tokens).
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = fg,
            maxLines = 1,
        )
    }
}


@Composable
private fun SectionHeadRow(title: String, countLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 17.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            countLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 临期摘要英雄卡 — 对齐 `.hero`：primary bg、r22、minH 168、padding 22、
 * WARRANTY NOTES、双圈 circle-art、旋转 mini-ticket、h3、已收好 N、lime CTA。
 */
@Composable
private fun TicketSummaryHero(
    totalCount: Int,
    soonCount: Int,
    onActionClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val heroBg = if (dark) DarkHeroBackground else LightHeroBackground
    val onHero = if (dark) DarkOnHero else LightOnHero
    val onHeroSecondary = if (dark) DarkOnHeroSecondary else LightOnHeroSecondary
    val eyebrow = if (dark) onHeroSecondary else androidx.compose.ui.graphics.Color(0xFFD0DDCF)
    val lime = LightAccent
    val ring = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f)
    val ringInner = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f)
    val miniBg = androidx.compose.ui.graphics.Color(0xFFDFECC7)
    val dash = androidx.compose.ui.graphics.Color(0x40285A45)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .clip(RoundedCornerShape(TicketKeepRadius.card))
            .background(heroBg),
    ) {
        // .circle-art：右上外溢双环
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = 19.dp)
                .size(170.dp)
                .border(1.dp, ring, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(122.dp)
                    .border(1.dp, ringInner, RoundedCornerShape(50)),
            )
        }
        // .mini-ticket：浅绿票根 + 盾标 + 虚线
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 43.dp, end = 25.dp)
                .graphicsLayer { rotationZ = 13f }
                .width(57.dp)
                .height(79.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(miniBg)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(27.dp),
                tint = heroBg,
            )
            Spacer(Modifier.height(9.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                drawLine(
                    color = dash,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
        ) {
            Text(
                "WARRANTY NOTES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                ),
                color = eyebrow,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (soonCount > 0) {
                    "有 $soonCount 张票证，\n保修快到期了。"
                } else {
                    "票证已收好，\n最近没有临期保修。"
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 31.sp,
                ),
                color = onHero,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "已收好 $totalCount 张票证",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = onHeroSecondary,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = lime),
            ) {
                Text(
                    if (soonCount > 0) "查看 30 天内到期 ›" else "查看全部票证 ›",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = lime,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = lime,
                )
            }
        }
    }
}



/**
 * emptyContent(first) 文案对齐 index.html `.empty`（avatar 89/r28、单 CTA）。
 */
@Composable
private fun EmptyTicketContent(
    firstRun: Boolean,
    onPrimary: () -> Unit,
) {
    val paperLine = androidx.compose.ui.graphics.Color(0xFFE6E8DF)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(89.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .then(
                    if (firstRun) {
                        Modifier.drawBehind {
                            val step = 6.dp.toPx()
                            var y = step
                            while (y < size.height) {
                                drawLine(
                                    color = paperLine.copy(alpha = 0.55f),
                                    start = Offset(10.dp.toPx(), y),
                                    end = Offset(size.width - 10.dp.toPx(), y),
                                    strokeWidth = 1.dp.toPx(),
                                )
                                y += step
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (firstRun) Icons.Default.ReceiptLong else Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (firstRun) "给重要的票证，一个家" else "没有找到这张票证",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (firstRun) {
                "拍下收据或保修单，留好每一份凭证。\n无需注册，即可保存在手机里。"
            } else {
                "换个商家名称或备注关键词，\n也可以清除筛选再找找。"
            },
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 22.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .height(52.dp),
            shape = RoundedCornerShape(TicketKeepRadius.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                if (firstRun) "存入第一张票证" else "清除搜索与筛选",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}



@Composable
private fun TicketRow(ticket: Ticket, onClick: () -> Unit) {
    TicketPaperCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 99.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!ticket.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = File(ticket.imagePath),
                    contentDescription = "票证缩略图",
                    modifier = Modifier
                        .width(44.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(9.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // .thumb.paper：横纹纸 + 6dp 边 + 微旋 -3°
                val line = androidx.compose.ui.graphics.Color(0xFFE6E8DF)
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(54.dp)
                        .graphicsLayer { rotationZ = -3f }
                        .border(
                            6.dp,
                            androidx.compose.ui.graphics.Color(0xFFF0F1E8),
                            RoundedCornerShape(9.dp),
                        )
                        .clip(RoundedCornerShape(9.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFFFFFFFF))
                        .drawBehind {
                            val step = 6.dp.toPx()
                            var y = step
                            while (y < size.height) {
                                drawLine(
                                    color = line,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx(),
                                )
                                y += step
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFF6B725E),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ticket.merchantName.ifBlank { "未命名商家" },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val note = ticket.note.trim().ifBlank { "暂无备注" }
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Text(
                    text = if (ticket.purchaseDateEpochDay != null) {
                        DateFormats.formatEpochDay(ticket.purchaseDateEpochDay)
                    } else {
                        "未设购买日"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MoneyFormats.formatYuan(ticket.amountCents),
                    style = AmountTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                WarrantyStatusChip(
                    warrantyEndEpochDay = ticket.warrantyEndEpochDay,
                    showNoneLabel = true,
                )
            }
        }
    }
}

@Composable
private fun NotifBanner(onDismiss: () -> Unit, onEnable: () -> Unit) {
    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        borderColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("保修提醒需要通知权限", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "未开启时，保修到期提醒可能无法送达。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("稍后") }
                TextButton(onClick = onEnable) { Text("去开启") }
            }
        }
    }
}

@Composable
private fun ProBanner(onOpenPaywall: () -> Unit, onDismiss: () -> Unit) {
    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        borderColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("需要更多条数？", style = MaterialTheme.typography.titleMedium)
            Text("Pro 可无限保存，本地优先。", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("关闭") }
                OutlinedButton(onClick = onOpenPaywall) { Text("了解 Pro") }
            }
        }
    }
}

@Composable
private fun MoreFiltersDialog(
    filter: TicketListFilterState,
    onDismiss: () -> Unit,
    onExpiry: (ExpiryBucket) -> Unit,
    onRangeField: (DateRangeField) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onClearRangeStart: () -> Unit,
    onClearRangeEnd: () -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选票证") },
        text = {
            Column {
                Text("按保修状态，缩小查找范围。")
                Spacer(Modifier.height(12.dp))
                ProtoChip("已过期", filter.expiry == ExpiryBucket.EXPIRED) {
                    onExpiry(ExpiryBucket.EXPIRED)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(
                        selected = filter.rangeField == DateRangeField.WARRANTY_END,
                        onClick = { onRangeField(DateRangeField.WARRANTY_END) },
                        label = { Text("按到期日") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = filter.rangeField == DateRangeField.PURCHASE,
                        onClick = { onRangeField(DateRangeField.PURCHASE) },
                        label = { Text("按购买日") },
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onPickStart) {
                    Text("起：" + (filter.rangeStart?.let { DateFormats.display.format(it) } ?: "未选"))
                }
                if (filter.rangeStart != null) {
                    TextButton(onClick = onClearRangeStart) { Text("清除起") }
                }
                TextButton(onClick = onPickEnd) {
                    Text("止：" + (filter.rangeEnd?.let { DateFormats.display.format(it) } ?: "未选"))
                }
                if (filter.rangeEnd != null) {
                    TextButton(onClick = onClearRangeEnd) { Text("清除止") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onClearAll) { Text("清除") }
        },
        shape = RoundedCornerShape(TicketKeepRadius.card),
    )
}

private const val BANNER_PREFS_NAME = "ui_banner_prefs"
private const val KEY_NOTIF_BANNER_DISMISSED = "notif_banner_dismissed"
private const val KEY_PRO_BANNER_DISMISSED = "pro_banner_dismissed"

private fun isPostNotificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
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
        shape = RoundedCornerShape(TicketKeepRadius.card),
    ) {
        DatePicker(state = pickerState)
    }
}
