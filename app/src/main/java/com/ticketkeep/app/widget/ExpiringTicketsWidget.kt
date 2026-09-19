package com.ticketkeep.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.util.DateFormats
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

/**
 * 临期桌面小组件（Jetpack Glance）：薄荷纸感列表，最多 5 条。
 * 数据来自 Room [TicketDao.getAllWithWarranty]，选条见 [ExpiringTicketsSelector]。
 */
class ExpiringTicketsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now().toEpochDay()
        val tickets = runCatching {
            val entry = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            ExpiringTicketsSelector.select(
                tickets = entry.ticketDao().getAllWithWarranty(),
                todayEpochDay = today,
            )
        }.getOrDefault(emptyList())

        provideContent {
            GlanceTheme {
                ExpiringTicketsContent(context = context, tickets = tickets, todayEpochDay = today)
            }
        }
    }
}

@Composable
private fun ExpiringTicketsContent(
    context: Context,
    tickets: List<Ticket>,
    todayEpochDay: Long,
) {
    val paperBg = Color(0xFFF4F7F6)
    val paperSurface = Color(0xFFFFFFFF)
    val onPaper = Color(0xFF1C1F1E)
    val muted = Color(0xFF5C6B66)
    val mint = Color(0xFF0F766E)
    val error = Color(0xFFB91C1C)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(paperBg)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity(WidgetIntents.openList(context)))
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "票证记 · 临期",
                style = TextStyle(
                    color = ColorProvider(mint, mint),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier,
            )
            Text(
                text = "打开",
                style = TextStyle(
                    color = ColorProvider(mint, mint),
                    fontSize = 12.sp,
                ),
                modifier = GlanceModifier.clickable(actionStartActivity(WidgetIntents.openList(context))),
            )
        }
        Spacer(GlanceModifier.height(8.dp))

        if (tickets.isEmpty()) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(paperSurface)
                    .cornerRadius(12.dp)
                    .padding(14.dp),
            ) {
                Text(
                    text = "暂无临期或已过期票证",
                    style = TextStyle(
                        color = ColorProvider(muted, muted),
                        fontSize = 13.sp,
                    ),
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "有保修截止日期的票证会出现在这里",
                    style = TextStyle(
                        color = ColorProvider(muted, muted),
                        fontSize = 12.sp,
                    ),
                )
            }
        } else {
            for (index in tickets.indices) {
                val ticket = tickets[index]
                if (index > 0) Spacer(GlanceModifier.height(6.dp))
                val endDay = ticket.warrantyEndEpochDay ?: continue
                TicketRow(
                    context = context,
                    ticket = ticket,
                    end = endDay,
                    todayEpochDay = todayEpochDay,
                    surface = paperSurface,
                    onPaper = onPaper,
                    muted = muted,
                    error = error,
                )
            }
        }
    }
}

@Composable
private fun TicketRow(
    context: Context,
    ticket: Ticket,
    end: Long,
    todayEpochDay: Long,
    surface: Color,
    onPaper: Color,
    muted: Color,
    error: Color,
) {
    val expired = ExpiringTicketsSelector.isExpired(end, todayEpochDay)
    val status = ExpiringTicketsSelector.statusLabel(end, todayEpochDay)
    val name = ticket.merchantName.ifBlank { "未命名票证" }
    val dateText = DateFormats.formatEpochDay(end)
    val statusColor = if (expired) error else muted

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(surface)
            .cornerRadius(10.dp)
            .clickable(actionStartActivity(WidgetIntents.openDetail(context, ticket.id)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier) {
            Text(
                text = name,
                style = TextStyle(
                    color = ColorProvider(onPaper, onPaper),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = dateText,
                style = TextStyle(
                    color = ColorProvider(muted, muted),
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = status,
            style = TextStyle(
                color = ColorProvider(statusColor, statusColor),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
