package com.spaco.app.core.domain.model

import java.time.Instant

/**
 * A study subject a learner is working on. Decks belong to a skill.
 */
data class Skill(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val archivedAt: Instant? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}
