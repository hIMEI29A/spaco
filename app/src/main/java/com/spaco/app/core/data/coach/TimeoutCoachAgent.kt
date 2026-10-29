package com.spaco.app.core.data.coach

import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachSelection
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.GeneratedExercise
import com.spaco.app.core.domain.coach.GeneratedExplanation
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/**
 * Bounds how long the session will wait on any coach delegate, so a future slow
 * or network-backed implementation cannot stall practice.
 *
 * A timeout — and any failure the delegate throws — becomes `null`, the
 * "no opinion" result, so the caller falls back to stored content instead of the
 * session being interrupted.
 */
class TimeoutCoachAgent(
    private val delegate: CoachAgent,
    private val timeout: Duration = DEFAULT_TIMEOUT,
) : CoachAgent {

    override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? =
        guarded { delegate.selectNextItem(request) }

    override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? =
        guarded { delegate.generateExercise(request) }

    override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? =
        guarded { delegate.generateExplanation(request) }

    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        withTimeoutOrNull(timeout.toMillis()) { block() }
    } catch (cancellation: CancellationException) {
        // A timeout is already turned into null by withTimeoutOrNull; any other
        // cancellation is the caller's and must propagate.
        throw cancellation
    } catch (_: Exception) {
        null
    }

    companion object {
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(2)
    }
}
