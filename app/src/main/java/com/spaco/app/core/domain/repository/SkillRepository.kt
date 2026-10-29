package com.spaco.app.core.domain.repository

import com.spaco.app.core.domain.model.Skill
import kotlinx.coroutines.flow.Flow

/**
 * Access to skills. Reads are `Flow`-backed so the UI updates without a restart.
 */
interface SkillRepository {

    fun observeAll(): Flow<List<Skill>>

    suspend fun getById(id: String): Skill?

    /** Case-sensitive lookup used when a deck is created inside a possibly-new skill. */
    suspend fun getByName(name: String): Skill?

    /** Creates a skill with the given name. */
    suspend fun create(name: String): Skill

    suspend fun rename(id: String, name: String)

    suspend fun archive(id: String)

    suspend fun restore(id: String)
}
