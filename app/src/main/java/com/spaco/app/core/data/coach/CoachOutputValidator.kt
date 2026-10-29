package com.spaco.app.core.data.coach

import com.spaco.app.core.domain.coach.CoachSelection
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.GeneratedExercise
import com.spaco.app.core.domain.coach.GeneratedExplanation

/**
 * The single place coach output is checked before it can reach the learner.
 *
 * Anything empty, malformed, or attributable to a different item is turned into
 * `null` — discarded, never surfaced — so the caller's fallback to stored
 * content is always the safe path.
 */
object CoachOutputValidator {

    fun exercise(result: GeneratedExercise?, request: ExerciseRequest): GeneratedExercise? {
        val candidate = result ?: return null
        if (candidate.targetItemId != request.item.id) return null
        if (candidate.prompt.isBlank() || candidate.expectedAnswer.isBlank()) return null
        return candidate
    }

    fun explanation(result: GeneratedExplanation?, request: ExplanationRequest): GeneratedExplanation? {
        val candidate = result ?: return null
        if (candidate.targetItemId != request.item.id) return null
        if (candidate.text.isBlank()) return null
        return candidate
    }

    fun selection(result: CoachSelection?, request: CoachSelectionRequest): CoachSelection? {
        val candidate = result ?: return null
        if (candidate.targetItemId.isBlank()) return null
        val known = request.candidates.any { it.itemId == candidate.targetItemId }
        if (!known) return null
        if (candidate.targetItemId in request.excludedItemIds) return null
        return candidate
    }
}
