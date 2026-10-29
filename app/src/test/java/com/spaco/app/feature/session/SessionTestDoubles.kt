package com.spaco.app.feature.session

import com.spaco.app.core.data.IdGenerator
import com.spaco.app.core.domain.model.Deck
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.ReviewEvent
import com.spaco.app.core.domain.model.SessionState
import com.spaco.app.core.domain.model.SessionStatus
import com.spaco.app.core.domain.repository.DeckRepository
import com.spaco.app.core.domain.repository.ItemRepository
import com.spaco.app.core.domain.repository.ReviewRepository
import com.spaco.app.core.domain.repository.SessionRepository
import com.spaco.app.core.domain.scheduling.Scheduler
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeReviewScheduler : com.spaco.app.core.domain.repository.ReviewScheduler {
    var periodicScheduled = false
    val nextDueChecks = mutableListOf<Instant?>()
    var cancelled = false

    override fun schedulePeriodic() {
        periodicScheduled = true
    }

    override fun scheduleNextDueCheck(earliestDueAt: Instant?) {
        nextDueChecks += earliestDueAt
    }

    override fun cancelAll() {
        cancelled = true
    }
}

/**
 * In-memory repositories for session unit tests. They are synchronous on
 * purpose: the ViewModel's own logic is what is under test, not Room.
 */
class FakeItemRepository(
    private val clock: () -> Instant,
    private val ids: IdGenerator,
) : ItemRepository {

    val stored = mutableMapOf<String, PracticeItem>()
    private val changes = MutableStateFlow(0)

    override fun observeForDeck(deckId: String): Flow<List<PracticeItem>> =
        changes.map { stored.values.filter { it.deckId == deckId }.sortedBy { it.createdAt } }

    override suspend fun getForDeck(deckId: String): List<PracticeItem> =
        stored.values.filter { it.deckId == deckId }.sortedBy { it.createdAt }

    override fun observeAllActive(): Flow<List<PracticeItem>> =
        changes.map { stored.values.filterNot { it.isArchived } }

    override suspend fun getAllDueItems(now: Instant): List<PracticeItem> =
        Scheduler.dueOrder(stored.values.filterNot { it.isArchived }, now)

    override fun observeDueItems(deckId: String, now: Instant): Flow<List<PracticeItem>> =
        observeForDeck(deckId).map { Scheduler.dueOrder(it, now) }

    override suspend fun getDueItems(deckId: String, now: Instant): List<PracticeItem> =
        Scheduler.dueOrder(getForDeck(deckId), now)

    override suspend fun getById(id: String): PracticeItem? = stored[id]

    override suspend fun getEarliestNextDueAt(): Instant? =
        stored.values.filterNot { it.isArchived }.minOfOrNull { it.schedule.nextDueAt }

    override fun observeEarliestNextDueAt(): Flow<Instant?> =
        changes.map { stored.values.filterNot { it.isArchived }.minOfOrNull { it.schedule.nextDueAt } }

    override suspend fun add(
        deckId: String,
        prompt: String,
        expectedAnswer: String,
        explanation: String?,
        isFreeRecall: Boolean,
    ): PracticeItem {
        val createdAt = clock()
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
        stored[item.id] = item
        changes.value += 1
        return item
    }

    override suspend fun update(
        id: String,
        prompt: String,
        expectedAnswer: String,
        explanation: String?,
        isFreeRecall: Boolean,
    ) {
        stored[id]?.let {
            stored[id] = it.copy(
                prompt = prompt,
                expectedAnswer = expectedAnswer,
                explanation = explanation,
                isFreeRecall = isFreeRecall,
            )
            changes.value += 1
        }
    }

    override suspend fun archive(id: String) {
        stored[id]?.let { stored[id] = it.copy(archivedAt = clock()); changes.value += 1 }
    }

    override suspend fun restore(id: String) {
        stored[id]?.let { stored[id] = it.copy(archivedAt = null); changes.value += 1 }
    }

    fun reschedule(id: String, nextDueAt: Instant) {
        stored[id]?.let { stored[id] = it.copy(schedule = it.schedule.copy(nextDueAt = nextDueAt)) }
    }
}

class FakeReviewRepository : ReviewRepository {

    val events = mutableListOf<ReviewEvent>()

    override suspend fun observeForItem(itemId: String): Flow<List<ReviewEvent>> =
        MutableStateFlow(events.filter { it.itemId == itemId })

    override suspend fun getForItem(itemId: String): List<ReviewEvent> =
        events.filter { it.itemId == itemId }

    override fun observeInWindow(deckId: String, since: Instant): Flow<List<ReviewEvent>> =
        MutableStateFlow(events.filter { it.answeredAt >= since })

    override suspend fun getInWindow(deckId: String, since: Instant): List<ReviewEvent> =
        events.filter { it.answeredAt >= since }

    override fun observeAll(): Flow<List<ReviewEvent>> = MutableStateFlow(events.toList())

    override suspend fun getAll(): List<ReviewEvent> = events.toList()

    override suspend fun recordReview(
        itemId: String,
        correct: Boolean,
        gradedBy: GradedBy,
        answeredAt: Instant,
        latencyMs: Long?,
    ): ReviewEvent {
        val event = ReviewEvent(
            id = "event-${events.size}",
            itemId = itemId,
            answeredAt = answeredAt,
            correct = correct,
            gradedBy = gradedBy,
            latencyMs = latencyMs,
        )
        events += event
        return event
    }
}

class FakeSessionRepository : SessionRepository {

    val sessions = mutableMapOf<String, SessionState>()

    override suspend fun getById(id: String): SessionState? = sessions[id]

    override suspend fun getActiveForDeck(deckId: String): SessionState? =
        sessions.values
            .filter { it.deckId == deckId && it.status == SessionStatus.ACTIVE }
            .maxByOrNull { it.startedAt }

    override fun observeForDeck(deckId: String): Flow<List<SessionState>> =
        MutableStateFlow(sessions.values.filter { it.deckId == deckId })

    override suspend fun save(session: SessionState) {
        sessions[session.id] = session
    }

    override suspend fun delete(id: String) {
        sessions.remove(id)
    }
}

class FakeDeckRepository : DeckRepository {
    private val decks = mutableMapOf<String, Deck>()

    fun put(deck: Deck) {
        decks[deck.id] = deck
    }

    override fun observeActive(): Flow<List<Deck>> =
        MutableStateFlow(decks.values.filterNot { it.isArchived })

    override fun observeAll(): Flow<List<Deck>> = MutableStateFlow(decks.values.toList())

    override suspend fun getById(id: String): Deck? = decks[id]

    override suspend fun create(skillId: String, name: String): Deck = throw UnsupportedOperationException()

    override suspend fun rename(id: String, name: String) {
        decks[id]?.let { decks[id] = it.copy(name = name) }
    }

    override suspend fun archive(id: String) {
        decks[id]?.let { decks[id] = it.copy(archivedAt = Instant.EPOCH) }
    }

    override suspend fun restore(id: String) {
        decks[id]?.let { decks[id] = it.copy(archivedAt = null) }
    }
}
