package com.spaco.app.core.data.coach

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachSelection
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.GeneratedExercise
import com.spaco.app.core.domain.coach.GeneratedExplanation
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.ItemSchedule
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FailoverCoachAgentTest {

    private val item = PracticeItem(
        id = "item-a",
        deckId = "deck-1",
        prompt = "What is the capital of France?",
        expectedAnswer = "Paris",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        schedule = ItemSchedule(
            nextDueAt = Instant.parse("2026-01-01T00:00:00Z"),
            state = LearningState.NEW,
            intervalIndex = 0,
            correctStreak = 0,
        ),
    )

    @Test
    fun `the primary answer wins and the fallback is not consulted`() = runBlocking {
        val fallback = CountingCoach()
        val subject = FailoverCoachAgent(primary = FixedCoach(), fallback = fallback)

        val result = subject.generateExercise(ExerciseRequest(item = item, seed = 1L))

        assertThat(result?.prompt).isEqualTo("from primary")
        assertThat(fallback.calls).isEqualTo(0)
    }

    @Test
    fun `a primary with no opinion hands the request to the fallback`() = runBlocking {
        val fallback = CountingCoach()
        val subject = FailoverCoachAgent(primary = SilentCoach(), fallback = fallback)

        val result = subject.generateExercise(ExerciseRequest(item = item, seed = 1L))

        assertThat(result?.prompt).isEqualTo("from fallback")
        assertThat(fallback.calls).isEqualTo(1)
    }

    @Test
    fun `selection and explanation fail over the same way`() = runBlocking {
        val subject = FailoverCoachAgent(primary = SilentCoach(), fallback = CountingCoach())

        assertThat(subject.selectNextItem(CoachSelectionRequest(deckId = "d", candidates = emptyList(), now = Instant.parse("2026-01-01T00:00:00Z"))))
            .isNotNull()
        assertThat(subject.generateExplanation(ExplanationRequest(item = item, grade = Grade.CORRECT)))
            .isNotNull()
    }

    @Test
    fun `both delegates without an opinion yield no opinion`() = runBlocking {
        val subject = FailoverCoachAgent(primary = SilentCoach(), fallback = SilentCoach())

        assertThat(subject.generateExercise(ExerciseRequest(item = item, seed = 1L))).isNull()
    }

    @Test
    fun `cancellation from the primary is not swallowed by the fallback`() {
        val fallback = CountingCoach()
        val subject = FailoverCoachAgent(primary = CancellingCoach(), fallback = fallback)

        val thrown = runCatching {
            runBlocking { subject.generateExercise(ExerciseRequest(item = item, seed = 1L)) }
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
        assertThat(fallback.calls).isEqualTo(0)
    }

    private class FixedCoach : CoachAgent {
        override suspend fun selectNextItem(request: CoachSelectionRequest) =
            CoachSelection(targetItemId = "any")

        override suspend fun generateExercise(request: ExerciseRequest) = GeneratedExercise(
            targetItemId = request.item.id,
            prompt = "from primary",
            expectedAnswer = "primary",
        )

        override suspend fun generateExplanation(request: ExplanationRequest) =
            GeneratedExplanation(targetItemId = request.item.id, text = "from primary")
    }

    private class SilentCoach : CoachAgent {
        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? = null
        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? = null
        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? = null
    }

    private class CancellingCoach : CoachAgent {
        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? =
            throw CancellationException("cancelled")

        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise =
            throw CancellationException("cancelled")

        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation =
            throw CancellationException("cancelled")
    }

    private class CountingCoach : CoachAgent {
        var calls = 0
            private set

        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection {
            calls += 1
            return CoachSelection(targetItemId = "from fallback")
        }

        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise {
            calls += 1
            return GeneratedExercise(
                targetItemId = request.item.id,
                prompt = "from fallback",
                expectedAnswer = "fallback",
            )
        }

        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation {
            calls += 1
            return GeneratedExplanation(targetItemId = request.item.id, text = "from fallback")
        }
    }
}
