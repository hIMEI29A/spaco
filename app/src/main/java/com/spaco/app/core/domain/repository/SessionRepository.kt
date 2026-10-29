package com.spaco.app.core.domain.repository

import com.spaco.app.core.domain.model.SessionState
import kotlinx.coroutines.flow.Flow

interface SessionRepository {

    suspend fun getById(id: String): SessionState?

    /** The single in-progress session for a deck, if one exists. */
    suspend fun getActiveForDeck(deckId: String): SessionState?

    fun observeForDeck(deckId: String): Flow<List<SessionState>>

    suspend fun save(session: SessionState)

    suspend fun delete(id: String)
}
