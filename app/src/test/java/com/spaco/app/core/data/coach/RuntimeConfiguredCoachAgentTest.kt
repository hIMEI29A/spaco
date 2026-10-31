package com.spaco.app.core.data.coach

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachSelection
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.GeneratedExercise
import com.spaco.app.core.domain.coach.GeneratedExplanation
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
 * The wiring guarantee behind the spec's opt-in rule: with nothing saved the
 * remote path is not merely unused, it is never built; with a configuration
 * saved the remote answer is preferred; and a clear returns to the offline coach
 * without a restart.
 */
class RuntimeConfiguredCoachAgentTest {

    private val store = FakeConfigurationStore()
    private val remoteCalls = mutableListOf<CoachConfiguration>()
    private var remoteAnswer: CoachAgent = RemoteCoach

    @Test
    fun `nothing saved means the remote coach is never built`() = runBlocking {
        val agent = agent()

        val exercise = agent.generateExercise(ExerciseRequest(item = item(), seed = 1L))

        assertThat(remoteCalls).isEmpty()
        assertThat(exercise?.prompt).isEqualTo(OFFLINE_PROMPT)
    }

    @Test
    fun `a saved configuration is used for the next request`() = runBlocking {
        val agent = agent()
        val configuration = configuration()

        store.save(configuration)
        val exercise = agent.generateExercise(ExerciseRequest(item = item(), seed = 1L))

        assertThat(remoteCalls).containsExactly(configuration)
        assertThat(exercise?.prompt).isEqualTo(REMOTE_PROMPT)
    }

    @Test
    fun `a saved configuration reaches the remote factory unchanged`() = runBlocking {
        val agent = agent()
        val configuration = configuration(credential = null)

        store.save(configuration)
        agent.generateExercise(ExerciseRequest(item = item(), seed = 1L))

        assertThat(remoteCalls).containsExactly(configuration)
        assertThat(remoteCalls.single().credential).isNull()
    }

    @Test
    fun `an unusable remote answer falls back to the offline coach`() = runBlocking {
        remoteAnswer = UnavailableRemoteCoach
        val agent = agent()
        store.save(configuration())

        val exercise = agent.generateExercise(ExerciseRequest(item = item(), seed = 1L))

        assertThat(remoteCalls).hasSize(1)
        assertThat(exercise?.prompt).isEqualTo(OFFLINE_PROMPT)
    }

    @Test
    fun `clearing the configuration returns to the offline coach`() = runBlocking {
        val agent = agent()
        store.save(configuration())
        assertThat(agent.generateExercise(ExerciseRequest(item = item(), seed = 1L))?.prompt)
            .isEqualTo(REMOTE_PROMPT)

        store.clear()
        remoteCalls.clear()
        val afterClear = agent.generateExercise(ExerciseRequest(item = item(), seed = 1L))

        assertThat(remoteCalls).isEmpty()
        assertThat(afterClear?.prompt).isEqualTo(OFFLINE_PROMPT)
    }

    @Test
    fun `selection and explanation follow the same rule`() = runBlocking {
        val agent = agent()
        store.save(configuration())

        val selection = agent.selectNextItem(
            CoachSelectionRequest(
                deckId = "deck-1",
                candidates = emptyList(),
                now = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )
        val explanation = agent.generateExplanation(
            ExplanationRequest(item = item(), grade = Grade.CORRECT),
        )

        assertThat(selection).isNull()
        assertThat(explanation?.text).isEqualTo(REMOTE_EXPLANATION)
        assertThat(remoteCalls).hasSize(2)
    }

    private fun agent() = RuntimeConfiguredCoachAgent(
        configurationStore = store,
        offline = OfflineCoach,
        remoteFactory = { configuration ->
            remoteCalls.add(configuration)
            remoteAnswer
        },
    )

    private fun configuration(credential: String? = "test-bearer-value") = CoachConfiguration(
        endpoint = "https://api.example.test",
        model = "gpt-4o-mini",
        credential = credential,
    )

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

    private companion object {
        const val OFFLINE_PROMPT = "offline prompt"
        const val REMOTE_PROMPT = "remote prompt"
        const val REMOTE_EXPLANATION = "remote explanation"

        val OfflineCoach = object : CoachAgent {
            override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? = null

            override suspend fun generateExercise(request: ExerciseRequest) =
                GeneratedExercise(
                    targetItemId = request.item.id,
                    prompt = OFFLINE_PROMPT,
                    expectedAnswer = request.item.expectedAnswer,
                )

            override suspend fun generateExplanation(request: ExplanationRequest) =
                GeneratedExplanation(targetItemId = request.item.id, text = "offline explanation")
        }

        val UnavailableRemoteCoach = object : CoachAgent {
            override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? = null

            override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? = null

            override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? = null
        }

        val RemoteCoach = object : CoachAgent {
            override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? = null

            override suspend fun generateExercise(request: ExerciseRequest) =
                GeneratedExercise(
                    targetItemId = request.item.id,
                    prompt = REMOTE_PROMPT,
                    expectedAnswer = request.item.expectedAnswer,
                )

            override suspend fun generateExplanation(request: ExplanationRequest) =
                GeneratedExplanation(targetItemId = request.item.id, text = REMOTE_EXPLANATION)
        }
    }
}
