package com.ticketkeep.app.widget

import com.ticketkeep.app.data.local.TicketDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 小组件侧获取 Room Dao（Glance / Receiver 无 Hilt 构造注入时用 EntryPoint）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun ticketDao(): TicketDao
}
