package com.ticketkeep.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate

/**
 * 保修状态（UI 专用，不改业务逻辑）。
 * daysLeft = endEpochDay - today；过期为负。
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

/** 根据保修到期 epochDay 计算芯片样式；无日期返回 null（可不展示芯片） */
fun warrantyStatusOf(warrantyEndEpochDay: Long?, today: LocalDate = LocalDate.now()): WarrantyStatusStyle? {
    if (warrantyEndEpochDay == null) return null
    val daysLeft = (warrantyEndEpochDay - today.toEpochDay()).toInt()
    return when {
        daysLeft > 30 -> WarrantyStatusStyle(
            kind = WarrantyStatusKind.Active,
            label = "保修中",
            container = SuccessContainer,
            content = Success,
        )
        daysLeft >= 0 -> WarrantyStatusStyle(
            kind = WarrantyStatusKind.Soon,
            label = if (daysLeft == 0) "今天到期" else "${daysLeft} 天后到期",
            container = WarningContainer,
            content = Warning,
        )
        else -> WarrantyStatusStyle(
            kind = WarrantyStatusKind.Expired,
            label = "已过期",
            container = PaperSurfaceVariant,
            content = PaperOnSurfaceVariant,
        )
    }
}

@Composable
fun warrantyChipColors(style: WarrantyStatusStyle): Pair<Color, Color> =
    style.container to style.content
