package com.spaco.app.core.data.repository

import androidx.room.withTransaction
import com.spaco.app.core.data.IdGenerator
import com.spaco.app.core.data.db.ItemDao
import com.spaco.app.core.data.db.ReviewDao
import com.spaco.app.core.data.db.ReviewEventEntity
import com.spaco.app.core.data.db.SpaCoDatabase
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.ReviewEvent
import com.spaco.app.core.domain.repository.ReviewRepository
import com.spaco.app.core.domain.scheduling.Scheduler
import com.spaco.app.core.domain.scheduling.SchedulingParameters
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val database: SpaCoDatabase,
    private val reviewDao: ReviewDao,
    private val itemDao: ItemDao,
    private val parameters: SchedulingParameters,
    private val ids: IdGenerator,
) : ReviewRepository {

    override suspend fun observeForItem(itemId: String): Flow<List<ReviewEvent>> =
        reviewDao.observeForItem(itemId).map { rows -> rows.map(ReviewEventEntity::toDomain) }

    override suspend fun getForItem(itemId: String): List<ReviewEvent> =
        reviewDao.getForItem(itemId).map(ReviewEventEntity::toDomain)

    override fun observeInWindow(deckId: String, since: Instant): Flow<List<ReviewEvent>> =
        reviewDao.observeInWindow(deckId, since.toEpochMilli())
            .map { rows -> rows.map(ReviewEventEntity::toDomain) }

    override suspend fun getInWindow(deckId: String, since: Instant): List<ReviewEvent> =
        reviewDao.getInWindow(deckId, since.toEpochMilli()).map(ReviewEventEntity::toDomain)

    override fun observeAll(): Flow<List<ReviewEvent>> =
        reviewDao.observeAll().map { rows -> rows.map(ReviewEventEntity::toDomain) }

    override suspend fun getAll(): List<ReviewEvent> =
        reviewDao.getAll().map(ReviewEventEntity::toDomain)

    override suspend fun recordReview(
        itemId: String,
        correct: Boolean,
        gradedBy: GradedBy,
        answeredAt: Instant,
        latencyMs: Long?,
    ): ReviewEvent {
        val event = ReviewEvent(
            id = ids.newId(),
            itemId = itemId,
            answeredAt = answeredAt,
            correct = correct,
            gradedBy = gradedBy,
            latencyMs = latencyMs,
        )

        // Append the event and refresh the derived cache atomically: if either
        // half fails, neither is written and the cache cannot drift from the
        // history it is derived from.
        database.withTransaction {
            reviewDao.insert(event.toEntity())

            val stored = itemDao.getById(itemId) ?: return@withTransaction
            val history = reviewDao.getForItem(itemId).map(ReviewEventEntity::toDomain)
            val recomputed = Scheduler.recompute(history, stored.toDomain(), parameters)

            itemDao.updateSchedule(
                id = itemId,
                nextDueAt = recomputed.nextDueAt.toEpochMilli(),
                learningState = recomputed.state,
                intervalIndex = recomputed.intervalIndex,
                correctStreak = recomputed.correctStreak,
            )
        }

        return event
    }
}
