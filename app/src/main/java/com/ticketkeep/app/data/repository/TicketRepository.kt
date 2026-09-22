package com.ticketkeep.app.data.repository

import com.ticketkeep.app.BuildConfig
import com.ticketkeep.app.data.local.TicketDao
import com.ticketkeep.app.data.model.Ticket
import com.ticketkeep.app.entitlement.EntitlementRepository
import com.ticketkeep.app.notification.WarrantyReminderScheduler
import com.ticketkeep.app.widget.WidgetRefresher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 票证仓库：本地读写、免费条数门禁、提醒调度；权益观察走 [EntitlementRepository]。
 *
 * [observeIsPro] / [setPro] 仅为兼容别名 → **本地高级或旧年订**，不含云备份。
 */
@Singleton
class TicketRepository @Inject constructor(
    private val ticketDao: TicketDao,
    private val entitlementRepository: EntitlementRepository,
    private val reminderScheduler: WarrantyReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
) {
    companion object {
        const val FREE_TICKET_LIMIT = 10

        /**
         * 免费条数门禁：本地高级版（或旧年订兼容）无限；否则当前数量 < 限额。
         *
         * 参数 [hasLocalPremium] 即旧调用中的 `isPro`，语义已收窄为「本地高级 / 旧年订」，
         * **不含云备份**。云能力请观察 [TicketRepository.observeCanUseCloud]。
         *
         * 兼容写法：`canAdd(isPro = x, count = n)` 在 Kotlin 命名参数下请改为
         * `canAdd(hasLocalPremium = x, count = n)`；若仍传位置参数则行为不变。
         */
        fun canAdd(hasLocalPremium: Boolean, count: Int, limit: Int = FREE_TICKET_LIMIT): Boolean {
            if (hasLocalPremium) return true
            return count < limit
        }

        /** @deprecated 使用 [canAdd] 的 hasLocalPremium 参数；此别名仅方便旧测试迁移。 */
        @Suppress("NOTHING_TO_INLINE")
        inline fun canAddIsPro(isPro: Boolean, count: Int, limit: Int = FREE_TICKET_LIMIT): Boolean =
            canAdd(hasLocalPremium = isPro, count = count, limit = limit)
    }

    fun observeTickets(query: String): Flow<List<Ticket>> = ticketDao.observeTickets(query.trim())

    fun observeTicket(id: Long): Flow<Ticket?> = ticketDao.observeById(id)

    fun observeCount(): Flow<Int> = ticketDao.observeCount()

    /**
     * 兼容：观察「本地高级或旧年订」。新代码请用 [observeHasLocalPremium] / [observeCanUseCloud]。
     */
    fun observeIsPro(): Flow<Boolean> = entitlementRepository.observeHasLocalPremium()

    fun observeHasLocalPremium(): Flow<Boolean> = entitlementRepository.observeHasLocalPremium()

    fun observeCanUseCloud(): Flow<Boolean> = entitlementRepository.observeCanUseCloud()

    suspend fun canAddTicket(): Boolean {
        return canAdd(
            hasLocalPremium = entitlementRepository.observeHasLocalPremium().first(),
            count = ticketDao.count(),
        )
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
        widgetRefresher.refreshExpiringWidget()
        return id
    }

    suspend fun deleteTicket(id: Long) {
        reminderScheduler.cancelForTicket(id)
        ticketDao.deleteById(id)
        widgetRefresher.refreshExpiringWidget()
    }

    /**
     * 仅 Debug：模拟本地高级版（不再隐含云备份）。
     * 正式路径由 BillingManager → EntitlementRepository。
     */
    suspend fun setPro(enabled: Boolean) {
        if (BuildConfig.DEBUG) {
            entitlementRepository.setDebugLocal(enabled)
        }
    }

    suspend fun setDebugLocal(enabled: Boolean) {
        if (BuildConfig.DEBUG) entitlementRepository.setDebugLocal(enabled)
    }

    suspend fun setDebugCloud(enabled: Boolean) {
        if (BuildConfig.DEBUG) entitlementRepository.setDebugCloud(enabled)
    }

    suspend fun setDebugAllOff() {
        if (BuildConfig.DEBUG) entitlementRepository.setDebugAllOff()
    }

    suspend fun rescheduleAllReminders() {
        reminderScheduler.rescheduleAll(ticketDao.getAllWithWarranty())
    }
}
