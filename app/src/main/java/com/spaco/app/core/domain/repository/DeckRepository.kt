package com.spaco.app.core.domain.repository

import com.spaco.app.core.domain.model.Deck
import kotlinx.coroutines.flow.Flow

interface DeckRepository {

    fun observeActive(): Flow<List<Deck>>

    fun observeAll(): Flow<List<Deck>>

    suspend fun getById(id: String): Deck?

    suspend fun create(skillId: String, name: String): Deck

    suspend fun rename(id: String, name: String)

    suspend fun archive(id: String)

    suspend fun restore(id: String)
}
