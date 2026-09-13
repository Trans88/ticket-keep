package com.ticketkeep.app.ui.screens.list

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.util.DateFormats
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    onOpenDetail: (Long) -> Unit,
    onCreateWithImage: (Uri) -> Unit,
    onCreateBlank: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAddSheet by remember { mutableStateOf(false) }
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

    fun tryAdd(action: () -> Unit) {
        scope.launch {
            if (viewModel.canAdd()) {
                action()
            } else {
                onOpenPaywall()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("票证记") },
                actions = {
                    if (!state.isPro) {
                        IconButton(onClick = onOpenPaywall) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = "升级 Pro")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { tryAdd { showAddSheet = true } },
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
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.isPro) {
                    "已保存 ${state.count} 条 · Pro"
                } else {
                    "已保存 ${state.count} / ${state.freeLimit} 条（免费上限）"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (showNotificationBanner) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "保修提醒需要通知权限",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "未开启时，保修到期提醒可能无法送达。可再次授权，或到系统设置中打开。",
                            style = MaterialTheme.typography.bodySmall,
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
                Spacer(Modifier.height(8.dp))
            }

            if (state.tickets.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("还没有票证", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "拍照或从相册选择收据，自动 OCR 后可手改保存。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.tickets, key = { it.id }) { ticket ->
                        TicketRow(ticket = ticket, onClick = { onOpenDetail(ticket.id) })
                    }
                }
            }
        }
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
                            tryAdd {
                                pickMedia.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
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
        )
    }
}

@Composable
private fun TicketRow(ticket: Ticket, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!ticket.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = File(ticket.imagePath),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ticket.merchantName.ifBlank { "未命名商家" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = MoneyFormats.formatYuan(ticket.amountCents),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "保修至 ${DateFormats.formatEpochDay(ticket.warrantyEndEpochDay)}",
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
