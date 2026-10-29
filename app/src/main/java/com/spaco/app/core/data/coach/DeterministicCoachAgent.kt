package com.spaco.app.core.data.coach

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

/**
 * The MVP coach: fully offline and deterministic, with no dependency beyond the
 * domain model.
 *
 * Determinism is the point. Every answer is a pure function of its request, so
 * sessions are reproducible in tests and a future LLM-backed coach can be
 * dropped in behind the same port without changing what the app expects.
 */
class DeterministicCoachAgent : CoachAgent {

    override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? {
        val eligible = request.candidates.filterNot { it.itemId in request.excludedItemIds }
        if (eligible.isEmpty()) return null

        val accuracy = recentAccuracyByItem(request)

        val chosen = eligible.sortedWith(
            compareByDescending<CoachCandidate> {
                it.learningState == LearningState.LAPSED
            }
                .thenBy { it.nextDueAt }
                .thenBy { accuracy[it.itemId] ?: NO_HISTORY_ACCURACY }
                .thenBy { it.itemId },
        ).first()

        return CoachSelection(
            targetItemId = chosen.itemId,
            rationale = "state=${chosen.learningState} due=${chosen.nextDueAt}",
        )
    }

    override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? {
        val item = request.item
        if (item.prompt.isBlank() || item.expectedAnswer.isBlank()) return null

        val transforms = listOfNotNull(
            clozeVariant(item, request.seed),
            reversalVariant(item),
        )
        if (transforms.isEmpty()) return null

        val chosen = transforms[request.seed.mod(transforms.size.toLong()).toInt()]
        val distractors = distractorsFor(item, request.siblings, request.seed)

        return chosen.copy(
            explanation = chosen.explanation ?: item.explanation,
            distractors = distractors,
        )
    }

    override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? {
        val item = request.item
        val stored = item.explanation?.takeIf { it.isNotBlank() }
        val text = stored ?: templateExplanation(item, request.grade)
        return GeneratedExplanation(targetItemId = item.id, text = text)
    }

    /**
     * A cloze deletion over the expected answer: one word is blanked and asked
     * for. Only possible when the answer has more than one word, so it is
     * offered as a transform rather than always the result.
     */
    private fun clozeVariant(item: PracticeItem, seed: Long): GeneratedExercise? {
        val words = item.expectedAnswer.trim().split(WHITESPACE)
        if (words.size < 2) return null

        val hiddenIndex = seed.mod(words.size.toLong()).toInt()
        val hidden = words[hiddenIndex]
        val masked = words.toMutableList().also { it[hiddenIndex] = BLANK }.joinToString(" ")

        return GeneratedExercise(
            targetItemId = item.id,
            prompt = "Fill in the blank: $masked",
            expectedAnswer = hidden,
            explanation = item.explanation,
        )
    }

    /** Asks the same question from the other direction: answer first, prompt second. */
    private fun reversalVariant(item: PracticeItem): GeneratedExercise = GeneratedExercise(
        targetItemId = item.id,
        prompt = "What is described by: \"${item.expectedAnswer}\"?",
        expectedAnswer = item.prompt,
        explanation = item.explanation,
    )

    /**
     * Up to three wrong answers drawn from sibling items in the same deck,
     * selected deterministically so the same request always yields the same set.
     */
    private fun distractorsFor(
        item: PracticeItem,
        siblings: List<PracticeItem>,
        seed: Long,
    ): List<String> {
        val pool = siblings.asSequence()
            .filter { it.id != item.id && it.deckId == item.deckId }
            .map { it.expectedAnswer.trim() }
            .filter { it.isNotEmpty() && it != item.expectedAnswer.trim() }
            .distinct()
            .sorted()
            .toList()

        if (pool.isEmpty()) return emptyList()

        val start = seed.mod(pool.size.toLong()).toInt()
        return (0 until minOf(MAX_DISTRACTORS, pool.size)).map { pool[(start + it) % pool.size] }
    }

    private fun templateExplanation(item: PracticeItem, grade: Grade): String {
        val lead = when (grade) {
            Grade.CORRECT -> "Correct."
            Grade.INCORRECT -> "Not quite."
        }
        return "$lead The expected answer is \"${item.expectedAnswer}\"."
    }

    private fun recentAccuracyByItem(request: CoachSelectionRequest): Map<String, Double> =
        request.recentEvents
            .groupBy { it.itemId }
            .mapValues { (_, events) -> events.count { it.correct }.toDouble() / events.size }

    private companion object {
        const val BLANK = "____"
        const val MAX_DISTRACTORS = 3

        /**
         * An item with no recent history is treated as neither strong nor weak,
         * so it does not jump ahead of items the learner is demonstrably
         * struggling with.
         */
        const val NO_HISTORY_ACCURACY = 1.0

        val WHITESPACE = Regex("\\s+")
    }
}
