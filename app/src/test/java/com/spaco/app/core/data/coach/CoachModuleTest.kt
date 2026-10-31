package com.spaco.app.core.data.coach

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.model.CoachConfiguration
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.ItemSchedule
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.repository.CoachConfigurationStore
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * The wiring guarantee from the spec: an install with no saved configuration
 * answers every coach request from the built-in coach, and a configuration that
 * cannot be served — here, an endpoint nothing listens on — still leaves the
 * session with usable content.
 *
 * This replaced an earlier check that skipped itself on a machine whose build was
 * configured: the build can no longer be configured, so the test always applies.
 */
class CoachModuleTest {

    private val store = FakeConfigurationStore()

    @Test
    fun `an install with nothing saved serves every coach request`() = runBlocking {
        val agent = CoachModule.provideCoachAgent(store)
        val item = item()

        assertThat(agent.generateExercise(ExerciseRequest(item = item, seed = 1L))).isNotNull()
        assertThat(agent.generateExplanation(ExplanationRequest(item = item, grade = Grade.CORRECT)))
            .isNotNull()
        assertThat(
            agent.selectNextItem(
                CoachSelectionRequest(
                    deckId = "deck-1",
                    candidates = emptyList(),
                    now = Instant.parse("2026-01-01T00:00:00Z"),
                ),
            ),
        ).isNull()
    }

    @Test
    fun `an unreachable configured endpoint never interrupts a session`() = runBlocking {
        store.save(CoachConfiguration(endpoint = "http://127.0.0.1:1", model = "test-model"))
        val agent = CoachModule.provideCoachAgent(store)
        val item = item()

        assertThat(agent.generateExercise(ExerciseRequest(item = item, seed = 1L))).isNotNull()
        assertThat(agent.generateExplanation(ExplanationRequest(item = item, grade = Grade.CORRECT)))
            .isNotNull()
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

    private class FakeConfigurationStore : CoachConfigurationStore {
        private val state = MutableStateFlow<CoachConfiguration?>(null)
        override val configuration: Flow<CoachConfiguration?> = state

        override suspend fun save(configuration: CoachConfiguration) {
            state.value = configuration
        }

        override suspend fun clear() {
            state.value = null
        }
    }
}
