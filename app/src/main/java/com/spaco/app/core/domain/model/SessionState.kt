package com.spaco.app.core.domain.model

import java.time.Instant

enum class SessionStatus {
    ACTIVE,
    ENDED,
    DISCARDED,
}

/**
 * A practice session. Persisted while active so an interrupted session can be
 * resumed without losing the answers already given.
 *
 * [plannedItemIds] is the fixed item budget chosen when the session started;
 * [answeredItemIds] grows as answers are graded and is what stops an item from
 * repeating inside one session.
 */
data class SessionState(
    val id: String,
    val deckId: String,
    val startedAt: Instant,
    val itemBudget: Int,
    val plannedItemIds: List<String>,
    val answeredItemIds: List<String>,
    val correctCount: Int,
    val endedAt: Instant? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
) {
    val answeredCount: Int get() = answeredItemIds.size

    val isActive: Boolean get() = status == SessionStatus.ACTIVE

    fun hasAnswered(itemId: String): Boolean = itemId in answeredItemIds

    /** Items from the budget that have not been answered yet. */
    val remainingItemIds: List<String> get() = plannedItemIds.filterNot { hasAnswered(it) }
}
