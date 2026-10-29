package com.spaco.app.core.data.coach

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachCandidate
import com.spaco.app.core.domain.coach.CoachSelection
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.GeneratedExercise
import com.spaco.app.core.domain.coach.GeneratedExplanation
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.scheduling.Scheduler
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CoachOutputValidatorTest {

    private val now = Instant.parse("2026-03-01T09:00:00Z")

    private fun item(id: String, deckId: String = "deck-1") = PracticeItem(
        id = id,
        deckId = deckId,
        prompt = "prompt-$id",
        expectedAnswer = "answer-$id",
        createdAt = now,
        schedule = Scheduler.newItemSchedule(now),
    )

    private fun candidate(id: String) = CoachCandidate(
        itemId = id,
        prompt = "prompt-$id",
        expectedAnswer = "answer-$id",
        learningState = LearningState.NEW,
        nextDueAt = now,
    )

    @Test
    fun `a well-formed exercise for the requested item is kept`() {
        val request = ExerciseRequest(item = item("i1"), seed = 1L)
        val result = GeneratedExercise(targetItemId = "i1", prompt = "p", expectedAnswer = "a")

        assertThat(CoachOutputValidator.exercise(result, request)).isEqualTo(result)
    }

    @Test
    fun `an exercise with an empty prompt or answer is discarded`() {
        val request = ExerciseRequest(item = item("i1"), seed = 1L)

        assertThat(
            CoachOutputValidator.exercise(
                GeneratedExercise("i1", prompt = "  ", expectedAnswer = "a"),
                request,
            ),
        ).isNull()
        assertThat(
            CoachOutputValidator.exercise(
                GeneratedExercise("i1", prompt = "p", expectedAnswer = ""),
                request,
            ),
        ).isNull()
    }

    @Test
    fun `an exercise aimed at a different item is discarded`() {
        val request = ExerciseRequest(item = item("i1"), seed = 1L)
        val result = GeneratedExercise(targetItemId = "someone-else", prompt = "p", expectedAnswer = "a")

        assertThat(CoachOutputValidator.exercise(result, request)).isNull()
    }

    @Test
    fun `null output is discarded`() {
        assertThat(
            CoachOutputValidator.exercise(null, ExerciseRequest(item = item("i1"), seed = 1L)),
        ).isNull()
        assertThat(
            CoachOutputValidator.explanation(
                null,
                ExplanationRequest(item = item("i1"), grade = Grade.CORRECT),
            ),
        ).isNull()
    }

    @Test
    fun `an explanation for a different item or with blank text is discarded`() {
        val request = ExplanationRequest(item = item("i1"), grade = Grade.CORRECT)

        assertThat(
            CoachOutputValidator.explanation(GeneratedExplanation("i2", "text"), request),
        ).isNull()
        assertThat(
            CoachOutputValidator.explanation(GeneratedExplanation("i1", "   "), request),
        ).isNull()
    }

    @Test
    fun `a selection outside the candidate set or already answered is discarded`() {
        val request = CoachSelectionRequest(
            deckId = "deck-1",
            candidates = listOf(candidate("a"), candidate("b")),
            excludedItemIds = setOf("b"),
            now = now,
        )

        assertThat(CoachOutputValidator.selection(CoachSelection("a"), request)).isNotNull()
        assertThat(CoachOutputValidator.selection(CoachSelection("missing"), request)).isNull()
        assertThat(CoachOutputValidator.selection(CoachSelection("b"), request)).isNull()
    }
}

class TimeoutCoachAgentTest {

    private val now = Instant.parse("2026-03-01T09:00:00Z")

    private fun item(id: String) = PracticeItem(
        id = id,
        deckId = "deck-1",
        prompt = "prompt-$id",
        expectedAnswer = "answer-$id",
        createdAt = now,
        schedule = Scheduler.newItemSchedule(now),
    )

    @Test
    fun `a delegate that answers within the timeout is passed through`() = runTest {
        val delegate = object : CoachAgent {
            override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? =
                CoachSelection(request.candidates.first().itemId)

            override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? = null
            override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? = null
        }
        val agent = TimeoutCoachAgent(delegate, Duration.ofSeconds(1))
        val request = CoachSelectionRequest(
            deckId = "deck-1",
            candidates = listOf(
                CoachCandidate("a", "p", "a", LearningState.NEW, now),
            ),
            now = now,
        )

        assertThat(agent.selectNextItem(request)?.targetItemId).isEqualTo("a")
    }

    @Test
    fun `a delegate that exceeds the timeout returns null instead of throwing`() = runTest {
        val agent = TimeoutCoachAgent(SlowCoachAgent(), Duration.ofSeconds(1))
        val request = CoachSelectionRequest(
            deckId = "deck-1",
            candidates = listOf(CoachCandidate("a", "p", "a", LearningState.NEW, now)),
            now = now,
        )

        assertThat(agent.selectNextItem(request)).isNull()
    }

    @Test
    fun `a delegate that throws returns null instead of throwing`() = runTest {
        val agent = TimeoutCoachAgent(ThrowingCoachAgent(), Duration.ofSeconds(1))

        assertThat(agent.generateExercise(ExerciseRequest(item = item("i1"), seed = 1L))).isNull()
    }

    private class SlowCoachAgent : CoachAgent {
        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? {
            delay(10_000)
            return CoachSelection("a")
        }

        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? = null
        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? = null
    }

    private class ThrowingCoachAgent : CoachAgent {
        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? =
            error("boom")

        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? =
            error("boom")

        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? =
            error("boom")
    }
}
