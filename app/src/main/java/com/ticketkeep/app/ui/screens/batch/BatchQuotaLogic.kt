package com.ticketkeep.app.ui.screens.batch

import com.ticketkeep.app.data.repository.TicketRepository

/**
 * 批量保存配额决策：纯函数，便于单测。
 * 剩余名额不足时**绝不静默截断**，由 UI 弹出选择或升级。
 */
object BatchQuotaLogic {

    /**
     * 免费额度下剩余可新增条数；本地高级版返回 [Int.MAX_VALUE] 表示无限。
     */
    fun remainingSlots(
        hasLocalPremium: Boolean,
        currentCount: Int,
        limit: Int = TicketRepository.FREE_TICKET_LIMIT,
    ): Int {
        if (hasLocalPremium) return Int.MAX_VALUE
        return (limit - currentCount).coerceAtLeast(0)
    }

    /**
     * 保存前决策。
     */
    sealed class SaveGate {
        /** 名额足够（或 Pro），可直接保存所选 */
        data object Proceed : SaveGate()

        /** 剩余 0：引导升级，不可保存 */
        data object NeedUpgrade : SaveGate()

        /**
         * 剩余 [remaining] 张但选了 [selectedCount] 张：
         * 让用户自行勾选子集或去升级，禁止静默截断。
         */
        data class NeedChoose(val remaining: Int, val selectedCount: Int) : SaveGate()

        /** 当前无可保存项（未选或均已保存） */
        data object NothingToSave : SaveGate()
    }

    fun decideSaveGate(remaining: Int, selectedUnsavedCount: Int): SaveGate {
        if (selectedUnsavedCount <= 0) return SaveGate.NothingToSave
        if (remaining == Int.MAX_VALUE || remaining >= selectedUnsavedCount) {
            return SaveGate.Proceed
        }
        if (remaining <= 0) return SaveGate.NeedUpgrade
        return SaveGate.NeedChoose(remaining = remaining, selectedCount = selectedUnsavedCount)
    }

    /** 仅未保存草稿可 insert。 */
    fun canInsert(item: BatchDraftItem): Boolean = item.savedTicketId == null

    /** 标记已保存：写入 id 并取消勾选，避免二次插入。 */
    fun markSaved(item: BatchDraftItem, ticketId: Long): BatchDraftItem =
        item.copy(savedTicketId = ticketId, selected = false)
}
