package com.ticketkeep.app.di

import android.content.Context
import androidx.room.Room
import com.ticketkeep.app.data.local.TicketDao
import com.ticketkeep.app.data.local.TicketDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 单例模块：提供 Room 数据库与 DAO。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TicketDatabase {
        return Room.databaseBuilder(
            context,
            TicketDatabase::class.java,
            "ticket_keep.db",
        ).build()
    }

    @Provides
    fun provideTicketDao(db: TicketDatabase): TicketDao = db.ticketDao()
}
