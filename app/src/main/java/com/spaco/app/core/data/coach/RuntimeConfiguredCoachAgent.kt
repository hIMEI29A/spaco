package com.spaco.app.core.data.coach

import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachSelection
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.GeneratedExercise
import com.spaco.app.core.domain.coach.GeneratedExplanation
import com.spaco.app.core.domain.model.CoachConfiguration
import com.spaco.app.core.domain.repository.CoachConfigurationStore
import kotlinx.coroutines.flow.first

/**
 * Chooses the coach to answer with *per request*, from the configuration the
 * learner has saved on the device.
 *
 * The choice used to be made once, when the object graph was built, because the
 * configuration came from the build. It now has to follow the settings: saving a
 * configuration starts using the remote coach immediately, and clearing it
 * returns to the offline one — in the same run of the app, with no restart.
 *
 * The remote coach is still wrapped in [FailoverCoachAgent] so that no failure
 * of a configured endpoint can interrupt a session.
 */
class RuntimeConfiguredCoachAgent(
    private val configurationStore: CoachConfigurationStore,
    private val offline: CoachAgent,
    /** Builds the remote agent for a configuration; injected so tests can watch it. */
    private val remoteFactory: (CoachConfiguration) -> CoachAgent,
) : CoachAgent {

    override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? =
        currentAgent().selectNextItem(request)

    override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? =
        currentAgent().generateExercise(request)

    override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? =
        currentAgent().generateExplanation(request)

    private suspend fun currentAgent(): CoachAgent {
        val configuration = configurationStore.configuration.first() ?: return offline
        return FailoverCoachAgent(primary = remoteFactory(configuration), fallback = offline)
    }
}
