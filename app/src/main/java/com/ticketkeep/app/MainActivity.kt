package com.ticketkeep.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ticketkeep.app.notification.WarrantyReminderWorker
import com.ticketkeep.app.ui.navigation.HomeBottomBar
import com.ticketkeep.app.ui.navigation.HomeBottomBarContainerMinHeight
import com.ticketkeep.app.ui.navigation.LocalHomeBottomBarHeight
import com.ticketkeep.app.ui.navigation.rememberHomeGlassBlurEnabled
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.ticketkeep.app.ui.navigation.Routes
import com.ticketkeep.app.ui.navigation.shouldShowHomeBottomBar
import com.ticketkeep.app.ui.screens.backup.CloudBackupScreen
import com.ticketkeep.app.ui.screens.detail.DetailScreen
import com.ticketkeep.app.ui.screens.edit.EditScreen
import com.ticketkeep.app.ui.screens.list.ListScreen
import com.ticketkeep.app.ui.screens.paywall.PaywallScreen
import com.ticketkeep.app.ui.screens.privacy.PrivacyPolicyScreen
import com.ticketkeep.app.ui.screens.settings.SettingsScreen
import com.ticketkeep.app.ui.screens.splash.BrandLaunchFrame
import com.ticketkeep.app.ui.theme.TicketKeepTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import com.ticketkeep.app.ui.screens.batch.BatchImportSession
import com.ticketkeep.app.ui.screens.batch.BatchReviewScreen
import javax.inject.Inject
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity

/**
 * 应用唯一 Activity：Compose 导航、Splash、通知深链与权限请求入口。
 * v2：底栏「票证 | 存一张票证 | 我的」；宽胶囊添加触发传给 ListScreen；详情等全屏隐藏底栏。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var batchImportSession: BatchImportSession

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        val openTicketId = intent?.getLongExtra(WarrantyReminderWorker.EXTRA_TICKET_ID, -1L) ?: -1L

        setContent {
            TicketKeepTheme {
                var showBrandFrame by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(350L)
                    showBrandFrame = false
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        val showBottomBar = shouldShowHomeBottomBar(currentRoute)
                        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                        val dockVisible = showBottomBar && !imeVisible
                        val glassState = remember { HazeState() }
                        val glassBlurEnabled = rememberHomeGlassBlurEnabled()
                        var dockHeight by remember { mutableStateOf(HomeBottomBarContainerMinHeight) }
                        val activity = LocalContext.current as? Activity
                        /** 递增后 ListScreen 打开添加面板（底栏中心按钮） */
                        var addTrigger by remember { mutableIntStateOf(0) }

                        fun navigateTab(route: String) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }

                        BackHandler(enabled = showBottomBar) {
                            when (currentRoute) {
                                Routes.SETTINGS -> navigateTab(Routes.LIST)
                                Routes.LIST -> activity?.finish()
                                else -> Unit
                            }
                        }

                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            // 避免与子页 TopAppBar/statusBarsPadding 叠两层状态栏空白
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            bottomBar = {},
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                            ) {
                            CompositionLocalProvider(LocalHomeBottomBarHeight provides dockHeight) {
                            NavHost(
                                navController = navController,
                                startDestination = Routes.LIST,
                                // Capture only page content, never the dock itself (no feedback loop).
                                modifier = Modifier.fillMaxSize().then(
                                    if (dockVisible && glassBlurEnabled) Modifier.haze(glassState)
                                    else Modifier,
                                ),
                            ) {
                                composable(Routes.LIST) {
                                    ListScreen(
                                        onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
                                        onCreateWithImage = { uri ->
                                            navController.navigate(Routes.edit(imageUri = uri.toString()))
                                        },
                                        onCreateWithImages = { uris ->
                                            batchImportSession.set(uris)
                                            navController.navigate(Routes.BATCH_REVIEW)
                                        },
                                        onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                                        onCreateBlank = { navController.navigate(Routes.edit()) },
                                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                                        onOpenSettings = { navigateTab(Routes.SETTINGS) },
                                        addTrigger = addTrigger,
                                    )
                                }
                                composable(
                                    route = Routes.DETAIL,
                                    arguments = listOf(navArgument("ticketId") { type = NavType.LongType }),
                                ) {
                                    DetailScreen(
                                        onBack = { navController.popBackStack() },
                                        onEdit = { id -> navController.navigate(Routes.edit(ticketId = id)) },
                                        onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                                    )
                                }
                                composable(
                                    route = Routes.EDIT,
                                    arguments = listOf(
                                        navArgument("ticketId") {
                                            type = NavType.LongType
                                            defaultValue = -1L
                                        },
                                        navArgument("imageUri") {
                                            type = NavType.StringType
                                            defaultValue = ""
                                        },
                                    ),
                                ) {
                                    EditScreen(
                                        onBack = { navController.popBackStack() },
                                        onSaved = { id ->
                                            navController.popBackStack()
                                            navController.navigate(Routes.detail(id)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onNeedPro = {
                                            navController.navigate(Routes.PAYWALL)
                                        },
                                    )
                                }
                                composable(Routes.BATCH_REVIEW) {
                                    BatchReviewScreen(
                                        onBack = { navController.popBackStack() },
                                        onDone = { navController.popBackStack() },
                                        onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                                    )
                                }
                                composable(Routes.PAYWALL) {
                                    PaywallScreen(
                                        onBack = { navController.popBackStack() },
                                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                                    )
                                }
                                composable(Routes.SETTINGS) {
                                    SettingsScreen(
                                        onBack = { navigateTab(Routes.LIST) },
                                        onOpenCloudBackup = { navController.navigate(Routes.CLOUD_BACKUP) },
                                        onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                                        showUpNavigation = false,
                                    )
                                }
                                composable(Routes.CLOUD_BACKUP) {
                                    CloudBackupScreen(
                                        onBack = { navController.popBackStack() },
                                        onNeedPro = {
                                            navController.popBackStack()
                                            navController.navigate(Routes.PAYWALL)
                                        },
                                    )
                                }
                                composable(Routes.PRIVACY) {
                                    PrivacyPolicyScreen(onBack = { navController.popBackStack() })
                                }
                            }
                        
                            if (dockVisible) {
                                HomeBottomBar(
                                    glassState = glassState,
                                    blurEnabled = glassBlurEnabled,
                                    onContainerHeightChanged = { dockHeight = it },
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    currentRoute = currentRoute,
                                    onSelectList = { navigateTab(Routes.LIST) },
                                    onSelectSettings = { navigateTab(Routes.SETTINGS) },
                                    onAdd = {
                                        if (currentRoute != Routes.LIST) {
                                            navigateTab(Routes.LIST)
                                        }
                                        addTrigger += 1
                                    },
                                )
                            }
                            }
                            }
                        }

                        if (openTicketId > 0) {
                            LaunchedEffect(openTicketId) {
                                navController.navigate(Routes.detail(openTicketId))
                            }
                        }
                    }
                    if (showBrandFrame) {
                        BrandLaunchFrame()
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
