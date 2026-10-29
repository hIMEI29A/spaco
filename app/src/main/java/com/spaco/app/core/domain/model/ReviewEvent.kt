package com.spaco.app.core.domain.model

import java.time.Instant

/**
 * One graded answer. Review events are append-only: recording a new answer never
 * rewrites an earlier one, which is what makes scheduling reproducible.
 */
data class ReviewEvent(
    val id: String,
    val itemId: String,
    val answeredAt: Instant,
    val correct: Boolean,
    val gradedBy: GradedBy,
    val latencyMs: Long? = null,
) {
    val grade: Grade get() = if (correct) Grade.CORRECT else Grade.INCORRECT
}
