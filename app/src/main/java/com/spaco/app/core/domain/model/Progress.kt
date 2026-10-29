package com.spaco.app.core.domain.model

/**
 * Accuracy over a recent window of reviews.
 *
 * [percent] is `null` when there is nothing to compute from, so the UI can show
 * "not yet available" rather than a misleading zero.
 */
data class Accuracy(
    val correct: Int,
    val total: Int,
    val windowDays: Int,
) {
    val percent: Int? get() = if (total == 0) null else Math.round(correct * 100f / total)

    /** True when some history exists but it is too thin to be representative. */
    val isLimited: Boolean get() = total in 1 until SPARSE_THRESHOLD

    val hasHistory: Boolean get() = total > 0

    companion object {
        const val SPARSE_THRESHOLD = 5
    }
}

/** Progress for one deck. */
data class DeckProgress(
    val deckId: String,
    val deckName: String,
    val totalItems: Int,
    val dueCount: Int,
    val stateCounts: Map<LearningState, Int>,
    val accuracy: Accuracy,
    val streakDays: Int,
) {
    fun countIn(state: LearningState): Int = stateCounts[state] ?: 0

    val hasItems: Boolean get() = totalItems > 0
}

/** Progress across every non-archived deck. */
data class OverallProgress(
    val totalItems: Int,
    val dueCount: Int,
    val stateCounts: Map<LearningState, Int>,
    val accuracy: Accuracy,
    val streakDays: Int,
)
