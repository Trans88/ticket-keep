package com.ticketkeep.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ticketkeep.app.notification.WarrantyReminderWorker
import com.ticketkeep.app.ui.navigation.Routes
import com.ticketkeep.app.ui.screens.detail.DetailScreen
import com.ticketkeep.app.ui.screens.edit.EditScreen
import com.ticketkeep.app.ui.screens.list.ListScreen
import com.ticketkeep.app.ui.screens.paywall.PaywallScreen
import com.ticketkeep.app.ui.theme.TicketKeepTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val openTicketIdState = MutableStateFlow(-1L)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ListScreen 展示再申请入口 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleOpenTicketIntent(intent)

        setContent {
            TicketKeepTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val openTicketId by openTicketIdState.collectAsStateWithLifecycle()

                    NavHost(
                        navController = navController,
                        startDestination = Routes.LIST,
                    ) {
                        composable(Routes.LIST) {
                            ListScreen(
                                onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
                                onCreateWithImage = { uri ->
                                    navController.navigate(Routes.edit(imageUri = uri.toString()))
                                },
                                onOpenPaywall = { navController.navigate(Routes.PAYWALL) },
                                onCreateBlank = { navController.navigate(Routes.edit()) },
                            )
                        }
                        composable(
                            route = Routes.DETAIL,
                            arguments = listOf(navArgument("ticketId") { type = NavType.LongType }),
                        ) {
                            DetailScreen(
                                onBack = { navController.popBackStack() },
                                onEdit = { id -> navController.navigate(Routes.edit(ticketId = id)) },
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
                        composable(Routes.PAYWALL) {
                            PaywallScreen(onBack = { navController.popBackStack() })
                        }
                    }

                    LaunchedEffect(openTicketId) {
                        if (openTicketId > 0) {
                            navController.navigate(Routes.detail(openTicketId)) {
                                launchSingleTop = true
                            }
                            openTicketIdState.value = -1L
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenTicketIntent(intent)
    }

    private fun handleOpenTicketIntent(intent: Intent?) {
        val id = intent?.getLongExtra(WarrantyReminderWorker.EXTRA_TICKET_ID, -1L) ?: -1L
        if (id > 0L) {
            openTicketIdState.value = id
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
