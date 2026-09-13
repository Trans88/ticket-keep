package com.ticketkeep.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ticketkeep.app.MainActivity
import com.ticketkeep.app.R
import com.ticketkeep.app.data.local.TicketDao
import com.ticketkeep.app.util.DateFormats
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

@HiltWorker
class WarrantyReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ticketDao: TicketDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ticketId = inputData.getLong(KEY_TICKET_ID, -1L)
        val daysBefore = inputData.getInt(KEY_DAYS_BEFORE, 0)
        if (ticketId < 0) return Result.failure()

        val ticket = ticketDao.getById(ticketId) ?: return Result.success()
        val endDay = ticket.warrantyEndEpochDay ?: return Result.success()
        val today = LocalDate.now().toEpochDay()
        val target = endDay - daysBefore

        // 保修已结束后不再提醒
        if (today > endDay) return Result.success()
        // 尚未到提醒日起点（过早）则跳过；允许 Doze/厂商延迟导致晚于 target 仍发送
        if (today < target) return Result.success()

        ensureChannel()
        val title = when {
            today >= endDay -> "保修今日到期"
            daysBefore <= 1 || today >= endDay - 1 -> "保修即将到期"
            else -> "保修还有 ${endDay - today} 天到期"
        }
        val body = buildString {
            append(ticket.merchantName.ifBlank { "未命名票证" })
            append(" · 到期日 ")
            append(DateFormats.formatEpochDay(endDay))
            if (today > target) {
                append("（延迟送达）")
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TICKET_ID, ticketId)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            (ticketId * 10 + daysBefore).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((ticketId * 10 + daysBefore).toInt(), notification)
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notification_channel_warranty),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = applicationContext.getString(R.string.notification_channel_desc)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "warranty_reminders"
        const val KEY_TICKET_ID = "ticket_id"
        const val KEY_DAYS_BEFORE = "days_before"
        const val EXTRA_TICKET_ID = "extra_ticket_id"
        val DEFAULT_DAYS_BEFORE = listOf(7, 1, 0)
    }
}
