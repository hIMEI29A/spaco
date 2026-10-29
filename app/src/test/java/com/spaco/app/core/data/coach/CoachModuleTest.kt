package com.spaco.app.core.data.coach

import com.google.common.truth.Truth.assertThat
import com.spaco.app.BuildConfig
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.ItemSchedule
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The wiring guarantee from the spec: an app built without remote settings must
 * still answer every coach request, using the offline coach.
 */
class CoachModuleTest {

    @Test
    fun `an unconfigured build serves every coach request`() = runBlocking {
        assumeTrue(
            "a locally configured remote coach makes this check meaningless",
            BuildConfig.LLM_BASE_URL.isBlank() &&
                BuildConfig.LLM_MODEL.isBlank() &&
                BuildConfig.LLM_API_KEY.isBlank(),
        )

        val agent = CoachModule.provideCoachAgent()
        val item = item()

        assertThat(agent.generateExercise(ExerciseRequest(item = item, seed = 1L))).isNotNull()
        assertThat(agent.generateExplanation(ExplanationRequest(item = item, grade = Grade.CORRECT)))
            .isNotNull()
        assertThat(agent.selectNextItem(CoachSelectionRequest(deckId = "deck-1", candidates = emptyList(), now = Instant.parse("2026-01-01T00:00:00Z"))))
            .isNull()
    }

    private fun item() = PracticeItem(
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
}
