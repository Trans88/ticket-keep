package com.ticketkeep.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ticketkeep.app.data.model.Ticket

/**
 * Room 数据库定义（ticket_keep.db），仅存本机。
 */
@Database(entities = [Ticket::class], version = 1, exportSchema = false)
abstract class TicketDatabase : RoomDatabase() {
    abstract fun ticketDao(): TicketDao
}
