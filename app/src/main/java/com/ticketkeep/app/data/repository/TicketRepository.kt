package com.ticketkeep.app.data.repository

import com.ticketkeep.app.data.local.ProPreferences
import com.ticketkeep.app.data.local.TicketDao
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.notification.WarrantyReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class TicketRepository @Inject constructor(
    private val ticketDao: TicketDao,
    private val proPreferences: ProPreferences,
    private val reminderScheduler: WarrantyReminderScheduler,
) {
    companion object {
        const val FREE_TICKET_LIMIT = 10
    }

    fun observeTickets(query: String): Flow<List<Ticket>> = ticketDao.observeTickets(query.trim())

    fun observeTicket(id: Long): Flow<Ticket?> = ticketDao.observeById(id)

    fun observeCount(): Flow<Int> = ticketDao.observeCount()

    fun observeIsPro(): Flow<Boolean> = proPreferences.isPro

    suspend fun canAddTicket(): Boolean {
        if (proPreferences.isPro.first()) return true
        return ticketDao.count() < FREE_TICKET_LIMIT
    }

    suspend fun getTicket(id: Long): Ticket? = ticketDao.getById(id)

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

    suspend fun setPro(enabled: Boolean) = proPreferences.setPro(enabled)

    suspend fun rescheduleAllReminders() {
        reminderScheduler.rescheduleAll(ticketDao.getAllWithWarranty())
    }
}
