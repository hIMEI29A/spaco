package com.spaco.app.core.domain.model

/**
 * Where an item sits in the learner's memory. `LAPSED` means it was known and
 * then answered incorrectly; `NEW` means it has never been reviewed.
 */
enum class LearningState {
    NEW,
    LEARNING,
    REVIEWING,
    MASTERED,
    LAPSED,
}
