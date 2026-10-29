package com.spaco.app.core.domain.coach

/**
 * The coach boundary. Everything the app needs from "intelligence" goes through
 * these three suspend functions, so a different implementation can be
 * substituted without touching session flow, review recording, or scheduling.
 *
 * Implementations must be safe to call with no network access. Every method may
 * return `null`, meaning "no opinion"; callers fall back to stored content.
 */
interface CoachAgent {

    suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection?

    suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise?

    suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation?
}
