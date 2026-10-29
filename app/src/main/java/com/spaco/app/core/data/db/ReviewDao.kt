package com.spaco.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Review events are append-only. There is deliberately no update or delete:
 * history is the source of truth that scheduling is rebuilt from.
 */
@Dao
interface ReviewDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: ReviewEventEntity)

    @Query("SELECT * FROM review_events WHERE itemId = :itemId ORDER BY answeredAt ASC, id ASC")
    suspend fun getForItem(itemId: String): List<ReviewEventEntity>

    @Query("SELECT * FROM review_events WHERE itemId = :itemId ORDER BY answeredAt ASC, id ASC")
    fun observeForItem(itemId: String): Flow<List<ReviewEventEntity>>

    @Query(
        "SELECT e.* FROM review_events e INNER JOIN practice_items i ON i.id = e.itemId " +
            "WHERE i.deckId = :deckId AND e.answeredAt >= :since ORDER BY e.answeredAt ASC, e.id ASC",
    )
    suspend fun getInWindow(deckId: String, since: Long): List<ReviewEventEntity>

    @Query(
        "SELECT e.* FROM review_events e INNER JOIN practice_items i ON i.id = e.itemId " +
            "WHERE i.deckId = :deckId AND e.answeredAt >= :since ORDER BY e.answeredAt ASC, e.id ASC",
    )
    fun observeInWindow(deckId: String, since: Long): Flow<List<ReviewEventEntity>>

    @Query("SELECT * FROM review_events ORDER BY answeredAt ASC, id ASC")
    fun observeAll(): Flow<List<ReviewEventEntity>>

    @Query("SELECT * FROM review_events ORDER BY answeredAt ASC, id ASC")
    suspend fun getAll(): List<ReviewEventEntity>

    @Query("SELECT COUNT(*) FROM review_events")
    suspend fun count(): Int
}
