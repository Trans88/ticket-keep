package com.ticketkeep.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate

/**
 * 保修状态（UI 专用，不改业务逻辑）。
 * daysLeft = endEpochDay - today；过期为负；临期含今天与第 30 天 [0, 30]。
 */
enum class WarrantyStatusKind {
    Active,   // 保修中 >30 天
    Soon,     // 临期 0..30
    Expired,  // 已过期
    None,     // 无到期
}

data class WarrantyStatusStyle(
    val kind: WarrantyStatusKind,
    val label: String,
    val container: Color,
    val content: Color,
)

/**
 * 根据保修到期 epochDay 计算芯片样式；无日期返回 null（可不展示芯片）。
 * 颜色取自 v2 tokens：Active→primaryContainer，Soon→warningContainer，Expired→surfaceVariant。
 */
fun warrantyStatusOf(
    warrantyEndEpochDay: Long?,
    today: LocalDate = LocalDate.now(),
    darkTheme: Boolean = false,
): WarrantyStatusStyle? {
    if (warrantyEndEpochDay == null) return null
    val daysLeft = (warrantyEndEpochDay - today.toEpochDay()).toInt()
    val successContainer = if (darkTheme) DarkPrimaryContainer else LightPrimaryContainer
    val successContent = if (darkTheme) DarkOnPrimaryContainer else LightOnPrimaryContainer
    val warnContainer = if (darkTheme) DarkWarningContainer else LightWarningContainer
    val warnContent = if (darkTheme) DarkOnWarningContainer else LightOnWarningContainer
    val expiredContainer = if (darkTheme) DarkSurfaceVariant else LightSurfaceVariant
    val expiredContent = if (darkTheme) DarkOnSurfaceVariant else LightOnSurfaceVariant
    return when {
        daysLeft > 30 -> WarrantyStatusStyle(
            kind = WarrantyStatusKind.Active,
            label = "保修中",
            container = successContainer,
            content = successContent,
        )
        daysLeft >= 0 -> WarrantyStatusStyle(
            kind = WarrantyStatusKind.Soon,
            label = if (daysLeft == 0) "今天到期" else "${daysLeft} 天后到期",
            container = warnContainer,
            content = warnContent,
        )
        else -> WarrantyStatusStyle(
            kind = WarrantyStatusKind.Expired,
            label = "已过期",
            container = expiredContainer,
            content = expiredContent,
        )
    }
}

@Composable
fun warrantyChipColors(style: WarrantyStatusStyle): Pair<Color, Color> =
    style.container to style.content
