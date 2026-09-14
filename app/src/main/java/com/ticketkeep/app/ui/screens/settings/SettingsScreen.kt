package com.ticketkeep.app.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ticketkeep.app.notification.NotificationSettingsHelper
import com.ticketkeep.app.ui.components.PaperCard
import kotlinx.coroutines.launch
import android.content.pm.PackageManager

/**
 * 简单设置页：通知权限状态、测试通知、电池优化与国产机自启动提示。
 * 不弹精确闹钟权限；WorkManager 调度保持不变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var notifEnabled by remember {
        mutableStateOf(NotificationSettingsHelper.areNotificationsEnabled(context))
    }
    var batteryIgnored by remember {
        mutableStateOf(NotificationSettingsHelper.isIgnoringBatteryOptimizations(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifEnabled = NotificationSettingsHelper.areNotificationsEnabled(context)
                batteryIgnored = NotificationSettingsHelper.isIgnoringBatteryOptimizations(context)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            Text("通知", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = if (notifEnabled) "通知权限：已授予" else "通知权限：未授予 / 已关闭",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "保修到期提醒依赖系统通知。若收不到，请先确认权限与渠道开关。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { requestOrOpenNotificationPermission() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (notifEnabled) "打开应用通知设置" else "再次请求通知权限")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                NotificationSettingsHelper.postTestNotification(context)
                                scope.launch {
                                    snackbarHostState.showSnackbar("已发送测试通知")
                                }
                            } catch (e: Exception) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "发送失败：${e.message ?: "请检查通知权限"}",
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("发送测试通知")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("通知收不到？", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = if (batteryIgnored) {
                            "电池优化：已忽略（有利于后台提醒）"
                        } else {
                            "电池优化：未忽略（部分机型会推迟后台任务）"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "允许「票证记」忽略电池优化，可降低保修提醒被系统杀掉的概率。不会改动 WorkManager 调度逻辑。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val ok = NotificationSettingsHelper.requestIgnoreBatteryOptimizations(context)
                            if (!ok) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("无法打开电池优化设置，请到系统设置中手动操作")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("请求忽略电池优化")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val ok = NotificationSettingsHelper.openBatteryOptimizationSettings(context)
                            if (!ok) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("无法打开系统电池优化列表")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("打开电池优化设置")
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "国产手机自启动 / 后台提示",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "小米 / 华为 / OPPO / vivo / 荣耀等机型可能额外限制后台。若仍收不到提醒，请到系统设置中为「票证记」开启自启动、后台运行或「允许关联启动」（路径因品牌而异，无统一系统入口）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
