package com.spaco.app.core.data.coach

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.coach.CoachCandidate
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.ReviewEvent
import com.spaco.app.core.domain.scheduling.Scheduler
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeterministicCoachAgentTest {

    private val coach = DeterministicCoachAgent()
    private val now = Instant.parse("2026-03-01T09:00:00Z")

    private fun item(
        id: String,
        deckId: String = "deck-1",
        prompt: String = "prompt-$id",
        expectedAnswer: String = "answer-$id",
        explanation: String? = null,
        createdAt: Instant = now,
    ) = PracticeItem(
        id = id,
        deckId = deckId,
        prompt = prompt,
        expectedAnswer = expectedAnswer,
        explanation = explanation,
        createdAt = createdAt,
        schedule = Scheduler.newItemSchedule(createdAt),
    )

    private fun candidate(
        id: String,
        state: LearningState = LearningState.NEW,
        dueAt: Instant = now,
    ) = CoachCandidate(
        itemId = id,
        prompt = "prompt-$id",
        expectedAnswer = "answer-$id",
        learningState = state,
        nextDueAt = dueAt,
    )

    // --- selection (4.2) ----------------------------------------------------

    @Test
    fun `no candidates yields no selection`() = runTest {
        val request = CoachSelectionRequest(deckId = "deck-1", candidates = emptyList(), now = now)

        assertThat(coach.selectNextItem(request)).isNull()
    }

    @Test
    fun `a lapsed item is chosen ahead of everything else`() = runTest {
        val request = CoachSelectionRequest(
            deckId = "deck-1",
            candidates = listOf(
                candidate("overdue", dueAt = now.minusSeconds(10_000)),
                candidate("lapsed", state = LearningState.LAPSED, dueAt = now),
            ),
            now = now,
        )

        assertThat(coach.selectNextItem(request)?.targetItemId).isEqualTo("lapsed")
    }

    @Test
    fun `among equally-stated items the most overdue wins`() = runTest {
        val request = CoachSelectionRequest(
            deckId = "deck-1",
            candidates = listOf(
                candidate("recent", dueAt = now.minusSeconds(60)),
                candidate("oldest", dueAt = now.minusSeconds(9_999)),
            ),
            now = now,
        )

        assertThat(coach.selectNextItem(request)?.targetItemId).isEqualTo("oldest")
    }

    @Test
    fun `among equally-overdue items the weaker recent accuracy wins`() = runTest {
        val events = listOf(
            event("weak-1", "weak", correct = false),
            event("weak-2", "weak", correct = false),
            event("strong-1", "strong", correct = true),
            event("strong-2", "strong", correct = true),
        )
        val request = CoachSelectionRequest(
            deckId = "deck-1",
            candidates = listOf(candidate("strong"), candidate("weak")),
            recentEvents = events,
            now = now,
        )

        assertThat(coach.selectNextItem(request)?.targetItemId).isEqualTo("weak")
    }

    @Test
    fun `when everything ties the id breaks the tie deterministically`() = runTest {
        val request = CoachSelectionRequest(
            deckId = "deck-1",
            candidates = listOf(candidate("c"), candidate("a"), candidate("b")),
            now = now,
        )

        assertThat(coach.selectNextItem(request)?.targetItemId).isEqualTo("a")
        assertThat(coach.selectNextItem(request)?.targetItemId).isEqualTo("a")
    }

    @Test
    fun `an excluded item is never selected and the choice stays inside the deck`() = runTest {
        val request = CoachSelectionRequest(
            deckId = "deck-1",
            candidates = listOf(candidate("a"), candidate("b")),
            excludedItemIds = setOf("a"),
            now = now,
        )

        val chosen = coach.selectNextItem(request)
        assertThat(chosen?.targetItemId).isEqualTo("b")
        assertThat(request.candidates.map { it.itemId }).contains(chosen?.targetItemId)
    }

    // --- generation (4.3) ---------------------------------------------------

    @Test
    fun `the same item and seed always produce the same exercise`() = runTest {
        val target = item("i1", expectedAnswer = "alpha beta gamma", explanation = "why")
        val request = ExerciseRequest(item = target, siblings = listOf(item("i2")), seed = 42L)

        val first = coach.generateExercise(request)
        val second = coach.generateExercise(request)

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `a generated exercise is always attributable to its target item`() = runTest {
        val target = item("i1", expectedAnswer = "two words")
        val request = ExerciseRequest(item = target, seed = 1L)

        assertThat(coach.generateExercise(request)?.targetItemId).isEqualTo("i1")
    }

    @Test
    fun `generation never overwrites the stored item content`() = runTest {
        val target = item("i1", prompt = "original prompt", expectedAnswer = "alpha beta")
        val before = target.copy()

        coach.generateExercise(ExerciseRequest(item = target, seed = 7L))

        assertThat(target).isEqualTo(before)
        assertThat(target.prompt).isEqualTo("original prompt")
        assertThat(target.expectedAnswer).isEqualTo("alpha beta")
    }

    @Test
    fun `a multi-word answer can be turned into a cloze deletion`() = runTest {
        val target = item("i1", expectedAnswer = "alpha beta gamma")

        // Seed 0 selects the first transform, the cloze deletion.
        val exercise = coach.generateExercise(ExerciseRequest(item = target, seed = 0L))!!

        assertThat(exercise.prompt).contains("____")
        assertThat(exercise.prompt).contains("Fill in the blank")
        assertThat(target.expectedAnswer).contains(exercise.expectedAnswer)
    }

    @Test
    fun `distractors are drawn from sibling items and never equal the answer`() = runTest {
        val target = item("i1", expectedAnswer = "correct")
        val siblings = listOf(
            item("i2", expectedAnswer = "wrong-one"),
            item("i3", expectedAnswer = "wrong-two"),
            item("i4", deckId = "other-deck", expectedAnswer = "other-deck-answer"),
            item("i5", expectedAnswer = "correct"),
        )

        val exercise = coach.generateExercise(
            ExerciseRequest(item = target, siblings = siblings, seed = 3L),
        )!!

        assertThat(exercise.distractors).isNotEmpty()
        assertThat(exercise.distractors).doesNotContain("correct")
        assertThat(exercise.distractors).doesNotContain("other-deck-answer")
        assertThat(exercise.distractors).containsExactly("wrong-one", "wrong-two")
    }

    @Test
    fun `explanation prefers the stored text and otherwise falls back to a template`() = runTest {
        val stored = item("i1", explanation = "stored explanation")
        val withoutExplanation = item("i2")

        val fromStored = coach.generateExplanation(
            ExplanationRequest(item = stored, grade = Grade.CORRECT),
        )!!
        val templated = coach.generateExplanation(
            ExplanationRequest(item = withoutExplanation, grade = Grade.INCORRECT),
        )!!

        assertThat(fromStored.text).isEqualTo("stored explanation")
        assertThat(fromStored.targetItemId).isEqualTo("i1")
        assertThat(templated.text).contains("Not quite")
        assertThat(templated.text).contains(withoutExplanation.expectedAnswer)
    }

    private fun event(id: String, itemId: String, correct: Boolean) = ReviewEvent(
        id = id,
        itemId = itemId,
        answeredAt = now,
        correct = correct,
        gradedBy = GradedBy.SYSTEM,
    )
}
