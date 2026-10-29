package com.spaco.app.core.data.repository

import com.spaco.app.core.domain.model.Accuracy
import com.spaco.app.core.domain.model.DeckProgress
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.OverallProgress
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.ReviewEvent
import com.spaco.app.core.domain.repository.DeckRepository
import com.spaco.app.core.domain.repository.ItemRepository
import com.spaco.app.core.domain.repository.ProgressRepository
import com.spaco.app.core.domain.repository.ReviewRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Computes progress from scheduling state and review history in the device's
 * local time zone, so "practice day" and streak boundaries match what the
 * learner experiences rather than UTC.
 */
@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val itemRepository: ItemRepository,
    private val reviewRepository: ReviewRepository,
    private val deckRepository: DeckRepository,
    private val zone: ZoneId,
) : ProgressRepository {

    override fun observeDeckProgress(deckId: String, now: Instant): Flow<DeckProgress?> =
        combine(
            itemRepository.observeForDeck(deckId),
            reviewRepository.observeAll(),
            deckRepository.observeAll(),
        ) { items, allReviews, decks ->
            val deck = decks.firstOrNull { it.id == deckId } ?: return@combine null
            val itemIds = items.map { it.id }.toSet()
            val deckReviews = allReviews.filter { it.itemId in itemIds }
            DeckProgress(
                deckId = deckId,
                deckName = deck.name,
                totalItems = items.size,
                dueCount = items.count { it.isDue(now) },
                stateCounts = items.stateCounts(),
                accuracy = accuracyOf(deckReviews, now),
                streakDays = streakOf(deckReviews, now),
            )
        }

    override fun observeOverallProgress(now: Instant): Flow<OverallProgress> =
        combine(
            itemRepository.observeAllActive(),
            deckRepository.observeAll(),
            reviewRepository.observeAll(),
        ) { items, decks, reviews ->
            // Items in an archived deck are not "active" for overall totals even
            // though the item itself is not archived.
            val activeDeckIds = decks.filterNot { it.isArchived }.map { it.id }.toSet()
            val activeItems = items.filter { it.deckId in activeDeckIds }
            val activeItemIds = activeItems.map { it.id }.toSet()
            val activeReviews = reviews.filter { it.itemId in activeItemIds }

            OverallProgress(
                totalItems = activeItems.size,
                dueCount = activeItems.count { it.isDue(now) },
                stateCounts = activeItems.stateCounts(),
                accuracy = accuracyOf(activeReviews, now),
                streakDays = streakOf(activeReviews, now),
            )
        }

    private fun List<PracticeItem>.stateCounts(): Map<LearningState, Int> =
        LearningState.entries.associateWith { state -> count { it.schedule.state == state } }

    private fun accuracyOf(reviews: List<ReviewEvent>, now: Instant): Accuracy {
        val windowStart = now.minus(RECENT_WINDOW)
        val recent = reviews.filter { it.answeredAt >= windowStart }
        return Accuracy(
            correct = recent.count { it.correct },
            total = recent.size,
            windowDays = RECENT_WINDOW.toDays().toInt(),
        )
    }

    /**
     * Consecutive local days with at least one answer. A gap resets the run; if
     * the learner has not practised today yet, yesterday's run still counts.
     */
    private fun streakOf(reviews: List<ReviewEvent>, now: Instant): Int {
        if (reviews.isEmpty()) return 0
        val days = reviews.map { it.answeredAt.atZone(zone).toLocalDate() }.toSet()
        var day = now.atZone(zone).toLocalDate()
        if (day !in days) day = day.minusDays(1)

        var streak = 0
        while (day in days) {
            streak += 1
            day = day.minusDays(1)
        }
        return streak
    }

    private companion object {
        val RECENT_WINDOW: Duration = Duration.ofDays(7)
    }
}
