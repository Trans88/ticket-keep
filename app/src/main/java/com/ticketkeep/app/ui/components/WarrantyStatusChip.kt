package com.ticketkeep.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ticketkeep.app.ui.theme.TicketKeepRadius
import com.ticketkeep.app.ui.theme.WarrantyStatusStyle
import com.ticketkeep.app.ui.theme.warrantyStatusOf

/**
 * 保修状态芯片；无到期日默认不渲染。
 * 圆角 badge=7；颜色对齐 tokens warning/error/primary containers。
 */
@Composable
fun WarrantyStatusChip(
    warrantyEndEpochDay: Long?,
    modifier: Modifier = Modifier,
    showNoneLabel: Boolean = false,
) {
    val style: WarrantyStatusStyle? = warrantyStatusOf(
        warrantyEndEpochDay = warrantyEndEpochDay,
        darkTheme = isSystemInDarkTheme(),
    )
    if (style == null) {
        if (showNoneLabel) {
            Text(
                text = "未设置保修",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        return
    }
    Text(
        text = style.label,
        style = MaterialTheme.typography.labelSmall,
        color = style.content,
        modifier = modifier
            .clip(RoundedCornerShape(TicketKeepRadius.badge))
            .background(style.container)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
