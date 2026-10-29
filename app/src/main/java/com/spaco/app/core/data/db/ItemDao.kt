package com.spaco.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spaco.app.core.domain.model.LearningState
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: PracticeItemEntity)

    @Query("SELECT * FROM practice_items WHERE id = :id")
    suspend fun getById(id: String): PracticeItemEntity?

    @Query(
        "SELECT * FROM practice_items WHERE deckId = :deckId AND archivedAt IS NULL " +
            "ORDER BY createdAt ASC, id ASC",
    )
    suspend fun getActiveForDeck(deckId: String): List<PracticeItemEntity>

    @Query(
        "SELECT * FROM practice_items WHERE deckId = :deckId AND archivedAt IS NULL " +
            "ORDER BY createdAt ASC, id ASC",
    )
    fun observeForDeck(deckId: String): Flow<List<PracticeItemEntity>>

    /**
     * Due items in the one order the app treats as canonical: most overdue
     * first, with the id as a stable tiebreaker. Mirrors `Scheduler.dueOrder`.
     */
    @Query(
        "SELECT * FROM practice_items WHERE deckId = :deckId AND archivedAt IS NULL " +
            "AND nextDueAt <= :now ORDER BY nextDueAt ASC, id ASC",
    )
    fun observeDueItems(deckId: String, now: Long): Flow<List<PracticeItemEntity>>

    @Query(
        "SELECT * FROM practice_items WHERE deckId = :deckId AND archivedAt IS NULL " +
            "AND nextDueAt <= :now ORDER BY nextDueAt ASC, id ASC",
    )
    suspend fun getDueItems(deckId: String, now: Long): List<PracticeItemEntity>

    @Query(
        "SELECT * FROM practice_items WHERE archivedAt IS NULL AND nextDueAt <= :now " +
            "ORDER BY nextDueAt ASC, id ASC",
    )
    fun observeAllDueItems(now: Long): Flow<List<PracticeItemEntity>>

    @Query("SELECT * FROM practice_items WHERE archivedAt IS NULL ORDER BY createdAt ASC, id ASC")
    fun observeAllActive(): Flow<List<PracticeItemEntity>>

    @Query(
        "SELECT * FROM practice_items WHERE archivedAt IS NULL AND nextDueAt <= :now " +
            "ORDER BY nextDueAt ASC, id ASC",
    )
    suspend fun getAllDueItems(now: Long): List<PracticeItemEntity>

    @Query("SELECT MIN(nextDueAt) FROM practice_items WHERE archivedAt IS NULL")
    fun observeEarliestNextDueAt(): Flow<Long?>

    @Query("SELECT MIN(nextDueAt) FROM practice_items WHERE archivedAt IS NULL")
    suspend fun getEarliestNextDueAt(): Long?

    /** The only writer of the derived scheduling cache. */
    @Query(
        "UPDATE practice_items SET nextDueAt = :nextDueAt, learningState = :learningState, " +
            "intervalIndex = :intervalIndex, correctStreak = :correctStreak WHERE id = :id",
    )
    suspend fun updateSchedule(
        id: String,
        nextDueAt: Long,
        learningState: LearningState,
        intervalIndex: Int,
        correctStreak: Int,
    )

    @Query("UPDATE practice_items SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun setArchivedAt(id: String, archivedAt: Long?)

    /**
     * Updates content only. Deliberately an `UPDATE`, not a REPLACE-style
     * upsert: `INSERT OR REPLACE` deletes the conflicting row first, which
     * would cascade-delete the item's review history.
     */
    @Query(
        "UPDATE practice_items SET prompt = :prompt, expectedAnswer = :expectedAnswer, " +
            "explanation = :explanation, isFreeRecall = :isFreeRecall WHERE id = :id",
    )
    suspend fun updateContent(
        id: String,
        prompt: String,
        expectedAnswer: String,
        explanation: String?,
        isFreeRecall: Boolean,
    )
}
