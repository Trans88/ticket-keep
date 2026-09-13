package com.ticketkeep.app.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ticketkeep.app.data.model.Ticket
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 使用 WorkManager 安排保修到期提醒（默认：到期前 7 天、1 天、当天）。
 *
 * 选择 WorkManager 而非 AlarmManager 的原因：
 * - 与 Hilt 集成简单，重启后由 WorkManager 自行恢复未执行工作；
 * - 对精确闹钟权限要求更低，适合 MVP「大约提醒」场景；
 * - BootReceiver 中会再次调用 rescheduleAll 作为双保险。
 */
@Singleton
class WarrantyReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun scheduleForTicket(ticket: Ticket) {
        cancelForTicket(ticket.id)
        val endDay = ticket.warrantyEndEpochDay ?: return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)

        for (daysBefore in WarrantyReminderWorker.DEFAULT_DAYS_BEFORE) {
            val fireDate = LocalDate.ofEpochDay(endDay).minusDays(daysBefore.toLong())
            val fireAt = LocalDateTime.of(fireDate, LocalTime.of(9, 0))
            if (fireAt.isBefore(now)) continue

            val delay = Duration.between(now, fireAt).toMillis().coerceAtLeast(1L)
            val data = Data.Builder()
                .putLong(WarrantyReminderWorker.KEY_TICKET_ID, ticket.id)
                .putInt(WarrantyReminderWorker.KEY_DAYS_BEFORE, daysBefore)
                .build()

            val request = OneTimeWorkRequestBuilder<WarrantyReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(tagFor(ticket.id))
                .build()

            workManager.enqueueUniqueWork(
                uniqueName(ticket.id, daysBefore),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    fun cancelForTicket(ticketId: Long) {
        workManager.cancelAllWorkByTag(tagFor(ticketId))
        for (days in WarrantyReminderWorker.DEFAULT_DAYS_BEFORE) {
            workManager.cancelUniqueWork(uniqueName(ticketId, days))
        }
    }

    fun rescheduleAll(tickets: List<Ticket>) {
        tickets.forEach { scheduleForTicket(it) }
    }

    private fun tagFor(ticketId: Long) = "warranty_$ticketId"

    private fun uniqueName(ticketId: Long, daysBefore: Int) = "warranty_${ticketId}_d$daysBefore"
}
