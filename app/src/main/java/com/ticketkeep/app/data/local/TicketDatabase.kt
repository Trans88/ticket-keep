package com.ticketkeep.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ticketkeep.app.data.model.Ticket

@Database(entities = [Ticket::class], version = 1, exportSchema = false)
abstract class TicketDatabase : RoomDatabase() {
    abstract fun ticketDao(): TicketDao
}
