package com.spaco.app.feature.session

import com.spaco.app.core.data.coach.CoachOutputValidator
import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachCandidate
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.ReviewEvent
import com.spaco.app.core.domain.scheduling.Scheduler
import java.time.Instant

/**
 * Chooses the next item a session should present.
 *
 * The coach's choice is advisory: it is validated against the items actually
 * available, and if the coach has no usable opinion — it returns `null`, throws,
 * times out, or names an ineligible item — the scheduler's due order is used
 * instead. Either way the session has a definite next item, so coaching can
 * never leave the learner stuck.
 */
class SessionItemSelector(private val coach: CoachAgent) {

    suspend fun selectNext(
        deckId: String,
        availableItems: List<PracticeItem>,
        recentEvents: List<ReviewEvent>,
        now: Instant,
    ): PracticeItem? {
        if (availableItems.isEmpty()) return null

        val request = CoachSelectionRequest(
            deckId = deckId,
            candidates = availableItems.map { it.toCandidate() },
            recentEvents = recentEvents,
            excludedItemIds = emptySet(),
            now = now,
        )

        val selection = runCatching { coach.selectNextItem(request) }
            .getOrElse { cause ->
                if (cause is kotlin.coroutines.cancellation.CancellationException) throw cause
                null
            }
        val validated = CoachOutputValidator.selection(selection, request)
        val chosen = validated?.let { selection ->
            availableItems.firstOrNull { it.id == selection.targetItemId }
        }

        return chosen ?: fallbackOrder(availableItems, now)
    }

    /** Most overdue first, with a stable id tiebreaker so the order never wobbles. */
    private fun fallbackOrder(items: List<PracticeItem>, now: Instant): PracticeItem =
        Scheduler.dueOrder(items, now).firstOrNull()
            ?: items.minWith(compareBy({ it.schedule.nextDueAt }, { it.id }))

    private fun PracticeItem.toCandidate() = CoachCandidate(
        itemId = id,
        prompt = prompt,
        expectedAnswer = expectedAnswer,
        learningState = schedule.state,
        nextDueAt = schedule.nextDueAt,
    )
}
