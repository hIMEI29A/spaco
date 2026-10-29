package com.spaco.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skill: SkillEntity)

    @Query("SELECT * FROM skills WHERE id = :id")
    suspend fun getById(id: String): SkillEntity?

    @Query("SELECT * FROM skills WHERE name = :name ORDER BY id ASC LIMIT 1")
    suspend fun getByName(name: String): SkillEntity?

    @Query("SELECT * FROM skills ORDER BY name ASC, id ASC")
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills ORDER BY name ASC, id ASC")
    suspend fun getAll(): List<SkillEntity>

    @Query("UPDATE skills SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE skills SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun setArchivedAt(id: String, archivedAt: Long?)
}
