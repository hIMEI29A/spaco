package com.spaco.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spaco.app.core.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionStateEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionStateEntity?

    /** The active session for a deck, if any, so an interrupted one can resume. */
    @Query(
        "SELECT * FROM sessions WHERE deckId = :deckId AND status = :status " +
            "ORDER BY startedAt DESC, id DESC LIMIT 1",
    )
    suspend fun getActiveForDeck(deckId: String, status: SessionStatus): SessionStateEntity?

    @Query("SELECT * FROM sessions WHERE deckId = :deckId ORDER BY startedAt DESC, id DESC")
    fun observeForDeck(deckId: String): Flow<List<SessionStateEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: String): Flow<SessionStateEntity?>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}
