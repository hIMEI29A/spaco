package com.spaco.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deck: DeckEntity)

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getById(id: String): DeckEntity?

    @Query("SELECT * FROM decks WHERE archivedAt IS NULL ORDER BY name ASC, id ASC")
    fun observeActive(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks ORDER BY name ASC, id ASC")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks ORDER BY name ASC, id ASC")
    suspend fun getAll(): List<DeckEntity>

    @Query("UPDATE decks SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE decks SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun setArchivedAt(id: String, archivedAt: Long?)
}
