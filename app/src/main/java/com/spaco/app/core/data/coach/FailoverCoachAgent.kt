package com.spaco.app.core.data.coach

import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachSelection
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.GeneratedExercise
import com.spaco.app.core.domain.coach.GeneratedExplanation

/**
 * Tries [primary] first — the remote coach — and answers from [fallback] — the
 * built-in offline coach — whenever the primary has no usable opinion.
 *
 * This is where "the model is optional" is actually implemented: the primary
 * signals every failure and every unusable answer as `null`, and the fallback
 * then produces the same result the app had before a remote coach existed. The
 * delegate that runs is the only difference the learner could notice.
 */
class FailoverCoachAgent(
    private val primary: CoachAgent,
    private val fallback: CoachAgent,
) : CoachAgent {

    override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? =
        primary.selectNextItem(request) ?: fallback.selectNextItem(request)

    override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? =
        primary.generateExercise(request) ?: fallback.generateExercise(request)

    override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? =
        primary.generateExplanation(request) ?: fallback.generateExplanation(request)
}
