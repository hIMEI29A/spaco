package com.spaco.app.core.domain.model

import java.time.Instant

/**
 * The scheduling cache for one item. This is *derived* from the item's review
 * history and is always reproducible from it; it is stored only so that due
 * queries and background work stay cheap. See `Scheduler.recompute`.
 */
data class ItemSchedule(
    val nextDueAt: Instant,
    val state: LearningState,
    val intervalIndex: Int,
    val correctStreak: Int,
) {
    fun isDue(now: Instant): Boolean = !nextDueAt.isAfter(now)

    companion object {
        /**
         * The schedule of an item that has never been reviewed: due immediately.
         */
        fun newItem(createdAt: Instant): ItemSchedule = ItemSchedule(
            nextDueAt = createdAt,
            state = LearningState.NEW,
            intervalIndex = 0,
            correctStreak = 0,
        )
    }
}

/**
 * A single thing the learner practices: a prompt, the answer it expects, and an
 * optional explanation shown after grading.
 */
data class PracticeItem(
    val id: String,
    val deckId: String,
    val prompt: String,
    val expectedAnswer: String,
    val explanation: String? = null,
    val createdAt: Instant,
    val archivedAt: Instant? = null,
    /**
     * When true the learner grades their own attempt instead of the app
     * comparing the answer with [expectedAnswer].
     */
    val isFreeRecall: Boolean = false,
    val schedule: ItemSchedule,
) {
    val isArchived: Boolean get() = archivedAt != null

    fun isDue(now: Instant): Boolean = schedule.isDue(now)
}
