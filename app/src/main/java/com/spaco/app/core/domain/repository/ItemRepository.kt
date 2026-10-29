package com.spaco.app.core.domain.repository

import com.spaco.app.core.domain.model.PracticeItem
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface ItemRepository {

    fun observeForDeck(deckId: String): Flow<List<PracticeItem>>

    suspend fun getForDeck(deckId: String): List<PracticeItem>

    /** Every non-archived item across all decks, for overall progress totals. */
    fun observeAllActive(): Flow<List<PracticeItem>>

    /** Every due item across all non-archived decks, for background reminders. */
    suspend fun getAllDueItems(now: Instant): List<PracticeItem>

    /** Due items, most overdue first, as of [now]. */
    fun observeDueItems(deckId: String, now: Instant): Flow<List<PracticeItem>>

    suspend fun getDueItems(deckId: String, now: Instant): List<PracticeItem>

    suspend fun getById(id: String): PracticeItem?

    /** The soonest time any item is next due, or `null` when nothing is pending. */
    suspend fun getEarliestNextDueAt(): Instant?

    fun observeEarliestNextDueAt(): Flow<Instant?>

    suspend fun add(
        deckId: String,
        prompt: String,
        expectedAnswer: String,
        explanation: String? = null,
        isFreeRecall: Boolean = false,
    ): PracticeItem

    /**
     * Edits an item's content. Review history and the derived scheduling cache
     * are deliberately untouched: editing what an item asks is not a reason to
     * forget how well the learner knows it.
     */
    suspend fun update(
        id: String,
        prompt: String,
        expectedAnswer: String,
        explanation: String? = null,
        isFreeRecall: Boolean = false,
    )

    suspend fun archive(id: String)

    suspend fun restore(id: String)
}
