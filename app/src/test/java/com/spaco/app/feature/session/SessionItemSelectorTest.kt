package com.spaco.app.feature.session

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.data.coach.DeterministicCoachAgent
import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachSelection
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.GeneratedExercise
import com.spaco.app.core.domain.coach.GeneratedExplanation
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.ReviewEvent
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SessionItemSelectorTest {

    private val now = Instant.parse("2026-04-01T08:00:00Z")

    private fun item(id: String, dueAt: Instant = now, state: LearningState = LearningState.NEW) =
        PracticeItem(
            id = id,
            deckId = "deck-1",
            prompt = "prompt-$id",
            expectedAnswer = "answer-$id",
            createdAt = dueAt,
            schedule = com.spaco.app.core.domain.scheduling.Scheduler
                .newItemSchedule(dueAt)
                .copy(nextDueAt = dueAt, state = state),
        )

    @Test
    fun `no available items yields no next item`() = runTest {
        val selector = SessionItemSelector(NullCoachAgent())

        assertThat(selector.selectNext("deck-1", emptyList(), emptyList(), now)).isNull()
    }

    @Test
    fun `a valid coach choice is honored`() = runTest {
        val items = listOf(item("a", dueAt = now.minusSeconds(10)), item("b", dueAt = now))
        val selector = SessionItemSelector(FixedCoachAgent("b"))

        assertThat(selector.selectNext("deck-1", items, emptyList(), now)?.id).isEqualTo("b")
    }

    @Test
    fun `a coach choice outside the available items falls back to due order`() = runTest {
        val items = listOf(item("a", dueAt = now.minusSeconds(10)), item("b", dueAt = now))
        val selector = SessionItemSelector(FixedCoachAgent("not-in-deck"))

        assertThat(selector.selectNext("deck-1", items, emptyList(), now)?.id).isEqualTo("a")
    }

    @Test
    fun `a null coach opinion falls back to due order with stored content`() = runTest {
        val items = listOf(item("a", dueAt = now.minusSeconds(10)), item("b", dueAt = now))
        val selector = SessionItemSelector(NullCoachAgent())

        val chosen = selector.selectNext("deck-1", items, emptyList(), now)!!
        assertThat(chosen.id).isEqualTo("a")
        // Stored content is presented, unchanged.
        assertThat(chosen.prompt).isEqualTo("prompt-a")
        assertThat(chosen.expectedAnswer).isEqualTo("answer-a")
    }

    @Test
    fun `a throwing coach still lets the session proceed`() = runTest {
        val items = listOf(item("a", dueAt = now.minusSeconds(10)), item("b", dueAt = now))
        val selector = SessionItemSelector(ThrowingCoachAgent())

        assertThat(selector.selectNext("deck-1", items, emptyList(), now)?.id).isEqualTo("a")
    }

    @Test
    fun `substituting the coach does not change the session's observable order`() = runTest {
        val items = listOf(
            item("newest", dueAt = now.minusSeconds(10)),
            item("oldest", dueAt = now.minusSeconds(900)),
            item("middle", dueAt = now.minusSeconds(400)),
        )

        val withDeterministic = runSession(DeterministicCoachAgent(), items)
        val withFake = runSession(NullCoachAgent(), items)

        assertThat(withFake).isEqualTo(withDeterministic)
        assertThat(withDeterministic).containsExactly("oldest", "middle", "newest").inOrder()
    }

    /** A minimal session loop: pick, answer, record, repeat until nothing is left. */
    private suspend fun runSession(coach: CoachAgent, items: List<PracticeItem>): List<String> {
        val selector = SessionItemSelector(coach)
        val available = items.toMutableList()
        val events = mutableListOf<ReviewEvent>()
        var clock = now

        while (true) {
            val next = selector.selectNext("deck-1", available, events, clock) ?: break
            available.remove(next)
            events += ReviewEvent(
                id = "event-${events.size}",
                itemId = next.id,
                answeredAt = clock,
                correct = true,
                gradedBy = GradedBy.SYSTEM,
            )
            clock = clock.plusSeconds(60)
        }

        return events.map { it.itemId }
    }

    private class NullCoachAgent : CoachAgent {
        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? = null
        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? = null
        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? = null
    }

    private class FixedCoachAgent(private val itemId: String) : CoachAgent {
        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection =
            CoachSelection(itemId)

        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? = null
        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? = null
    }

    private class ThrowingCoachAgent : CoachAgent {
        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection =
            error("coach unavailable")

        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? =
            error("coach unavailable")

        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? =
            error("coach unavailable")
    }
}
