package com.spaco.app.core.domain.coach

import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.ReviewEvent
import java.time.Instant

/**
 * Plain, framework-free request and response types for the coach port.
 *
 * Responses are nullable on purpose: "no opinion" is a first-class result, and
 * the caller's fallback to stored content is the normal path, not an error path.
 */

/** One item the coach may choose from, reduced to what a choice needs. */
data class CoachCandidate(
    val itemId: String,
    val prompt: String,
    val expectedAnswer: String,
    val learningState: LearningState,
    val nextDueAt: Instant,
)

data class CoachSelectionRequest(
    val deckId: String,
    val candidates: List<CoachCandidate>,
    val recentEvents: List<ReviewEvent> = emptyList(),
    val excludedItemIds: Set<String> = emptySet(),
    val now: Instant,
)

data class CoachSelection(
    /** Must name one of the supplied candidates; the caller validates this. */
    val targetItemId: String,
    val rationale: String? = null,
)

data class ExerciseRequest(
    /** The item the generated exercise must remain attributable to. */
    val item: PracticeItem,
    /** Sibling items in the same deck, available as distractor material. */
    val siblings: List<PracticeItem> = emptyList(),
    /** Makes generation reproducible: the same (item, seed) yields the same output. */
    val seed: Long,
)

data class GeneratedExercise(
    val targetItemId: String,
    val prompt: String,
    val expectedAnswer: String,
    val explanation: String? = null,
    /** Optional wrong answers for the UI to offer alongside the prompt. */
    val distractors: List<String> = emptyList(),
)

data class ExplanationRequest(
    val item: PracticeItem,
    val grade: Grade,
    val seed: Long = 0L,
)

data class GeneratedExplanation(
    val targetItemId: String,
    val text: String,
)
