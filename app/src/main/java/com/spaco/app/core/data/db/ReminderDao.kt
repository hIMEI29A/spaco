package com.spaco.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A single-row table keyed by [ReminderStateEntity.id] that remembers the last
 * due set the learner was notified about, so the same due set is not announced
 * twice.
 */
@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ReminderStateEntity)

    @Query("SELECT * FROM reminder_state WHERE id = :id")
    suspend fun getById(id: String): ReminderStateEntity?

    @Query("SELECT * FROM reminder_state WHERE id = :id")
    fun observeById(id: String): Flow<ReminderStateEntity?>
}
