package com.ticketkeep.app.data.repository

import com.ticketkeep.app.BuildConfig
import com.ticketkeep.app.data.local.ProPreferences
import com.ticketkeep.app.data.local.TicketDao
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.notification.WarrantyReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 票证仓库：本地读写、免费额度门禁、提醒调度；Pro 观察来自 Billing 缓存。
 */
@Singleton
class TicketRepository @Inject constructor(
    private val ticketDao: TicketDao,
    private val proPreferences: ProPreferences,
    private val reminderScheduler: WarrantyReminderScheduler,
) {
    companion object {
        const val FREE_TICKET_LIMIT = 10

        /** 免费额度门禁：Pro 不限；非 Pro 仅当已有条数 < 上限。 */
        fun canAdd(isPro: Boolean, count: Int, limit: Int = FREE_TICKET_LIMIT): Boolean {
            if (isPro) return true
            return count < limit
        }
    }

    fun observeTickets(query: String): Flow<List<Ticket>> = ticketDao.observeTickets(query.trim())

    fun observeTicket(id: Long): Flow<Ticket?> = ticketDao.observeById(id)

    fun observeCount(): Flow<Int> = ticketDao.observeCount()

    fun observeIsPro(): Flow<Boolean> = proPreferences.isPro

    suspend fun canAddTicket(): Boolean {
        return canAdd(isPro = proPreferences.isPro.first(), count = ticketDao.count())
    }

    suspend fun getTicket(id: Long): Ticket? = ticketDao.getById(id)

    /** 导出用：全部票证（按创建时间倒序）。 */
    suspend fun getAllTickets(): List<Ticket> = ticketDao.getAll()

    suspend fun saveTicket(ticket: Ticket): Long {
        val now = System.currentTimeMillis()
        val id = if (ticket.id == 0L) {
            ticketDao.insert(ticket.copy(createdAtMillis = now, updatedAtMillis = now))
        } else {
            ticketDao.update(ticket.copy(updatedAtMillis = now))
            ticket.id
        }
        val saved = ticketDao.getById(id) ?: ticket.copy(id = id)
        reminderScheduler.scheduleForTicket(saved)
        return id
    }

    suspend fun deleteTicket(id: Long) {
        reminderScheduler.cancelForTicket(id)
        ticketDao.deleteById(id)
    }

    /**
     * 仅 Debug：本地假开关。正式 Pro 由 [com.ticketkeep.app.billing.BillingManager] 同步。
     */
    suspend fun setPro(enabled: Boolean) {
        if (BuildConfig.DEBUG) {
            proPreferences.setPro(enabled)
        }
    }

    suspend fun rescheduleAllReminders() {
        reminderScheduler.rescheduleAll(ticketDao.getAllWithWarranty())
    }
}
