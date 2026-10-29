package com.spaco.app.core.data.repository

import com.spaco.app.core.data.db.SessionDao
import com.spaco.app.core.data.db.SessionStateEntity
import com.spaco.app.core.domain.model.SessionState
import com.spaco.app.core.domain.model.SessionStatus
import com.spaco.app.core.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao,
) : SessionRepository {

    override suspend fun getById(id: String): SessionState? = dao.getById(id)?.toDomain()

    override suspend fun getActiveForDeck(deckId: String): SessionState? =
        dao.getActiveForDeck(deckId, SessionStatus.ACTIVE)?.toDomain()

    override fun observeForDeck(deckId: String): Flow<List<SessionState>> =
        dao.observeForDeck(deckId).map { rows -> rows.map(SessionStateEntity::toDomain) }

    override suspend fun save(session: SessionState) = dao.upsert(session.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}
