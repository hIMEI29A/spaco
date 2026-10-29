package com.spaco.app.core.data.repository

import com.spaco.app.core.data.IdGenerator
import com.spaco.app.core.data.db.DeckDao
import com.spaco.app.core.data.db.DeckEntity
import com.spaco.app.core.domain.model.Deck
import com.spaco.app.core.domain.repository.DeckRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DeckRepositoryImpl @Inject constructor(
    private val dao: DeckDao,
    private val clock: Clock,
    private val ids: IdGenerator,
) : DeckRepository {

    override fun observeActive(): Flow<List<Deck>> =
        dao.observeActive().map { rows -> rows.map(DeckEntity::toDomain) }

    override fun observeAll(): Flow<List<Deck>> =
        dao.observeAll().map { rows -> rows.map(DeckEntity::toDomain) }

    override suspend fun getById(id: String): Deck? = dao.getById(id)?.toDomain()

    override suspend fun create(skillId: String, name: String): Deck {
        val deck = Deck(
            id = ids.newId(),
            skillId = skillId,
            name = name,
            createdAt = clock.instant(),
        )
        dao.upsert(deck.toEntity())
        return deck
    }

    override suspend fun rename(id: String, name: String) = dao.rename(id, name)

    override suspend fun archive(id: String) = dao.setArchivedAt(id, clock.millis())

    override suspend fun restore(id: String) = dao.setArchivedAt(id, null)
}
