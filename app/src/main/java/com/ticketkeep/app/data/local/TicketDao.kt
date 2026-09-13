package com.ticketkeep.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ticketkeep.app.data.model.Ticket
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO：票证的增删改查与观察流。
 */
@Dao
interface TicketDao {
    @Query(
        """
        SELECT * FROM tickets
        WHERE (:query = '' OR merchantName LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%')
        ORDER BY createdAtMillis DESC
        """
    )
    fun observeTickets(query: String): Flow<List<Ticket>>

    @Query("SELECT * FROM tickets WHERE id = :id")
    fun observeById(id: Long): Flow<Ticket?>

    @Query("SELECT * FROM tickets WHERE id = :id")
    suspend fun getById(id: Long): Ticket?

    @Query("SELECT * FROM tickets ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<Ticket>

    @Query("SELECT COUNT(*) FROM tickets")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM tickets")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM tickets
        WHERE warrantyEndEpochDay IS NOT NULL
        ORDER BY warrantyEndEpochDay ASC
        """
    )
    suspend fun getAllWithWarranty(): List<Ticket>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ticket: Ticket): Long

    @Update
    suspend fun update(ticket: Ticket)

    @Delete
    suspend fun delete(ticket: Ticket)

    @Query("DELETE FROM tickets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
