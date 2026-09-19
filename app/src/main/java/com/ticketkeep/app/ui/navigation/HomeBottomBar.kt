package com.ticketkeep.app.ui.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 主页底部双 Tab：票证 / 我的。仅 list、settings 显示；选中色 primary，纸感无 VIP 风。
 */
@Composable
fun HomeBottomBar(
    currentRoute: String?,
    onSelectList: () -> Unit,
    onSelectSettings: () -> Unit,
) {
    val listSelected = currentRoute == Routes.LIST
    val settingsSelected = currentRoute == Routes.SETTINGS
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        HomeTabItem(
            selected = listSelected,
            onClick = onSelectList,
            selectedIcon = Icons.Filled.ReceiptLong,
            unselectedIcon = Icons.Outlined.ReceiptLong,
            label = "票证",
        )
        HomeTabItem(
            selected = settingsSelected,
            onClick = onSelectSettings,
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            label = "我的",
        )
    }
}

@Composable
private fun RowScope.HomeTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
            )
        },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/** 主 Tab（list / settings）显示底栏；详情/编辑等全屏栈上盖时隐藏。 */
fun shouldShowHomeBottomBar(route: String?): Boolean =
    route == Routes.LIST || route == Routes.SETTINGS