package com.spaco.app.core.data.repository

import com.spaco.app.core.data.IdGenerator
import com.spaco.app.core.data.db.ItemDao
import com.spaco.app.core.data.db.PracticeItemEntity
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.repository.ItemRepository
import com.spaco.app.core.domain.scheduling.Scheduler
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ItemRepositoryImpl @Inject constructor(
    private val dao: ItemDao,
    private val clock: Clock,
    private val ids: IdGenerator,
) : ItemRepository {

    override fun observeForDeck(deckId: String): Flow<List<PracticeItem>> =
        dao.observeForDeck(deckId).map { rows -> rows.map(PracticeItemEntity::toDomain) }

    override suspend fun getForDeck(deckId: String): List<PracticeItem> =
        dao.getActiveForDeck(deckId).map(PracticeItemEntity::toDomain)

    override fun observeAllActive(): Flow<List<PracticeItem>> =
        dao.observeAllActive().map { rows -> rows.map(PracticeItemEntity::toDomain) }

    override suspend fun getAllDueItems(now: Instant): List<PracticeItem> =
        dao.getAllDueItems(now.toEpochMilli()).map(PracticeItemEntity::toDomain)

    override fun observeDueItems(deckId: String, now: Instant): Flow<List<PracticeItem>> =
        dao.observeDueItems(deckId, now.toEpochMilli())
            .map { rows -> rows.map(PracticeItemEntity::toDomain) }

    override suspend fun getDueItems(deckId: String, now: Instant): List<PracticeItem> =
        dao.getDueItems(deckId, now.toEpochMilli()).map(PracticeItemEntity::toDomain)

    override suspend fun getById(id: String): PracticeItem? = dao.getById(id)?.toDomain()

    override suspend fun getEarliestNextDueAt(): Instant? =
        dao.getEarliestNextDueAt()?.let(Instant::ofEpochMilli)

    override fun observeEarliestNextDueAt(): Flow<Instant?> =
        dao.observeEarliestNextDueAt().map { it?.let(Instant::ofEpochMilli) }

    override suspend fun add(
        deckId: String,
        prompt: String,
        expectedAnswer: String,
        explanation: String?,
        isFreeRecall: Boolean,
    ): PracticeItem {
        val createdAt = clock.instant()
        val item = PracticeItem(
            id = ids.newId(),
            deckId = deckId,
            prompt = prompt,
            expectedAnswer = expectedAnswer,
            explanation = explanation,
            createdAt = createdAt,
            isFreeRecall = isFreeRecall,
            schedule = Scheduler.newItemSchedule(createdAt),
        )
        dao.insert(item.toEntity())
        return item
    }

    override suspend fun update(
        id: String,
        prompt: String,
        expectedAnswer: String,
        explanation: String?,
        isFreeRecall: Boolean,
    ) {
        // A plain content update; the scheduling cache and review history are
        // intentionally left untouched.
        dao.updateContent(id, prompt, expectedAnswer, explanation, isFreeRecall)
    }

    override suspend fun archive(id: String) = dao.setArchivedAt(id, clock.millis())

    override suspend fun restore(id: String) = dao.setArchivedAt(id, null)
}
