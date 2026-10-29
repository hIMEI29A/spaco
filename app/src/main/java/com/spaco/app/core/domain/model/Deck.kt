package com.spaco.app.core.domain.model

import java.time.Instant

/**
 * A set of practice items under a skill. Exactly one deck is the learner's
 * active deck at a time.
 */
data class Deck(
    val id: String,
    val skillId: String,
    val name: String,
    val createdAt: Instant,
    val archivedAt: Instant? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}
