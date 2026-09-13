package com.ticketkeep.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ticketkeep.app.ui.theme.WarrantyStatusStyle
import com.ticketkeep.app.ui.theme.warrantyStatusOf

/** 保修状态芯片；无到期日不渲染（quiet「无到期」由调用方决定）。 */
@Composable
fun WarrantyStatusChip(
    warrantyEndEpochDay: Long?,
    modifier: Modifier = Modifier,
    showNoneLabel: Boolean = false,
) {
    val style: WarrantyStatusStyle? = warrantyStatusOf(warrantyEndEpochDay)
    if (style == null) {
        if (showNoneLabel) {
            Text(
                text = "无到期",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        return
    }
    Text(
        text = style.label,
        style = MaterialTheme.typography.labelMedium,
        color = style.content,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(style.container)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
