package com.spaco.app.core.domain.repository

import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.ReviewEvent
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Access to review history. History is append-only: [recordReview] adds an
 * event and refreshes the item's derived scheduling cache, but nothing here
 * rewrites or deletes an event.
 */
interface ReviewRepository {

    suspend fun observeForItem(itemId: String): Flow<List<ReviewEvent>>

    suspend fun getForItem(itemId: String): List<ReviewEvent>

    fun observeInWindow(deckId: String, since: Instant): Flow<List<ReviewEvent>>

    suspend fun getInWindow(deckId: String, since: Instant): List<ReviewEvent>

    /** Every recorded review, for streaks and overall accuracy. */
    fun observeAll(): Flow<List<ReviewEvent>>

    suspend fun getAll(): List<ReviewEvent>

    /**
     * Records one graded answer: appends the event and recomputes the item's
     * cached schedule in the same transaction, so the cache can never diverge
     * from the history it is derived from.
     */
    suspend fun recordReview(
        itemId: String,
        correct: Boolean,
        gradedBy: GradedBy,
        answeredAt: Instant,
        latencyMs: Long? = null,
    ): ReviewEvent
}
